package com.ahmety.uygulama.core.model

/**
 * Seri (streak) hesapları. Saf fonksiyonlar — veritabanına, saate veya
 * Android'e bağlı değiller, bu yüzden test edilebilirler.
 *
 * İki önemli davranış kararı:
 *
 * 1. **Bugün henüz seriyi bozmaz.** Günün alışkanlığını akşam yapacaksan,
 *    sabah uygulamayı açtığında serinin sıfırlanmış görünmesi yanlış olur.
 *    Bu yüzden bugün "yapılması gereken ama henüz yapılmamış" ise seri
 *    dünden geriye sayılır.
 * 2. **Yapılması gerekmeyen gün seriyi bozmaz.** Haftanın belirli günlerine
 *    kurulmuş bir alışkanlıkta araya giren günler atlanır.
 */
object HabitStreaks {

    private const val MAX_LOOKBACK_DAYS = 3650
    private const val MAX_LOOKBACK_WEEKS = 520

    /** 0 = Pazartesi … 6 = Pazar. Epoch günü 0 (1970-01-01) bir Perşembedir. */
    fun dayOfWeekIndex(epochDay: Int): Int = ((epochDay + 3) % 7 + 7) % 7

    /** İçinde bulunulan haftanın Pazartesi günü. */
    fun weekStart(epochDay: Int): Int = epochDay - dayOfWeekIndex(epochDay)

    fun isDue(schedule: HabitSchedule, epochDay: Int): Boolean = when (schedule) {
        HabitSchedule.Daily -> true
        is HabitSchedule.SpecificDays ->
            (schedule.daysMask shr dayOfWeekIndex(epochDay)) and 1 == 1
        // Haftada N kez: hangi gün yapıldığı serbest, o yüzden her gün uygundur.
        is HabitSchedule.TimesPerWeek -> true
    }

    /**
     * Bugüne kadar kesintisiz süren seri.
     * Haftada-N-kez alışkanlıklarında birim gün değil **hafta**dır.
     */
    fun currentStreak(
        schedule: HabitSchedule,
        completedDates: Set<Int>,
        today: Int,
    ): Int = when (schedule) {
        is HabitSchedule.TimesPerWeek -> currentWeekStreak(schedule.times, completedDates, today)
        is HabitSchedule.SpecificDays ->
            if (schedule.daysMask == 0) 0 else currentDayStreak(schedule, completedDates, today)
        HabitSchedule.Daily -> currentDayStreak(schedule, completedDates, today)
    }

    private fun currentDayStreak(
        schedule: HabitSchedule,
        completedDates: Set<Int>,
        today: Int,
    ): Int {
        var day = today
        // Bugün yapılması gerekiyor ama henüz yapılmadıysa seriyi bozmadan dünden başla.
        if (isDue(schedule, day) && day !in completedDates) day--

        var streak = 0
        var scanned = 0
        while (scanned < MAX_LOOKBACK_DAYS) {
            scanned++
            if (!isDue(schedule, day)) {
                day--
                continue
            }
            if (day in completedDates) {
                streak++
                day--
            } else {
                break
            }
        }
        return streak
    }

    private fun currentWeekStreak(
        times: Int,
        completedDates: Set<Int>,
        today: Int,
    ): Int {
        if (times <= 0) return 0
        var week = weekStart(today)
        // İçinde bulunduğumuz hafta henüz bitmedi; hedefe ulaşılmadıysa seriyi bozmaz.
        if (completedInWeek(completedDates, week) < times) week -= 7

        var streak = 0
        var scanned = 0
        while (scanned < MAX_LOOKBACK_WEEKS && completedInWeek(completedDates, week) >= times) {
            scanned++
            streak++
            week -= 7
        }
        return streak
    }

    private fun completedInWeek(completedDates: Set<Int>, weekStart: Int): Int =
        (0..6).count { weekStart + it in completedDates }

    /** Kayıtlardaki en uzun seri. */
    fun longestStreak(
        schedule: HabitSchedule,
        completedDates: Set<Int>,
        today: Int,
    ): Int {
        if (completedDates.isEmpty()) return 0
        return when (schedule) {
            is HabitSchedule.TimesPerWeek -> longestWeekStreak(schedule.times, completedDates)
            is HabitSchedule.SpecificDays ->
                if (schedule.daysMask == 0) 0 else longestDayStreak(schedule, completedDates, today)
            HabitSchedule.Daily -> longestDayStreak(schedule, completedDates, today)
        }
    }

    private fun longestDayStreak(
        schedule: HabitSchedule,
        completedDates: Set<Int>,
        today: Int,
    ): Int {
        val start = completedDates.min()
        val end = minOf(completedDates.max(), today)
        var best = 0
        var run = 0
        for (day in start..end) {
            if (!isDue(schedule, day)) continue
            if (day in completedDates) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
        }
        return best
    }

    private fun longestWeekStreak(times: Int, completedDates: Set<Int>): Int {
        if (times <= 0) return 0
        var week = weekStart(completedDates.min())
        val lastWeek = weekStart(completedDates.max())
        var best = 0
        var run = 0
        while (week <= lastWeek) {
            if (completedInWeek(completedDates, week) >= times) {
                run++
                if (run > best) best = run
            } else {
                run = 0
            }
            week += 7
        }
        return best
    }
}
