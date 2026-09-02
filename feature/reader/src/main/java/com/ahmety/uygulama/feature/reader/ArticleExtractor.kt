package com.ahmety.uygulama.feature.reader

import net.dankito.readability4j.Readability4J
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

data class ExtractedArticle(
    val title: String,
    val byline: String?,
    /** Boş satırla ayrılmış okunabilir bloklar. */
    val paragraphs: List<String>,
    /** Sayfanın kapak görselinin adresi; yoksa null. */
    val image: String? = null,
) {
    val body: String get() = paragraphs.joinToString("\n\n")
    val length: Int get() = paragraphs.sumOf { it.length }
}

/**
 * HTML'i okunabilir metne indirger — Pocket'ın yaptığı iş.
 *
 * Sıralama gerçek sitelerdeki testlerden çıktı:
 * 1. Gürültü blokları (ilgili haber, reklam, paylaş) ÖNCE temizlenir; Türk
 *    haber siteleri bunları içerik div'inin içine gömüyor, sonra temizlemek
 *    işe yaramıyor.
 * 2. Readability çalıştırılır.
 * 3. Sonuç şüpheli derecede kısaysa yedek seçici zinciri denenir — "article
 *    etiketi var ama gövde JS ile geliyor" vakası gerçek (cloud.google.com
 *    blog'unda doğrulandı); uzunluk kontrolü olmadan başarı sanılıyor.
 */
object ArticleExtractor {

    /** Bu uzunluğun altındaki sonuç "başarı" sayılmıyor. */
    private const val MIN_ACCEPTABLE_LENGTH = 500

    /** Bunun da altındaysa makale çıkarılamamış demektir. */
    private const val MIN_FINAL_LENGTH = 180

    fun extract(url: String, html: String): ExtractedArticle? {
        val doc = Jsoup.parse(html, url)
        val title = bestTitle(doc)
        val byline = doc.selectFirst("meta[name=author]")?.attr("content")
            ?.takeIf { it.isNotBlank() }

        // Kapak görseli temizlikten önce okunuyor: adresi <head> içinde ama
        // yedek olarak gövdedeki ilk büyük resme de bakıyoruz ve preClean
        // gövdenin yarısını atıyor.
        val image = coverImage(doc)

        preClean(doc)

        val fromReadability = runCatching {
            val article = Readability4J(url, doc.outerHtml()).parse()
            article.content?.let { contentHtml ->
                paragraphsOf(Jsoup.parse(contentHtml).body())
            }
        }.getOrNull().orEmpty()

        val paragraphs = if (fromReadability.sumOf { it.length } >= MIN_ACCEPTABLE_LENGTH) {
            fromReadability
        } else {
            val fallback = fallbackParagraphs(doc)
            // İkisi de kısaysa uzun olanı al; belki gerçekten kısa bir duyurudur.
            if (fallback.sumOf { it.length } > fromReadability.sumOf { it.length }) {
                fallback
            } else {
                fromReadability
            }
        }

        if (paragraphs.sumOf { it.length } < MIN_FINAL_LENGTH) return null
        return ExtractedArticle(
            title = title,
            byline = byline,
            paragraphs = paragraphs,
            image = image,
        )
    }

    /**
     * Kapak görseli.
     *
     * Sıra paylaşım etiketlerinden başlıyor: `og:image` zaten "bu sayfayı
     * bir yerde göstereceksen bu resmi kullan" demek — Pocket'ın kartlarda
     * gösterdiği resim de buydu. Hiçbiri yoksa gövdedeki ilk büyükçe resme
     * düşüyoruz; küçükler genelde simge, avatar ya da izleme pikseli.
     */
    private fun coverImage(doc: Document): String? {
        val meta = listOf(
            "meta[property=og:image]",
            "meta[name=og:image]",
            "meta[name=twitter:image]",
            "meta[property=twitter:image]",
            "link[rel=image_src]",
        )
        meta.forEach { selector ->
            val element = doc.selectFirst(selector) ?: return@forEach
            val attribute = if (element.tagName() == "link") "href" else "content"
            val url = element.absUrl(attribute)
            if (url.isNotBlank()) return url
        }
        return doc.select("article img, main img, img")
            .firstOrNull { image ->
                val width = image.attr("width").toIntOrNull() ?: DEFAULT_IMAGE_EDGE
                val height = image.attr("height").toIntOrNull() ?: DEFAULT_IMAGE_EDGE
                width >= MIN_IMAGE_EDGE && height >= MIN_IMAGE_EDGE &&
                    image.absUrl("src").isNotBlank()
            }
            ?.absUrl("src")
    }

