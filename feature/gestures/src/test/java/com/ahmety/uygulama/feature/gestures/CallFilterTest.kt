package com.ahmety.uygulama.feature.gestures

import org.junit.Assert.assertEquals
import org.junit.Test

class CallFilterTest {

    @Test
    fun `numara her bicimde ulusal bicime cevriliyor`() {
        assertEquals("08501234567", CallFilterStore.national("+90 850 123 45 67"))
        assertEquals("08501234567", CallFilterStore.national("908501234567"))
        assertEquals("08501234567", CallFilterStore.national("00908501234567"))
        assertEquals("08501234567", CallFilterStore.national("0850 123 45 67"))
        assertEquals("08501234567", CallFilterStore.national("8501234567"))
        assertEquals("05321234567", CallFilterStore.national("+905321234567"))
        assertEquals("", CallFilterStore.national(""))
    }

    @Test
    fun `yazilan baslangic ulusal bicime cevriliyor`() {
        assertEquals("0850", CallFilterStore.normalizePrefix("0850"))
        assertEquals("0850", CallFilterStore.normalizePrefix("850"))
        assertEquals("0850", CallFilterStore.normalizePrefix("+90850"))
        assertEquals("0850", CallFilterStore.normalizePrefix("+90 850"))
        assertEquals("0212", CallFilterStore.normalizePrefix("0212"))
    }

    @Test
    fun `kalip isaretleri`() {
        assertEquals("02129*", CallFilterStore.normalizePrefix("0212 9*"))
        assertEquals("0212???00", CallFilterStore.normalizePrefix("0212 xxx 00"))
        val star = CallFilterStore.pattern("02129*")
        assertEquals(true, star.matches("02129001122"))
        assertEquals(false, star.matches("02128001122"))
        val single = CallFilterStore.pattern("0212???00")
        assertEquals(true, single.matches("02125550012"))
        assertEquals(false, single.matches("02125551212"))
        // Düz başlangıç eskisi gibi.
        assertEquals(true, CallFilterStore.pattern("0850").matches("08501234567"))
        assertEquals(false, CallFilterStore.pattern("0850").matches("05321234567"))
    }
}
