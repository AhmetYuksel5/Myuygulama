package com.ahmety.uygulama.feature.habits

import com.ahmety.uygulama.core.model.HabitSchedule

internal val dayShortNames = listOf("Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz")

internal fun scheduleLabel(schedule: HabitSchedule): String = when (schedule) {
    HabitSchedule.Daily -> "Her gün"
    is HabitSchedule.TimesPerWeek -> "Haftada ${schedule.times} kez"
    is HabitSchedule.SpecificDays -> {
        val days = dayShortNames.filterIndexed { index, _ ->
            (schedule.daysMask shr index) and 1 == 1
        }
        if (days.isEmpty()) "Gün seçilmedi" else days.joinToString(", ")
    }
}

/**
 * Seri birimi programa göre değişiyor: haftada-N-kez alışkanlıklarında
 * seriyi gün değil hafta cinsinden sayıyoruz, o yüzden etiketi de öyle yazmalı.
 */
internal fun streakLabel(schedule: HabitSchedule, streak: Int): String = when (schedule) {
    is HabitSchedule.TimesPerWeek -> "$streak hafta seri"
    else -> "$streak gün seri"
}