    /** Bundan küçük resim kapak değil; simge ya da izleme pikselidir. */
    private const val MIN_IMAGE_EDGE = 200

    /** Ölçüsünü yazmayan resim: şansını denesin. */
    private const val DEFAULT_IMAGE_EDGE = 400

    private fun bestTitle(doc: Document): String =
        doc.selectFirst("meta[property=og:title]")?.attr("content")?.takeIf { it.isNotBlank() }
            ?: doc.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: doc.title().ifBlank { "Başlıksız makale" }

    /** Ayıklamadan önce atılan gürültü. */
    private val NOISE_SELECTORS = listOf(
        "nav", "aside", "footer", "header", "form", "iframe",
        "script", "style", "noscript",
        ".related", ".related-news", ".ilgili-haber", ".news-related",
        ".inread", "[class*=adslot]", "[id^=google_ads]", "[class*=advert]",
        ".tags", ".etiketler", ".paylas", ".share", ".social",
        ".comment", ".comments", ".yorum", ".yorumlar",
        ".newsletter", "[class*=promo]", ".navbox", ".toc", ".breadcrumb",
    ).joinToString(", ")

    /**
     * Türk haber sitelerinin paragraf aralarına gömdüğü kısa gürültü blokları.
     * Sınıf adları site yenilemeleriyle değiştiği için metne de bakıyoruz.
     */
    private val NOISE_TEXT_PREFIXES = listOf(
        "İLGİLİ HABER", "İLGİNİZİ ÇEKEBİLİR", "BUNLARI DA OKUYUN",
        "HABERİN DEVAMI", "REKLAM", "SPONSORLU", "ETİKETLER",
        "İLGİLİ VİDEO", "ÖNERİLEN VİDEO",
    )

    private fun preClean(doc: Document) {
        doc.select(NOISE_SELECTORS).remove()
        doc.allElements
            .filter { element ->
                val text = element.ownText().trim()
                text.length in 1..80 && NOISE_TEXT_PREFIXES.any { text.startsWith(it) }
            }
            .forEach(Element::remove)
    }

    /** Readability yetersiz kaldığında denenen seçiciler, sırayla. */
    private val FALLBACK_SELECTORS = listOf(
        "article",
        "[role=main]",
        "main",
        "#mw-content-text .mw-parser-output", // Wikipedia
        ".available-content .body.markup",    // Substack
        "#content", "#main-content",
        ".post-content", ".article-body", ".entry-content",
        ".story-body", ".news-content",
        "div[itemprop=articleBody]",
    )

    private fun fallbackParagraphs(doc: Document): List<String> {
        for (selector in FALLBACK_SELECTORS) {
            val candidates = runCatching { doc.select(selector) }.getOrNull() ?: continue
            // Aynı seçici birden çok blok bulursa en uzun metinli olanı al.
            val best = candidates.maxByOrNull { it.text().length } ?: continue
            val paragraphs = paragraphsOf(best)
            if (paragraphs.sumOf { it.length } >= MIN_ACCEPTABLE_LENGTH) return paragraphs
        }
        return emptyList()
    }

    /**
     * Bir elemandan okunabilir blokları toplar. Kod blokları satır sonlarını
     * koruyarak alınır (Jsoup'un text()'i onları tek satıra yapıştırır).
     */
    private fun paragraphsOf(root: Element): List<String> {
        val blocks = root.select("p, h2, h3, h4, li, pre, blockquote")
        val seen = LinkedHashSet<String>()

        val result = mutableListOf<String>()
        for (block in blocks) {
            // İç içe eşleşmelerde (li > p) aynı metni iki kez almayalım.
            if (block.parents().any { it.tagName() == "pre" }) continue

            val text = if (block.tagName() == "pre") {
                block.wholeText().trimEnd()
            } else {
                block.text().trim()
            }
            if (text.length < 2) continue
            if (!seen.add(text)) continue
            result += text
        }

        // Hiç blok bulunamadıysa (etiketsiz düz içerik) tüm metni tek parça al.
        if (result.isEmpty()) {
            val whole = root.text().trim()
            if (whole.isNotEmpty()) result += whole
        }
        return result
    }
}
