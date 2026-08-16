package com.ahmety.uygulama.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitStreaksTest {

    // 2026-08-17 bir Pazartesi; epoch günü 20682.
    private val monday = 20682

    @Test
    fun `epoch gunu 0 persembedir`() {
        assertEquals(3, HabitStreaks.dayOfWeekIndex(0))
    }

    @Test
    fun `hafta basi pazartesiye denk gelir`() {
        assertEquals(0, HabitStreaks.dayOfWeekIndex(monday))
        assertEquals(monday, HabitStreaks.weekStart(monday))
        assertEquals(monday, HabitStreaks.weekStart(monday + 6))
        assertEquals(monday + 7, HabitStreaks.weekStart(monday + 7))
    }

    @Test
    fun `negatif epoch gunlerinde de dogru gun indeksi`() {
        assertEquals(2, HabitStreaks.dayOfWeekIndex(-1))
        assertTrue(HabitStreaks.dayOfWeekIndex(-1000) in 0..6)
    }

    @Test
    fun `gunluk aliskanlikta kesintisiz seri sayilir`() {
        val completed = setOf(monday - 2, monday - 1, monday)
        assertEquals(3, HabitStreaks.currentStreak(HabitSchedule.Daily, completed, monday))
    }

    @Test
    fun `bugun henuz yapilmadiysa seri bozulmaz`() {
        // Bugün işaretlenmemiş ama önceki üç gün tamam: seri hâlâ 3 görünmeli.
        val completed = setOf(monday - 3, monday - 2, monday - 1)
        assertEquals(3, HabitStreaks.currentStreak(HabitSchedule.Daily, completed, monday))
    }

    @Test
    fun `dun atlanmissa seri sifirlanir`() {
        val completed = setOf(monday - 5, monday - 4, monday - 3)
        assertEquals(0, HabitStreaks.currentStreak(HabitSchedule.Daily, completed, monday))
    }

    @Test
    fun `belirli gunlerde aradaki bos gunler seriyi bozmaz`() {
        // Pazartesi + Çarşamba + Cuma (bit 0, 2, 4)
        val schedule = HabitSchedule.SpecificDays(daysMask = 0b0010101)
        assertTrue(HabitStreaks.isDue(schedule, monday))
        assertFalse(HabitStreaks.isDue(schedule, monday + 1))
        assertTrue(HabitStreaks.isDue(schedule, monday + 2))

        // Önceki hafta Pzt/Çar/Cum ve bu hafta Pazartesi yapılmış.
        val completed = setOf(monday - 7, monday - 5, monday - 3, monday)
        assertEquals(4, HabitStreaks.currentStreak(schedule, completed, monday))
    }

    @Test
    fun `belirli gunlerde kacirilan gun seriyi bozar`() {
        val schedule = HabitSchedule.SpecificDays(daysMask = 0b0010101)
        // Çarşamba (monday - 5) atlanmış.
        val completed = setOf(monday - 7, monday - 3, monday)
        assertEquals(2, HabitStreaks.currentStreak(schedule, completed, monday))
    }

    @Test
    fun `bos gun maskesi sonsuz donguye girmez`() {
        val schedule = HabitSchedule.SpecificDays(daysMask = 0)
        assertEquals(0, HabitStreaks.currentStreak(schedule, setOf(monday), monday))
        assertEquals(0, HabitStreaks.longestStreak(schedule, setOf(monday), monday))
    }

    @Test
    fun `haftada uc kez hedefinde seri hafta bazinda sayilir`() {
        val schedule = HabitSchedule.TimesPerWeek(times = 3)
        val onceki = monday - 7
        val ondanOnceki = monday - 14
        val completed = setOf(
            ondanOnceki, ondanOnceki + 2, ondanOnceki + 4,
            onceki, onceki + 1, onceki + 5,
        )
        // Bu hafta henüz hedefe ulaşmadı ama seriyi bozmuyor.
        assertEquals(2, HabitStreaks.currentStreak(schedule, completed, monday))
    }

    @Test
    fun `haftada uc kez hedefi tutmayan hafta seriyi bozar`() {
        val schedule = HabitSchedule.TimesPerWeek(times = 3)
        val onceki = monday - 7
        val ondanOnceki = monday - 14
        val completed = setOf(
            ondanOnceki, ondanOnceki + 2, ondanOnceki + 4,
            onceki, onceki + 1, // sadece iki gün
        )
        assertEquals(0, HabitStreaks.currentStreak(schedule, completed, monday))
    }

    @Test
    fun `en uzun seri gecmisteki en iyi kosuyu bulur`() {
        val completed = buildSet {
            addAll((monday - 30..monday - 26)) // 5 gün
            addAll((monday - 20..monday - 18)) // 3 gün
            add(monday)
        }
        assertEquals(5, HabitStreaks.longestStreak(HabitSchedule.Daily, completed, monday))
    }

    @Test
    fun `kayit yoksa seriler sifirdir`() {
        assertEquals(0, HabitStreaks.currentStreak(HabitSchedule.Daily, emptySet(), monday))
        assertEquals(0, HabitStreaks.longestStreak(HabitSchedule.Daily, emptySet(), monday))
    }

    @Test
    fun `gunluk durum hedefe gore tamamlanma hesaplar`() {
        val status = HabitDayStatus(date = monday, count = 2, target = 3)
        assertFalse(status.isComplete)
        assertEquals(2f / 3f, status.fraction, 0.0001f)
        assertTrue(HabitDayStatus(monday, 3, 3).isComplete)
        assertTrue(HabitDayStatus(monday, 5, 3).isComplete)
        assertEquals(1f, HabitDayStatus(monday, 5, 3).fraction, 0.0001f)
    }
}
