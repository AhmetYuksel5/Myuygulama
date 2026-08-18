package com.ahmety.uygulama.core.model

import kotlin.math.abs

/** Kart üzerinde verilen karar. */
enum class VocabDecision {
    /** Yukarı: öğrendim, bir daha gösterme. */
    LEARNED,

    /** Sol: çalıştım, tekrar çalışacağım. */
    STUDIED,

    /** Sağ: şimdilik geç. */
    POSTPONE,

    /** Aşağı: önemsiz; desteden çıksın ama silinmesin. */
    IGNORE,
}

/**
 * Bir kelimenin tekrar programındaki yeri. Veritabanı satırının saf hâli;
 * zamanlama mantığı Android'e bağlı olmadan test edilebilsin diye burada.
 */
data class VocabSchedule(
    val word: String,
    val status: VocabStatus = VocabStatus.NEW,
    val box: Int = 0,
    val dueAt: Long? = null,
    val lastReviewedAt: Long? = null,
    val introducedAt: Long? = null,
    val reviewCount: Int = 0,
    val lapseCount: Int = 0,
    val postponeCount: Int = 0,
    val revealCount: Int = 0,
)

/**
 * Tekrar aralıkları, gün. Kullanıcının istediği 3/7/30 iskeleti korunuyor;
 * araya 16 giriyor çünkü 7'den 30'a atlamak 4,3 kat — Leitner de SM-2 de
 * aralığı 2-2,5 katla büyütür, o adımda unutma fırlıyor. Başa 1 ekleniyor
 * (ilk karşılaşma tekrar değil, öğrenmedir) ve 30'dan sonrası kapatılıyor,
 * yoksa kelime ya sonsuza kadar ayda bir gelir ya da programdan düşer.
 */
val VOCAB_LADDER = intArrayOf(1, 3, 7, 16, 30, 60, 120)

private const val DAY_MILLIS = 86_400_000L

/** Üst üste kaç "geç"ten sonra kelime birinci kademeye iner. */
private const val POSTPONE_LIMIT = 3

/**
 * Karara göre kelimenin bir sonraki tekrar tarihini hesaplar.
 *
 * [revealed] kartın anlamının açılıp açılmadığı. Açtıysan hatırlayamadın
 * demektir: kademe ilerlemez, aynı aralık bir kez daha denenir. Böylece ayrı
 * bir "hatırlayamadım" düğmesi koymadan zorluk sinyali alıyoruz.
 */
fun nextSchedule(
    current: VocabSchedule,
    decision: VocabDecision,
    now: Long,
    revealed: Boolean = false,
    dayStart: Long = startOfDay(now),
): VocabSchedule {
    val next = when (decision) {
        VocabDecision.LEARNED -> current.copy(
            status = VocabStatus.KNOWN,
            box = VOCAB_LADDER.size,
            dueAt = null,
            reviewCount = current.reviewCount + 1,
            lastReviewedAt = now,
        )

        VocabDecision.IGNORE -> current.copy(
            status = VocabStatus.IGNORED,
            dueAt = null,
        )

        VocabDecision.POSTPONE -> {
            // Ertelemek kademeyi tüketmemeli: yorgun bir akşam bütün destenin
            // aralığı bozulmasın. Ama üst üste üçüncü geçiş, kelimenin
            // oturmadığını söylüyor — Leitner'in "yanlışta ilk kutuya in"
            // kuralı orada devreye giriyor.
            val count = current.postponeCount + 1
            val demoted = count >= POSTPONE_LIMIT
            current.copy(
                status = VocabStatus.LEARNING,
                box = if (demoted) 1 else maxOf(current.box, 1),
                postponeCount = if (demoted) 0 else count,
                lapseCount = current.lapseCount + if (demoted) 1 else 0,
                dueAt = dayStart + DAY_MILLIS,
            )
        }

        VocabDecision.STUDIED -> {
            val box = if (revealed) {
                maxOf(current.box, 1)
            } else {
                minOf(current.box + 1, VOCAB_LADDER.size)
            }
            current.copy(
                status = VocabStatus.LEARNING,
                box = box,
                postponeCount = 0,
                reviewCount = current.reviewCount + 1,
                revealCount = current.revealCount + if (revealed) 1 else 0,
                lastReviewedAt = now,
                dueAt = dayStart + fuzzedDays(VOCAB_LADDER[box - 1], current.word) * DAY_MILLIS,
            )
        }
    }
    return next.copy(introducedAt = current.introducedAt ?: now)
}

/**
 * Aralığa ±%15 sapma: aynı gün eklenen kırk kelime aynı gün geri gelmesin.
 * Sapma kelimeden türetiliyor, rastgele değil — iki telefon aynı tarihi
 * bulmalı, yoksa senkron sonrası kelime iki kez sorulur.
 */
internal fun fuzzedDays(days: Int, word: String): Int {
    // Kısa aralıklarda sapmaya gerek yok: yığılma uzun aralıklarda oluyor,
    // ve 3 gün yerine 2 ya da 4 gün demek programın iskeletini bulanıklaştırır.
    if (days <= 7) return days
    val spread = days * 15 / 100
    val offset = abs(word.hashCode().toLong()) % (2L * spread + 1L) - spread
    return (days + offset).toInt().coerceAtLeast(days - spread)
}

/**
 * Günün başlangıcı — gece yarısı değil, yerel saatle sabah 04:00. Gece
 * bir'de çalışmak "yeni gün" saymamalı.
 *
 * [zoneOffsetMillis] cihazın UTC'ye göre farkı; çağıran veriyor ki bu dosya
 * saat dilimi kütüphanesine bağlanmasın ve test edilebilir kalsın.
 */
fun startOfDay(
    now: Long,
    zoneOffsetMillis: Long = 0L,
    dayStartHour: Int = 4,
): Long {
    val shift = zoneOffsetMillis + dayStartHour * 3_600_000L
    return ((now - shift) / DAY_MILLIS) * DAY_MILLIS + shift
}
