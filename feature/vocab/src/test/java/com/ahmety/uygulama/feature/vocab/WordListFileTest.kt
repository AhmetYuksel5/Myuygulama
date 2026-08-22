package com.ahmety.uygulama.feature.vocab

import com.ahmety.uygulama.core.model.HighlightColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WordListFileTest {

    @Test
    fun `ilk satir listenin adi`() {
        val parsed = WordListFile.parse("COSY\nchoker\nplump flesh")
        assertEquals("COSY", parsed?.name)
        assertEquals(listOf("choker", "plump flesh"), parsed?.entries)
    }

    @Test
    fun `tirnakli ve tirnaksiz ayni sonucu veriyor`() {
        val quoted = WordListFile.parse("\"COSY\"\n\"choker\"\n\"plump flesh\"")
        val plain = WordListFile.parse("COSY\nchoker\nplump flesh")
        assertEquals(plain, quoted)
    }

    @Test
    fun `cumle icindeki virgul maddeyi bolmuyor`() {
        // Virgülden bölseydik bu satır üç maddeye ayrılırdı.
        val parsed = WordListFile.parse("Liste\n\"The truth is, probably, an inconvenience.\"")
        assertEquals(listOf("The truth is, probably, an inconvenience."), parsed?.entries)
    }

    @Test
    fun `metnin icindeki tirnak korunuyor`() {
        // CSV'de tırnak içindeki tırnak iki kez yazılıyor.
        val parsed = WordListFile.parse("Liste\n\"She said \"\"no\"\" twice.\"")
        assertEquals(listOf("She said \"no\" twice."), parsed?.entries)
    }

    @Test
    fun `bos satirlar ve tekrarlar atiliyor`() {
        val parsed = WordListFile.parse("Liste\n\nchoker\n\nChoker\n  \nedge\n")
        assertEquals(listOf("choker", "edge"), parsed?.entries)
    }

    @Test
    fun `tek satirlik dosya liste sayilmiyor`() {
        assertNull(WordListFile.parse("COSY"))
        assertNull(WordListFile.parse(""))
    }

    @Test
    fun `tek kelime mavi digerleri kirmizi`() {
        assertEquals(HighlightColor.BLUE, WordListFile.colorOf("choker"))
        assertEquals(HighlightColor.RED, WordListFile.colorOf("plump flesh"))
        assertEquals(HighlightColor.RED, WordListFile.colorOf("I will hold you to that."))
    }
}
