package com.ahmety.uygulama.feature.subtitles

import org.junit.Assert.assertEquals
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
    fun `bilinen siklik araligi eleniyor`() {
        val words = SubtitleText.words(srt)
        val ranks = mapOf("the" to 3, "harvest" to 4200, "abundant" to 8100, "village" to 2600)
        val picked = SubtitleText.selectUnknown(
            words = words,
            frequencyRank = ranks,
            knownUpToRank = 3000,
            alreadySeen = emptySet(),
            limit = 10,
        ).map { it.word }
        assertTrue("the" !in picked)
        assertTrue("village" !in picked)
        assertTrue("abundant" in picked)
        assertTrue("harvest" in picked)
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
