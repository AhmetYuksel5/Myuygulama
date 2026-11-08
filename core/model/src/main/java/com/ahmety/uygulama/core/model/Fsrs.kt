package com.ahmety.uygulama.core.model

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * FSRS-6: Anki'nin varsayılan tekrar algoritması.
 *
 * Sabit merdiven (1-3-7-16-30-60-120 gün) her kelimeye aynı aralığı
 * veriyordu: kolay kelime de zor kelime de, zamanında tekrarlanan da bir
 * hafta gecikmiş olan da. FSRS her kelime için iki sayı tutuyor —
 * hafızadaki dayanıklılığı (stability, gün) ve zorluğu (1–10) — ve bir
 * sonraki tekrarı, hatırlama ihtimali %90'a düşeceği güne koyuyor. Açık
 * karşılaştırmada kullanıcıların %99,6'sında eski SM-2'den isabetli çıktı.
 *
 * Ağırlıklar FSRS-6'nın varsayılanları. Kişiye göre uydurma ileride, yeterli
 * tekrar biriktiğinde yapılabilir; varsayılanlar da tek başına iyi çalışıyor.
 */
object Fsrs {

    /** Notlar: 1 unuttum, 2 zor, 3 iyi, 4 kolay. Merkez 1 ve 3'ü kullanıyor. */
    const val AGAIN = 1
    const val GOOD = 3

    /** Hedeflenen hatırlama oranı. Anki'nin varsayılanı. */
    const val RETENTION = 0.9

    /** Bir kelime en fazla bu kadar gün sonra yeniden gelir. */
    const val MAX_INTERVAL_DAYS = 365

    private val W = doubleArrayOf(
        0.212, 1.2931, 2.3065, 8.2956, 6.4133, 0.8334, 3.0194, 0.001, 1.8722, 0.1666,
        0.796, 1.4835, 0.0614, 0.2629, 1.6483, 0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
        0.1542,
    )

    private val DECAY = -W[20]
    private val FACTOR = 0.9.pow(1.0 / DECAY) - 1.0

    private const val MIN_STABILITY = 0.1

    fun initialStability(grade: Int): Double = W[grade - 1].coerceAtLeast(MIN_STABILITY)

    fun initialDifficulty(grade: Int): Double =
        (W[4] - exp(W[5] * (grade - 1)) + 1.0).coerceIn(1.0, 10.0)

    /** [elapsedDays] gün sonra hatırlama ihtimali. */
    fun retrievability(elapsedDays: Double, stability: Double): Double =
        (1.0 + FACTOR * elapsedDays.coerceAtLeast(0.0) / stability).pow(DECAY)

    fun nextDifficulty(difficulty: Double, grade: Int): Double {
        val delta = -W[6] * (grade - 3)
        // Uçlara yaklaştıkça değişim yavaşlıyor; zorluk 10'a çakılıp kalmasın.
        val damped = difficulty + delta * (10.0 - difficulty) / 9.0
        val reverted = W[7] * initialDifficulty(4) + (1.0 - W[7]) * damped
        return reverted.coerceIn(1.0, 10.0)
    }

    /**
     * Tekrardan sonraki dayanıklılık.
     *
     * Aynı gün içindeki tekrar ayrı hesaplanıyor: birkaç dakika önce
     * görülen kelimeyi hatırlamak, hafızanın güçlendiğine dair zayıf bir
     * işaret.
     */
    fun nextStability(
        difficulty: Double,
        stability: Double,
        elapsedDays: Double,
        grade: Int,
    ): Double {
        if (elapsedDays < 1.0) {
            var factor = exp(W[17] * (grade - 3 + W[18])) * stability.pow(-W[19])
            if (grade >= 3) factor = factor.coerceAtLeast(1.0)
            return (stability * factor).coerceAtLeast(MIN_STABILITY)
        }
        val r = retrievability(elapsedDays, stability)
        if (grade == AGAIN) {
            val forgot = W[11] * difficulty.pow(-W[12]) * ((stability + 1.0).pow(W[13]) - 1.0) *
                exp(W[14] * (1.0 - r))
            val ceiling = stability / exp(W[17] * W[18])
            return minOf(forgot, ceiling).coerceAtLeast(MIN_STABILITY)
        }
        val hard = if (grade == 2) W[15] else 1.0
        val easy = if (grade == 4) W[16] else 1.0
        val growth = exp(W[8]) * (11.0 - difficulty) * stability.pow(-W[9]) *
            (exp(W[10] * (1.0 - r)) - 1.0) * hard * easy
        return (stability * (growth + 1.0)).coerceAtLeast(MIN_STABILITY)
    }

    /** Hatırlama ihtimalinin [RETENTION]'a düşeceği gün sayısı. */
    fun intervalDays(stability: Double): Int {
        val days = stability / FACTOR * (RETENTION.pow(1.0 / DECAY) - 1.0)
        return days.roundToInt().coerceIn(1, MAX_INTERVAL_DAYS)
    }
}
