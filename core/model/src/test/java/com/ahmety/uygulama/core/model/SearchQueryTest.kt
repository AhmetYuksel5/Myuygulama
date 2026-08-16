package com.ahmety.uygulama.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SearchQueryTest {

    @Test
    fun `sozcukler onek eslesmesi icin yildizlanir`() {
        assertEquals("kant* ahlak*", SearchQuery.toFtsQuery("kant ahlak"))
    }

    @Test
    fun `fts sozdizimini bozan karakterler temizlenir`() {
        // Bunlar temizlenmezse FTS sorgusu hata verir ve arama hiç çalışmaz.
        assertEquals("kant*", SearchQuery.toFtsQuery("\"kant\""))
        assertEquals("test*", SearchQuery.toFtsQuery("  test-  "))
        assertEquals("a* b*", SearchQuery.toFtsQuery("a*b"))
        assertEquals("kant* eleştiri*", SearchQuery.toFtsQuery("kant: eleştiri!"))
    }

    @Test
    fun `turkce harfler korunur`() {
        assertEquals("şiir* güneş*", SearchQuery.toFtsQuery("şiir güneş"))
    }

    @Test
    fun `rakamlar korunur`() {
        assertEquals("2026* bütçe*", SearchQuery.toFtsQuery("2026 bütçe"))
    }

    @Test
    fun `anlamli sozcuk kalmazsa null doner`() {
        assertNull(SearchQuery.toFtsQuery(""))
        assertNull(SearchQuery.toFtsQuery("   "))
        assertNull(SearchQuery.toFtsQuery("*** --- ,,,"))
    }
}
