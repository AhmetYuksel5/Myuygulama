package com.ahmety.uygulama.feature.ebook

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipFile

/** Kitabın tek bir bölümü: başlık + paragraflar. */
data class EpubChapter(
    val title: String,
    val paragraphs: List<String>,
)

data class EpubBook(
    val title: String,
    val author: String,
    val chapters: List<EpubChapter>,
)

/**
 * EPUB ayrıştırıcı.
 *
 * EPUB, içinde XHTML dosyaları bulunan bir ZIP arşivi. Ayrı bir kitap
 * kitaplığı eklemek yerine standardın kendi yolunu izliyoruz:
 * `META-INF/container.xml` → OPF dosyası → `manifest` (dosya listesi) +
 * `spine` (okuma sırası). Metni Jsoup ile çıkarıyoruz.
 *
 * Ayrıştırma tamamen dosya üzerinde; ağ erişimi yok.
 */
object EpubParser {

    fun parse(file: File): EpubBook? = runCatching {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip) ?: return@use null
            val opfDoc = zip.readXml(opfPath) ?: return@use null
            val basePath = opfPath.substringBeforeLast('/', "")

            val title = opfDoc.selectFirst("metadata > dc|title, metadata > title")
                ?.text()
                ?.trim()
                .orEmpty()
                .ifBlank { file.nameWithoutExtension }
            val author = opfDoc.selectFirst("metadata > dc|creator, metadata > creator")
                ?.text()
                ?.trim()
                .orEmpty()

            // manifest: id -> href
            val hrefById = opfDoc.select("manifest > item").associate { item ->
                item.attr("id") to item.attr("href")
            }
            // spine: okuma sırası
            val spine = opfDoc.select("spine > itemref").mapNotNull { ref ->
                hrefById[ref.attr("idref")]
            }
            val hrefs = spine.ifEmpty {
                // spine yoksa manifest'teki xhtml dosyalarına düş.
                opfDoc.select("manifest > item")
                    .filter { it.attr("media-type").contains("xhtml", ignoreCase = true) }
                    .map { it.attr("href") }
            }

            val chapters = hrefs.mapNotNull { href ->
                val entryPath = resolve(basePath, href)
                readChapter(zip, entryPath)
            }.filter { it.paragraphs.isNotEmpty() }

