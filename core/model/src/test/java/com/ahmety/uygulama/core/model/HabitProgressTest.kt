package com.ahmety.uygulama.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HabitProgressTest {

    @Test
    fun `puan tamamlama ve seri toplamindan gelir`() {
        // 10 tamamlama * 10 + 4 seri günü * 5 = 120
        assertEquals(120, HabitProgress.score(totalCompletions = 10, activeStreakSum = 4))
    }

    @Test
    fun `sifir puanda seviye bir`() {
        val level = HabitProgress.levelFor(0)
        assertEquals(1, level.level)
        assertEquals(0f, level.progress, 0.0001f)
    }

    @Test
    fun `seviye esikleri karesel buyur`() {
        // BASE=100: seviye tabanları 0, 100, 400, 900...
        assertEquals(1, HabitProgress.levelFor(99).level)
        assertEquals(2, HabitProgress.levelFor(100).level)
        assertEquals(2, HabitProgress.levelFor(399).level)
        assertEquals(3, HabitProgress.levelFor(400).level)
        assertEquals(4, HabitProgress.levelFor(900).level)
    }

    @Test
    fun `ilerleme ve sonraki seviyeye kalan tutarli`() {
        val level = HabitProgress.levelFor(250) // seviye 2, taban 100, tavan 400
        assertEquals(2, level.level)
        assertEquals(100, level.currentLevelFloor)
        assertEquals(400, level.nextLevelFloor)
        assertEquals(0.5f, level.progress, 0.0001f) // (250-100)/(400-100)=0.5
        assertEquals(150, level.pointsToNext)
    }

    @Test
    fun `negatif puan seviye bire kirpilir`() {
        val level = HabitProgress.levelFor(-50)
        assertEquals(1, level.level)
        assertTrue(level.score >= 0)
    }
}
