package com.ahmety.uygulama.ui.update

import org.junit.Assert.assertEquals
import org.junit.Test

class ReleaseNotesTest {

    @Test
    fun `elle sarilan satirlar geri birlestiriliyor`() {
        val raw = """
            Kalem daireciği geldi

            Renk şimdiye kadar yalnız otomatik
            belirleniyordu; artık çevrilebiliyor.
        """.trimIndent()

        val blocks = formatReleaseNotes(raw)
        assertEquals(NoteBlock.Heading("Kalem daireciği geldi"), blocks[0])
        // İki satır tek paragraf: aradaki satır sonu sarma, paragraf değil.
        assertEquals(
            NoteBlock.Paragraph(
                "Renk şimdiye kadar yalnız otomatik belirleniyordu; artık çevrilebiliyor.",
            ),
            blocks[1],
        )
    }

    @Test
    fun `maddeler ayri parca oluyor`() {
        val raw = """
            Başlık

            - Birinci madde uzun olduğu için
              ikinci satıra sarmış.
            - İkinci madde.
        """.trimIndent()

        val blocks = formatReleaseNotes(raw)
        assertEquals(NoteBlock.Heading("Başlık"), blocks[0])
        assertEquals(NoteBlock.Bullet("Birinci madde uzun olduğu için ikinci satıra sarmış."), blocks[1])
        assertEquals(NoteBlock.Bullet("İkinci madde."), blocks[2])
    }

    @Test
    fun `kunye satirlari atiliyor`() {
        val raw = """
            Başlık

            Gövde.

            Co-Authored-By: Biri <biri@example.com>
            Claude-Session: https://example.com/x

            [skip ci]
        """.trimIndent()

        val blocks = formatReleaseNotes(raw)
        assertEquals(2, blocks.size)
        assertEquals(NoteBlock.Heading("Başlık"), blocks[0])
        assertEquals(NoteBlock.Paragraph("Gövde."), blocks[1])
    }

    @Test
    fun `bos not bos liste veriyor`() {
        assertEquals(emptyList<NoteBlock>(), formatReleaseNotes("   \n\n  "))
    }
}
