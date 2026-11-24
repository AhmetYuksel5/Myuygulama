package com.yesiltemizlik.ofis

import java.text.Collator
import java.util.Locale

/** Paneldeki etkin iş (projects): yalnız adı ve yeri gelir, para ya da personel bilgisi gelmez. */
data class Is(val id: String, val ad: String, val ilce: String, val yaka: String)

object Ilceler {
    val ANADOLU = listOf(
        "Adalar", "Ataşehir", "Beykoz", "Çekmeköy", "Kadıköy", "Kartal", "Maltepe", "Pendik",
        "Sancaktepe", "Sultanbeyli", "Şile", "Tuzla", "Ümraniye", "Üsküdar",
    )
    val AVRUPA = listOf(
        "Arnavutköy", "Avcılar", "Bağcılar", "Bahçelievler", "Bakırköy", "Başakşehir", "Bayrampaşa",
        "Beşiktaş", "Beylikdüzü", "Beyoğlu", "Büyükçekmece", "Çatalca", "Esenler", "Esenyurt",
        "Eyüpsultan", "Fatih", "Gaziosmanpaşa", "Güngören", "Kağıthane", "Küçükçekmece", "Sarıyer",
        "Silivri", "Sultangazi", "Şişli", "Zeytinburnu",
    )

    fun yaka(ilce: String): String = when (ilce) {
        in ANADOLU -> "anadolu"
        in AVRUPA -> "avrupa"
        else -> ""
    }

    fun liste(yaka: String): List<String> = if (yaka == "anadolu") ANADOLU else AVRUPA
}

private val turkce: Collator = Collator.getInstance(Locale("tr", "TR"))

fun adSirasi(isler: List<Is>): List<Is> = isler.sortedWith { a, b -> turkce.compare(a.ad, b.ad) }

/** Seçilen ilçedeki işler, ada göre. */
fun ilceIsleri(isler: List<Is>, ilce: String): List<Is> = adSirasi(isler.filter { it.ilce == ilce })

/**
 * Seçilen ilçenin dışındaki işler. Aynı yakadakiler önce (aday oraya da
 * gidebilir), ilçesi bilinmeyenler ortada, karşı yaka en sonda.
 */
fun digerIsler(isler: List<Is>, ilce: String): List<Is> {
    val yaka = Ilceler.yaka(ilce)
    fun derece(i: Is) = when {
        yaka.isNotEmpty() && i.yaka == yaka -> 0
        i.yaka.isEmpty() -> 1
        else -> 2
    }
    return isler.filter { it.ilce != ilce }.sortedWith { a, b ->
        val d = derece(a) - derece(b)
        if (d != 0) d else {
            val c = turkce.compare(a.ilce, b.ilce)
            if (c != 0) c else turkce.compare(a.ad, b.ad)
        }
    }
}

fun ilceSayilari(isler: List<Is>): Map<String, Int> = isler.groupingBy { it.ilce }.eachCount()

/** Aramada İ/ı ayrımı ve büyük-küçük harf takılmasın. */
fun katla(s: String): String = s.replace('İ', 'i').replace('I', 'ı').lowercase(Locale("tr", "TR"))
    .replace('ı', 'i').replace('ş', 's').replace('ğ', 'g').replace('ü', 'u').replace('ö', 'o').replace('ç', 'c')

fun araSuz(isler: List<Is>, aranan: String): List<Is> {
    val q = katla(aranan.trim())
    if (q.isEmpty()) return isler
    return isler.filter { katla(it.ad).contains(q) || katla(it.ilce).contains(q) }
}

fun sureMetni(saniye: Long): String = when {
    saniye <= 0 -> ""
    saniye < 60 -> "$saniye sn"
    else -> "${saniye / 60} dk" + (if (saniye % 60 != 0L) " ${saniye % 60} sn" else "")
}

/** 05321234567 → 0532 123 45 67; tanınmayan biçim olduğu gibi kalır. */
fun telBicim(tel: String): String {
    var d = tel.filter { it.isDigit() }
    if (d.startsWith("90") && d.length == 12) d = "0" + d.substring(2)
    if (d.length == 10 && d.startsWith("5")) d = "0$d"
    return if (d.length == 11) "${d.substring(0, 4)} ${d.substring(4, 7)} ${d.substring(7, 9)} ${d.substring(9)}" else tel
}
