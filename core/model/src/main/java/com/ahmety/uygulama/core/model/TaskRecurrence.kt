package com.ahmety.uygulama.core.model

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * Tekrarlayan görevin bir sonraki tarihini hesaplar. Saf fonksiyon — bugünün
 * tarihini dışarıdan alır, böylece test edilebilir.
 */
object TaskRecurrence {

    /**
     * @param currentDue görevin mevcut tarihi (epoch gün), yoksa null
     * @param completedOn görevin tamamlandığı gün (epoch gün)
     */
    fun nextDueDate(rule: RecurrenceRule, currentDue: Int?, completedOn: Int): Int {
        val interval = rule.interval.coerceAtLeast(1)
        // "Tamamlanınca say" seçiliyse veya görevin tarihi yoksa, sayacı bugünden başlat.
        val base = if (rule.fromCompletion || currentDue == null) completedOn else currentDue

        return when (rule.unit) {
            RecurrenceUnit.DAY -> base + interval
            RecurrenceUnit.WEEK -> nextWeeklyDate(base, rule.daysMask, interval)
            RecurrenceUnit.MONTH -> base.shiftBy(DatePeriod(months = interval))
            RecurrenceUnit.YEAR -> base.shiftBy(DatePeriod(years = interval))
        }
    }

    /**
     * Haftalık tekrarda birden çok gün seçilmişse (ör. Pzt + Per), sıradaki tarih
     * aynı hafta içindeki bir sonraki seçili gündür. Haftayı devrettiğimizde
     * aralık kadar hafta atlanır.
     */
    private fun nextWeeklyDate(base: Int, daysMask: Int, interval: Int): Int {
        if (daysMask == 0) return base + 7 * interval

        val baseWeek = HabitStreaks.weekStart(base)
        for (offset in 1..7) {
            val candidate = base + offset
            val index = HabitStreaks.dayOfWeekIndex(candidate)
            if ((daysMask shr index) and 1 != 1) continue
            val weeksAhead = (HabitStreaks.weekStart(candidate) - baseWeek) / 7
            return if (weeksAhead > 0) candidate + 7 * (interval - 1) else candidate
        }
        return base + 7 * interval
    }

    private fun Int.shiftBy(period: DatePeriod): Int =
        LocalDate.fromEpochDays(this).plus(period).toEpochDays()
}
