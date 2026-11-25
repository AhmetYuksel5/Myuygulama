package com.yesiltemizlik.ofis

import org.junit.Assert.assertEquals
import org.junit.Test

class IslerTest {
    private val isler = listOf(
        Is("1", "Zeta Ofis", "Kadıköy", "anadolu"),
        Is("2", "Alfa Plaza", "Kadıköy", "anadolu"),
        Is("3", "Beta", "Şişli", "avrupa"),
        Is("4", "Gama", "Üsküdar", "anadolu"),
        Is("5", "Delta", "", ""),
    )

    @Test
    fun ilcedekiIslerAdSirasinda() {
        assertEquals(listOf("Alfa Plaza", "Zeta Ofis"), ilceIsleri(isler, "Kadıköy").map { it.ad })
    }

    @Test
    fun digerIslerdeAyniYakaOnce() {
        assertEquals(listOf("Gama", "Delta", "Beta"), digerIsler(isler, "Kadıköy").map { it.ad })
    }

    @Test
    fun aramaTurkceHarfleriKatlar() {
        assertEquals(listOf("Beta"), araSuz(isler, "ŞİŞLİ").map { it.ad })
        assertEquals(listOf("Gama"), araSuz(isler, "uskudar").map { it.ad })
    }

    @Test
    fun yakaVeSayilar() {
        assertEquals("anadolu", Ilceler.yaka("Üsküdar"))
        assertEquals("avrupa", Ilceler.yaka("Şişli"))
        assertEquals(2, ilceSayilari(isler)["Kadıköy"])
    }

    @Test
    fun numaraAnahtariAyniNumarayiBirlestirir() {
        assertEquals("5321234567", numaraAnahtari("+90 532 123 45 67"))
        assertEquals("5321234567", numaraAnahtari("05321234567"))
        assertEquals("5321234567", numaraAnahtari("5321234567"))
        assertEquals("", numaraAnahtari("1234"))
        assertEquals("", numaraAnahtari(""))
    }

    @Test
    fun telefonVeSure() {
        assertEquals("0532 123 45 67", telBicim("+905321234567"))
        assertEquals("0532 123 45 67", telBicim("5321234567"))
        assertEquals("2 dk 5 sn", sureMetni(125))
        assertEquals("45 sn", sureMetni(45))
        assertEquals("", sureMetni(0))
    }
}