            if (chapters.isEmpty()) null else EpubBook(title, author, chapters)
        }
    }.getOrNull()

    /**
     * Kitabın kapak görseli.
     *
     * Standart tek bir yol söylemiyor, bu yüzden sırayla deneniyor:
     * 1. EPUB 2'nin `<meta name="cover" content="ID">` işareti,
     * 2. EPUB 3'ün `properties="cover-image"` niteliği,
     * 3. adında "cover" geçen bir resim,
     * 4. arşivdeki ilk resim — bir şey göstermek hiç göstermemekten iyi.
     *
     * Ayrı bir fonksiyon: kapak yalnızca kitap yüklenirken bir kez
     * gerekiyor, oysa [parse] kitabı her açışta çalışıyor. Baytları
     * orada taşımak boşuna bellek olurdu.
     */
    fun coverBytes(file: File): ByteArray? = runCatching {
        ZipFile(file).use { zip ->
            val opfPath = findOpfPath(zip) ?: return@use null
            val opfDoc = zip.readXml(opfPath) ?: return@use null
            val basePath = opfPath.substringBeforeLast('/', "")

            val items = opfDoc.select("manifest > item")
            val isImage = { href: String, type: String ->
                type.startsWith("image/", ignoreCase = true) ||
                    IMAGE_SUFFIXES.any { href.endsWith(it, ignoreCase = true) }
            }

            val metaId = opfDoc.selectFirst("metadata > meta[name=cover]")?.attr("content")
            val candidates = buildList {
                if (!metaId.isNullOrBlank()) {
                    items.firstOrNull { it.attr("id") == metaId }?.let { add(it.attr("href")) }
                }
                items.firstOrNull { it.attr("properties").contains("cover-image") }
                    ?.let { add(it.attr("href")) }
                items.filter { isImage(it.attr("href"), it.attr("media-type")) }
                    .firstOrNull {
                        it.attr("id").contains("cover", ignoreCase = true) ||
                            it.attr("href").contains("cover", ignoreCase = true)
                    }
                    ?.let { add(it.attr("href")) }
                items.firstOrNull { isImage(it.attr("href"), it.attr("media-type")) }
                    ?.let { add(it.attr("href")) }
            }

            candidates.firstNotNullOfOrNull { href ->
                val entry = zip.getEntry(resolve(basePath, href)) ?: return@firstNotNullOfOrNull null
                if (entry.size > MAX_COVER_BYTES) return@firstNotNullOfOrNull null
                runCatching { zip.getInputStream(entry).use { it.readBytes() } }.getOrNull()
            }
        }
    }.getOrNull()

    private val IMAGE_SUFFIXES = listOf(".jpg", ".jpeg", ".png", ".webp", ".gif")

    /** Bundan büyük bir kapak zaten kapak değildir. */
    private const val MAX_COVER_BYTES = 8L * 1024 * 1024

    private fun findOpfPath(zip: ZipFile): String? {
        val container = zip.readXml("META-INF/container.xml")
        val fromContainer = container?.selectFirst("rootfile")?.attr("full-path")
        if (!fromContainer.isNullOrBlank()) return fromContainer
        // Bazı kitaplarda container.xml bozuk; .opf'yi arayarak bulalım.
        return zip.entries().asSequence()
            .map { it.name }
            .firstOrNull { it.endsWith(".opf", ignoreCase = true) }
    }

    private fun readChapter(zip: ZipFile, path: String): EpubChapter? {
        val entry = zip.getEntry(path) ?: return null
        val bytes = runCatching {
            zip.getInputStream(entry).use { it.readBytes() }
        }.getOrNull() ?: return null

        // Kodlama: dosya geçerli UTF-8 ise UTF-8 olarak okuyoruz. Bazı EPUB'lar
        // gövdesi UTF-8 olduğu hâlde meta etiketinde latin-1 ilan ediyor;
        // Jsoup o etikete uyunca tırnaklar "â€™" gibi çıkıyordu.
        val charset = if (isValidUtf8(bytes)) "UTF-8" else null
        val doc = runCatching {
            Jsoup.parse(bytes.inputStream(), charset, "")
        }.getOrNull() ?: return null
        doc.select("script, style, nav, svg, img, figure").remove()

        val title = repairMojibake(doc.selectFirst("h1, h2, h3, title")?.text()?.trim().orEmpty())
        var paragraphs = doc.select("p, h1, h2, h3, h4, li, blockquote")
            .map { repairMojibake(it.text().trim()) }
            .filter { it.length > 1 }
        // Bazı kitaplar paragrafları <div> ile kuruyor; hiç <p> yoksa yedek.
        if (paragraphs.isEmpty()) {
            paragraphs = doc.select("div")
                .map { repairMojibake(it.ownText().trim()) }
                .filter { it.length > 1 }
        }

        return if (paragraphs.isEmpty()) null else EpubChapter(title, paragraphs)
    }

    /** OPF'deki göreli yolu ZIP içindeki gerçek yola çevirir. */
    private fun resolve(basePath: String, href: String): String {
        // "My%20Chapter.xhtml" gibi yollar ZIP içinde çözülmüş adla duruyor.
        val clean = runCatching {
            URLDecoder.decode(href.substringBefore('#'), "UTF-8")
        }.getOrDefault(href.substringBefore('#'))
        if (basePath.isBlank()) return clean
        return "$basePath/$clean"
            // "a/b/../c" gibi yolları düzleştir.
            .split('/')
            .fold(mutableListOf<String>()) { acc, part ->
                when (part) {
                    "", "." -> Unit
                    ".." -> if (acc.isNotEmpty()) acc.removeAt(acc.lastIndex) else Unit
                    else -> acc.add(part)
                }
                acc
            }
            .joinToString("/")
    }

    /** Baytlar geçerli UTF-8 mi (kesin kontrol). */
    private fun isValidUtf8(bytes: ByteArray): Boolean = runCatching {
        val decoder = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        decoder.decode(ByteBuffer.wrap(bytes))
        true
    }.getOrDefault(false)

    /**
     * Kitabın kendisi bozuk üretilmişse (UTF-8 metin latin-1 olarak kaydedilmiş)
     * okuma anında düzeltilemez; metinde "â€™" gibi imzalar varsa baytları geri
     * çevirip yeniden çözüyoruz.
     */
    internal fun repairMojibake(text: String): String {
        if (!text.contains('\u00e2') && !text.contains('\u00c3')) return text
        val repaired = runCatching {
            String(text.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
        }.getOrNull() ?: return text
        // Yalnızca gerçekten düzeldiyse kullan.
        return if (repaired.count { it == '\ufffd' } <= text.count { it == '\ufffd' } &&
            !repaired.contains("\u00e2\u0080")
        ) {
            repaired
        } else {
            text
        }
    }

    private fun ZipFile.readXml(path: String) = runCatching {
        val entry = getEntry(path) ?: return@runCatching null
        getInputStream(entry).use { stream -> Jsoup.parse(stream, null, "", Parser.xmlParser()) }
    }.getOrNull()
}
