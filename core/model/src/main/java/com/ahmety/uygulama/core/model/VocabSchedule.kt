package com.ahmety.uygulama.core.model

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
    /** FSRS dayanıklılığı, gün. null: kelime FSRS'ye henüz geçmedi. */
    val stability: Double? = null,
    /** FSRS zorluğu, 1 (kolay) – 10 (zor). */
    val difficulty: Double? = null,
)

/**
 * Tekrar günleri, öğrenme gününden sayarak: Blarma'nın merdiveni.
 *
 * Önceki FSRS her kelimeye kendi aralığını veriyordu; ne zaman neyin
 * geleceği belli değildi ve kullanıcı bunu sevmedi. Sabit merdiven: kelime
 * öğrenildikten 1, 3, 7, 30 ve 120 gün sonra geri geliyor. Beşinci tekrar da
 * bilinince kelime öğrenilmiş sayılıp programdan çıkıyor.
 */
val VOCAB_LADDER = intArrayOf(1, 3, 7, 30, 120)

/** FSRS döneminin merdiveni; o dönemden kalan kademeyi yenisine çevirmek için. */
private val OLD_LADDER = intArrayOf(1, 3, 7, 16, 30, 60, 120)

/**
 * Yeni merdivende yazılan satırın işareti. Zorluk FSRS'de 1–10 arası, ondan
 * önceki merdivende boştu; 0 yalnız bu merdivende yazılıyor. Bu sayede
 * eski bir satırın kademesi bir kez, ilk tekrarında çevriliyor; ayrı bir
 * veritabanı göçü ve iki telefon arasında çift çevirme riski yok.
 */
private const val LADDER_MARK = 0.0

private const val DAY_MILLIS = 86_400_000L

/** Üst üste kaç "geç"ten sonra kelime birinci kademeye iner. */
private const val POSTPONE_LIMIT = 3

/**
 * Kelimenin merdivendeki yeri, 1–5: sıradaki tekrarın [VOCAB_LADDER]'daki
 * sırası. Eski merdivenden gelen kelime, aralığı en yakın basamağa oturuyor.
 */
fun ladderStage(current: VocabSchedule): Int {
    if (current.difficulty == LADDER_MARK) return current.box.coerceIn(1, VOCAB_LADDER.size)
    if (current.box <= 0) return 1
    val oldDays = OLD_LADDER[(current.box - 1).coerceIn(0, OLD_LADDER.size - 1)]
    val index = VOCAB_LADDER.indexOfFirst { it >= oldDays }
    return if (index < 0) VOCAB_LADDER.size else index + 1
}

/**
 * Karara göre kelimenin bir sonraki tekrar tarihini hesaplar.
 *
 * [revealed] kartın anlamının açılıp açılmadığı. Açtıysan hatırlayamadın
 * demektir: kelime bir basamak geri iner ve yarın yeniden sorulur. Ayrı bir
 * "bilemedim" düğmesi gerekmiyor.
 */
fun nextSchedule(
    current: VocabSchedule,
    decision: VocabDecision,
    now: Long,
    revealed: Boolean = false,
    dayStart: Long = startOfDay(now),
): VocabSchedule {
    val firstStudy = decision == VocabDecision.STUDIED && current.lastReviewedAt == null
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
            // Hiç çalışılmamış kelimeyi geçmek onu tekrar programına sokmaz.
            // Tekrar, çalışılmış bir şeyin üstüne gelir; bakmadığın kelimenin
            // "tekrarı" olmaz — o hâlâ yeni kelimedir.
            if (current.lastReviewedAt == null) {
                current.copy(
                    status = VocabStatus.NEW,
                    dueAt = null,
                    postponeCount = current.postponeCount + 1,
                )
            } else {
                // Ertelemek kademeyi tüketmemeli: yorgun bir akşam bütün
                // destenin aralığı bozulmasın. Ama üst üste üçüncü geçiş,
                // kelimenin oturmadığını söylüyor: ilk basamağa iniyor.
                val count = current.postponeCount + 1
                val demoted = count >= POSTPONE_LIMIT
                current.copy(
                    status = VocabStatus.LEARNING,
                    box = if (demoted) 1 else ladderStage(current),
                    postponeCount = if (demoted) 0 else count,
                    lapseCount = current.lapseCount + if (demoted) 1 else 0,
                    stability = null,
                    difficulty = LADDER_MARK,
                    dueAt = dayStart + DAY_MILLIS,
                )
            }
        }

        VocabDecision.STUDIED -> {
            val studied = current.copy(
                status = VocabStatus.LEARNING,
                postponeCount = 0,
                reviewCount = current.reviewCount + 1,
                revealCount = current.revealCount + if (revealed) 1 else 0,
                lastReviewedAt = now,
                stability = null,
                difficulty = LADDER_MARK,
            )
            val stage = ladderStage(current)
            val isDue = current.dueAt == null || current.dueAt < dayStart + DAY_MILLIS
            when {
                // İlk çalışma öğrenme günü: ilk tekrar yarın.
                firstStudy -> studied.copy(box = 1, dueAt = dayStart + DAY_MILLIS)
                // Bilemedin: bir basamak geri, yarın yeniden.
                revealed -> studied.copy(
                    box = (stage - 1).coerceAtLeast(1),
                    lapseCount = current.lapseCount + 1,
                    dueAt = dayStart + DAY_MILLIS,
                )
                // Vadesi gelmeden ("Tümü"nde) bilinen kelime basamak atlamıyor:
                // merdiven günleri sabit, erken bakmak onları kısaltmasın.
                !isDue -> studied.copy(box = stage, dueAt = current.dueAt)
                // Beşinci tekrar (120. gün) da bilindi: öğrenildi.
                stage >= VOCAB_LADDER.size -> studied.copy(
                    status = VocabStatus.KNOWN,
                    box = VOCAB_LADDER.size,
                    dueAt = null,
                )
                // Sıradaki basamak; bekleme iki tekrar gününün farkı.
                else -> studied.copy(
                    box = stage + 1,
                    dueAt = dayStart + (VOCAB_LADDER[stage] - VOCAB_LADDER[stage - 1]) * DAY_MILLIS,
                )
            }
        }
    }
    // Öğrenme günü ilk çalışmanın günü; günlük yeni kelime hedefi buna bakıyor.
    return next.copy(introducedAt = if (firstStudy) now else current.introducedAt ?: now)
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
