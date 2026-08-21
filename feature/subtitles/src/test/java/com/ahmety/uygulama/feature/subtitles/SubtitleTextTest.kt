package com.ahmety.uygulama.feature.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleTextTest {

    private val srt = """
        1
        00:00:01,000 --> 00:00:03,000
        The <i>abundant</i> harvest fed the village.

        2
        00:00:04,000 --> 00:00:06,000
        - We can't stay here.
        - The harvest is abundant.
    """.trimIndent()

    @Test
    fun `zaman ve sira satirlari atiliyor`() {
        val lines = SubtitleText.lines(srt)
        assertEquals(3, lines.size)
        assertTrue(lines.none { it.contains("-->") })
        assertTrue(lines.none { it.contains("<i>") })
    }

    @Test
    fun `kelimeler sayiliyor ve baglam aliniyor`() {
        val words = SubtitleText.words(srt).associateBy { it.word }
        assertEquals(2, words.getValue("abundant").count)
        assertEquals(2, words.getValue("harvest").count)
        assertTrue(words.getValue("abundant").context.contains("harvest"))
        // Kesme işaretli kısaltmalar alınmıyor: sözlükte karşılıkları yok.
        assertTrue(words.keys.none { it.contains('\'') })
    }

    @Test
    fun `cekimler kokune inip bilinen kelime sayiliyor`() {
        // "lions" listede yok ama "lion" var: bilinen bir kelimeyi
        // "çok nadir" diye listeye sokmamalı.
        val ranks = mapOf("lion" to 1500, "owe" to 900, "straighten" to 3800)
        assertEquals(1500, SubtitleDifficulty.rankOf("lions", ranks))
        assertEquals(900, SubtitleDifficulty.rankOf("owes", ranks))
        assertEquals(3800, SubtitleDifficulty.rankOf("straightened", ranks))
        // Listede hiç karşılığı olmayan kelime en zor sayılıyor.
        assertNull(SubtitleDifficulty.rankOf("amorphous", ranks))
    }

    @Test
    fun `ozel adlar eleniyor`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            I set up Janice in an apartment.

            2
            00:00:04,000 --> 00:00:06,000
            Janice can do what she wants.

            3
            00:00:07,000 --> 00:00:09,000
            The apartment was empty.
        """.trimIndent()

        val names = SubtitleText.properNouns(srt)
        assertTrue("janice" in names)
        // "The" satır başında büyük ama başka yerde küçük geçiyor: özel ad değil.
        assertTrue("the" !in names)
        assertTrue("apartment" !in names)
    }

    @Test
    fun `zorluk esigini gecmeyen kelime alinmiyor`() {
        val words = listOf(
            SubtitleWord("lions", 4, "throw him to the lions"),
            SubtitleWord("amorphous", 1, "an amorphous shape"),
        )
        val ranks = mapOf("lion" to 1500)
        val picked = SubtitleText.selectWords(
            words = words,
            ranks = ranks,
            properNouns = emptySet(),
            minDifficulty = 60,
            alreadySeen = emptySet(),
            limit = 10,
        ).map { it.text }
        // "lions" -> "lion" (1500. sıra) eşiğin altında; dört kez geçmesi
        // onu zor yapmıyor.
        assertTrue("lions" !in picked)
        assertTrue("amorphous" in picked)
    }

    @Test
    fun `cumleler birlestiriliyor`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            I had to straighten out

            2
            00:00:04,000 --> 00:00:06,000
            her boss at the diner.

            3
            00:00:07,000 --> 00:00:09,000
            Let's go.
        """.trimIndent()

        val sentences = SubtitleText.sentences(srt)
        assertEquals("I had to straighten out her boss at the diner.", sentences.first())
        assertEquals(2, sentences.size)
    }

    @Test
    fun `zor cumle kolay cumleden yuksek puan aliyor`() {
        val ranks = mapOf(
            "come" to 40, "here" to 60, "now" to 30,
            "although" to 1200, "insisted" to 4500, "arrangement" to 5200,
        )
        val easy = SubtitleDifficulty.ofSentence("Come here now.", ranks, 10_000)
        val hard = SubtitleDifficulty.ofSentence(
            "Although he insisted, the arrangement they had come up with " +
                "was, in the long run, beside the point.",
            ranks,
            10_000,
        )
        assertTrue("$easy < $hard", easy < hard)
    }

    @Test
    fun `bicim etiketleri ve konum etiketleri temizleniyor`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            {\an8}<font color="#ffffff">Meet me at the <b>docks</b>.</font>
        """.trimIndent()

        val lines = SubtitleText.lines(srt)
        assertEquals(listOf("Meet me at the docks."), lines)
    }

    @Test
    fun `nokta ve rakamlar kelime sanilmiyor`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            Room 237 -- well-lit, quiet, O'Brien said.
        """.trimIndent()

        val words = SubtitleText.words(srt).map { it.word }
        assertTrue("well-lit" in words)
        assertTrue("room" in words)
        assertTrue("said" in words)
        // Rakamlar, kesme işaretliler ve üç harften kısalar dışarıda.
        assertTrue(words.none { it.any(Char::isDigit) })
        assertTrue(words.none { it.contains('\'') })
    }

    @Test
    fun `kapanmayan isaret repligi yutmuyor`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            It's 5 < 10, believe me.

            2
            00:00:04,000 --> 00:00:06,000
            The pointer --> north, remember.
        """.trimIndent()

        val lines = SubtitleText.lines(srt)
        // Kapanışı olmayan "<" etiket değil; satırın kalanı duruyor.
        assertTrue(lines.any { it.contains("believe me") })
        // İçinde ok geçen replik zaman satırı değil.
        assertTrue(lines.any { it.contains("north") })
    }

    @Test
    fun `arapca yazim sadelestiriliyor`() {
        // Harekeli ve harekesiz aynı kelime; elifin üç biçimi tek elif.
        assertEquals("كتاب", ArabicText.normalize("كِتَاب"))
        assertEquals("امر", ArabicText.normalize("أمر"))
        assertEquals("علي", ArabicText.normalize("على"))
        assertEquals("مدرسه", ArabicText.normalize("مدرسة"))
    }

    @Test
    fun `arapca ekler soyuluyor`() {
        // "ve kalemle" -> و + ب + ال + قلم
        assertTrue("قلم" in ArabicText.stems("وبالقلم"))
        assertTrue("كتاب" in ArabicText.stems("الكتاب"))
        assertTrue("معلم" in ArabicText.stems("المعلمون"))
    }

    @Test
    fun `arapca kelimeler ayiklaniyor`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,000
            هذا الكتاب جميل جدا.
        """.trimIndent()

        val words = SubtitleText.words(srt).map { it.word }
        assertTrue("الكتاب" in words)
        assertTrue("جميل" in words)
        // Latin harf yok, tek karakterli parçalar kelime sayılmıyor.
        assertTrue(words.none { it.any { char -> char in 'a'..'z' } })
    }

    @Test
    fun `arapcada listede olmayan yazim eleniyor`() {
        // Arapçada büyük harf yok, özel adı oradan ayırt edemiyoruz; listede
        // hiç geçmeyen yazımı eliyoruz.
        val words = listOf(
            SubtitleWord("مدرسه", 3, "هذه مدرسه"),
            SubtitleWord("جوزيبينا", 2, "اسمها جوزيبينا"),
        )
        val ranks = mapOf("مدرسه" to 9000)
        val picked = SubtitleText.selectWords(
            words = words,
            ranks = ranks,
            properNouns = emptySet(),
            minDifficulty = 0,
            alreadySeen = emptySet(),
            limit = 10,
        ).map { it.text }
        assertTrue("مدرسه" in picked)
        assertTrue("جوزيبينا" !in picked)
    }

    @Test
    fun `ayni surum grubu eslesiyor`() {
        val english = "The.Matrix.1999.1080p.BluRay.x264-YIFY"
        val turkishYify = "The.Matrix.1999.1080p.BluRay.x264.YTS"
        val turkishOther = "The.Matrix.1999.720p.HDTV.RARBG"
        assertTrue(
            ReleaseMatch.score(english, turkishYify) > ReleaseMatch.score(english, turkishOther),
        )
        assertEquals("yify", ReleaseMatch.groupOf(turkishYify))
    }
}
