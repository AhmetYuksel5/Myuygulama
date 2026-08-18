package com.ahmety.uygulama.feature.ebook

import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.File
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
        val html = runCatching {
            zip.getInputStream(entry).bufferedReader().use { it.readText() }
        }.getOrNull() ?: return null

        val doc = Jsoup.parse(html)
        doc.select("script, style, nav, svg, img, figure").remove()

        val title = doc.selectFirst("h1, h2, h3, title")?.text()?.trim().orEmpty()
        val paragraphs = doc.select("p, h1, h2, h3, h4, li, blockquote")
            .map { it.text().trim() }
            .filter { it.length > 1 }
            .distinct()

        return if (paragraphs.isEmpty()) null else EpubChapter(title, paragraphs)
    }

    /** OPF'deki göreli yolu ZIP içindeki gerçek yola çevirir. */
    private fun resolve(basePath: String, href: String): String {
        val clean = href.substringBefore('#')
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
        val text = getInputStream(entry).bufferedReader().use { it.readText() }
        Jsoup.parse(text, "", Parser.xmlParser())
    }.getOrNull()
}
