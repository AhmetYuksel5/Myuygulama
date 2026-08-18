package com.ahmety.uygulama.feature.ebook

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
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
        // Kodlamayı Jsoup'un kendisi bulsun: EPUB2 kitapları her zaman UTF-8
        // değil, zorlarsak Türkçe/aksanlı harfler bozuk çıkıyor.
        val doc = runCatching {
            zip.getInputStream(entry).use { stream -> Jsoup.parse(stream, null, "") }
        }.getOrNull() ?: return null
        doc.select("script, style, nav, svg, img, figure").remove()

        val title = doc.selectFirst("h1, h2, h3, title")?.text()?.trim().orEmpty()
        var paragraphs = doc.select("p, h1, h2, h3, h4, li, blockquote")
            .map { it.text().trim() }
            .filter { it.length > 1 }
        // Bazı kitaplar paragrafları <div> ile kuruyor; hiç <p> yoksa yedek.
        if (paragraphs.isEmpty()) {
            paragraphs = doc.select("div")
                .map { it.ownText().trim() }
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

    private fun ZipFile.readXml(path: String) = runCatching {
        val entry = getEntry(path) ?: return@runCatching null
        getInputStream(entry).use { stream -> Jsoup.parse(stream, null, "", Parser.xmlParser()) }
    }.getOrNull()
}
