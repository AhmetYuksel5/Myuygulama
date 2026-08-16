package com.ahmety.uygulama.core.model

/**
 * Bir alışkanlık tanımı. İşaretlemeler ayrı tutulur ([HabitCheck]), çünkü
 * işaretleme `(alışkanlık, gün)` anahtarlı ve idempotenttir — hangi telefondan
 * işaretlediğin fark etmez, sonuç aynıdır. Bu, iki cihaz senkronunda
 * çakışma üretmeyen bir veri şekli.
 */
data class Habit(
    val id: Long = 0L,
    val uuid: String,
    val name: String,
    val description: String = "",
    val schedule: HabitSchedule = HabitSchedule.Daily,
    /** Gün içinde kaç kez yapılması hedefleniyor (ör. günde 3 bardak su). */
    val targetPerDay: Int = 1,
    val colorArgb: Int? = null,
    /** Gün başlangıcından itibaren dakika; null ise hatırlatıcı yok. */
    val reminderMinuteOfDay: Int? = null,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

sealed interface HabitSchedule {
    /** Her gün. */
    data object Daily : HabitSchedule

    /**
     * Haftanın belirli günleri. [daysMask] bit maskesi: bit 0 = Pazartesi …
     * bit 6 = Pazar.
     */
    data class SpecificDays(val daysMask: Int) : HabitSchedule

    /** Haftada [times] kez; hangi günler olduğu serbest. */
    data class TimesPerWeek(val times: Int) : HabitSchedule
}

/**
 * Bir alışkanlığın belirli bir gündeki durumu.
 *
 * [date] epoch gün sayısıdır (1970-01-01 = 0) — saat dilimi taşımaz, çünkü
 * "bugün yaptım mı" sorusu yerel takvim günüyle ilgilidir.
 */
data class HabitCheck(
    val habitUuid: String,
    val date: Int,
    /** Kaç kez yapıldı. 0 = işaretlenmemiş. */
    val count: Int,
    val updatedAt: Long,
)

/** Bir günün, alışkanlığın hedefine göre tamamlanma durumu. */
data class HabitDayStatus(
    val date: Int,
    val count: Int,
    val target: Int,
) {
    val isComplete: Boolean get() = target > 0 && count >= target
    val fraction: Float get() = if (target <= 0) 0f else (count.toFloat() / target).coerceIn(0f, 1f)
}
