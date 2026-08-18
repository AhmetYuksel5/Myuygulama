package com.ahmety.uygulama.feature.vocab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * İngilizce seviye tespiti.
 *
 * 10.000 kelime **sıklık sırasına** göre diziliyor (OpenSubtitles derlemesi —
 * ölçmek istediğimiz şey tam olarak film İngilizcesi). Kelimeler tek tek
 * geliyor, "biliyorum / bilmiyorum" diye işaretleniyor. Anlam, örnek, öbek
 * yok: bu bir sınav, çalışma destesi değil.
 *
 * Sonuç iki işe yarıyor: seviyeni söylemek ve altyazıdan kelime çıkarırken
 * bildiğin sıklık aralığını atlamak.
 */
@Singleton
class LevelTestStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cached: List<String>? = null

    /** Sıklık sırasına göre 10.000 kelime. */
    fun words(): List<String> = cached ?: load().also { cached = it }

    /**
     * Verilen cevaplar: her karakter bir kelime — '1' biliyorum, '0' bilmiyorum.
     * Dizideki konum kelimenin sıklık sırası.
     */
    var answers: String
        get() = prefs.getString(KEY_ANSWERS, "").orEmpty()
        private set(value) = prefs.edit().putString(KEY_ANSWERS, value).apply()

    fun answer(known: Boolean) {
        answers += if (known) '1' else '0'
    }

    /** Son cevabı geri alır: yanlış tuşa basmak sık. */
    fun undo() {
        answers = answers.dropLast(1)
    }

    fun reset() {
        answers = ""
    }

    private fun load(): List<String> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val PREFS_NAME = "merkez_seviye"
        const val KEY_ANSWERS = "answers"
        const val ASSET_NAME = "vocab_level_10k.txt"
    }
}

/** Sınavın o anki tablosu. */
data class LevelEstimate(
    val answered: Int,
    val known: Int,
    /**
     * Tahmini kelime hazinesi: her sıklık dilimindeki bilme oranı, o dilimin
     * kelime sayısıyla çarpılıp toplanıyor. Böylece 300 kelime cevaplayıp
     * bıraksan bile makul bir sayı çıkıyor.
     */
    val estimatedVocabulary: Int,
    val level: String,
    /** Bu sıklık sırasına kadar olan kelimeleri büyük ölçüde biliyorsun. */
    val knownUpToRank: Int,
) {
    val unknown: Int get() = answered - known
}

/**
 * Sıklık dilimleri. Kelime bilgisi sıklıkla hızla düştüğü için eşit değil,
 * genişleyen dilimler kullanıyoruz: ilk 1000 kelimeyi bilmek ile 9.000'inciyi
 * bilmek aynı şey değil.
 */
private val BANDS = listOf(0, 250, 500, 1000, 2000, 3000, 5000, 7500, 10000)

fun estimateLevel(answers: String, total: Int = 10_000): LevelEstimate {
    val answered = answers.length
    val known = answers.count { it == '1' }
    if (answered == 0) {
        return LevelEstimate(0, 0, 0, "—", 0)
    }

    var vocabulary = 0.0
    var lastSolidRank = 0
    for (index in 0 until BANDS.lastIndex) {
        val from = BANDS[index]
        val to = minOf(BANDS[index + 1], total)
        if (from >= answered) break
        val slice = answers.substring(from, minOf(to, answered))
        if (slice.isEmpty()) continue
        val ratio = slice.count { it == '1' }.toFloat() / slice.length
        vocabulary += ratio * (to - from)
        // %80'in üstünde bildiğin en son dilim: altyazı süzgecinin eşiği.
        if (ratio >= 0.8f) lastSolidRank = to
    }

    // Cevaplanmamış kısım için son dilimin oranını sürdürmüyoruz; ölçmediğimiz
    // yeri tahmin etmek sayıyı şişiriyor. Onun yerine son dilimin oranını
    // yarısına indirip uyguluyoruz.
    if (answered < total) {
        val tailRatio = answers.takeLast(minOf(200, answered)).count { it == '1' }
            .toFloat() / minOf(200, answered)
        vocabulary += (tailRatio / 2f) * (total - answered)
    }

    val size = vocabulary.toInt()
    return LevelEstimate(
        answered = answered,
        known = known,
        estimatedVocabulary = size,
        level = levelFor(size),
        knownUpToRank = lastSolidRank,
    )
}

/**
 * Kelime hazinesi → Avrupa dil çerçevesi kabaca. Kesin bir sınav değil;
 * nerede durduğunu göstermek için.
 */
private fun levelFor(size: Int): String = when {
    size < 800 -> "A1"
    size < 1600 -> "A2"
    size < 2800 -> "B1"
    size < 4500 -> "B2"
    size < 7000 -> "C1"
    else -> "C2"
}
