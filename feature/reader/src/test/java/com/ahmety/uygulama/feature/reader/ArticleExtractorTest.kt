package com.ahmety.uygulama.feature.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArticleExtractorTest {

    private val longParagraph =
        "Bu paragraf ayıklayıcının içerik olarak kabul etmesi gereken uzunlukta " +
            "bir metin içeriyor; gerçek makalelerdeki paragraflar da aşağı yukarı " +
            "bu uzunlukta olur ve anlamlı cümlelerden oluşur."

    private fun page(bodyHtml: String, head: String = ""): String =
        "<html><head><title>Sayfa Başlığı</title>$head</head><body>$bodyHtml</body></html>"

    @Test
    fun `duz makale cikarilir ve baslik og-title dan gelir`() {
        val html = page(
            head = """<meta property="og:title" content="Gerçek Başlık">""",
            bodyHtml = "<article>" + (1..5).joinToString("") { "<p>$longParagraph</p>" } + "</article>",
        )

        val result = ArticleExtractor.extract("https://ornek.com/yazi", html)

        assertNotNull(result)
        assertEquals("Gerçek Başlık", result!!.title)
        assertTrue(result.length > 500)
    }

    @Test
    fun `turkce gurultu bloklari icerige karismaz`() {
        val html = page(
            "<article>" +
                "<p>$longParagraph</p>" +
                "<div>İLGİLİ HABER Çok önemli başka haber</div>" +
                "<p>$longParagraph</p>" +
                "<div class=\"related-news\"><p>$longParagraph</p></div>" +
                "<p>$longParagraph</p>" +
                "</article>",
        )

        val result = ArticleExtractor.extract("https://haber.com/x", html)

        assertNotNull(result)
        assertFalse(result!!.body.contains("İLGİLİ HABER"))
        assertEquals(3, result.paragraphs.size)
    }

    @Test
    fun `bos article etiketinde yedek zincir main icerigini bulur`() {
        // "article var ama gövde JS ile geliyor" vakası: article boş,
        // gerçek içerik main altında.
        val html = page(
            "<article></article>" +
                "<main>" + (1..5).joinToString("") { "<p>$longParagraph</p>" } + "</main>",
        )

        val result = ArticleExtractor.extract("https://blog.ornek.com/x", html)

        assertNotNull(result)
        assertTrue(result!!.length > 500)
    }

    @Test
    fun `kod bloklari satir sonlarini korur`() {
        val code = "fun main() {\n    println(\"merhaba\")\n}"
        val html = page(
            "<main>" +
                (1..4).joinToString("") { "<p>$longParagraph</p>" } +
                "<pre>$code</pre>" +
                "</main>",
        )

        val result = ArticleExtractor.extract("https://docs.ornek.com/x", html)

        assertNotNull(result)
        assertTrue(result!!.paragraphs.any { it.contains("fun main() {\n") })
    }

    @Test
    fun `cikarilabilir icerik yoksa null doner`() {
        val html = page("<nav><ul><li>Menü 1</li><li>Menü 2</li></ul></nav><p>Kısa.</p>")
        assertNull(ArticleExtractor.extract("https://bos.com", html))
    }

    @Test
    fun `ayni metin iki kez alinmaz`() {
        // li > p gibi iç içe eşleşmeler aynı paragrafı iki kez üretebilir.
        val html = page(
            "<main><ul><li><p>$longParagraph</p></li></ul>" +
                (1..4).joinToString("") { index -> "<p>$longParagraph $index</p>" } + "</main>",
        )

        val result = ArticleExtractor.extract("https://ornek.com", html)

        assertNotNull(result)
        val occurrences = result!!.paragraphs.count { it == longParagraph }
        assertEquals(1, occurrences)
    }
}
