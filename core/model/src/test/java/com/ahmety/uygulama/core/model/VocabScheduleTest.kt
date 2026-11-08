package com.ahmety.uygulama.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabScheduleTest {

    private val day = 86_400_000L
    private val now = 1_700_000_000_000L
    private val today = startOfDay(now)

    private fun schedule(word: String = "abundant") = VocabSchedule(word = word)

    @Test
    fun `zamaninda tekrar araligi buyutur`() {
        var state = schedule()
        var time = now
        val gaps = mutableListOf<Int>()
        // Dört tekrar: daha fazlası bir yıllık üst sınıra dayanıyor.
        repeat(4) {
            val dayStart = startOfDay(time)
            state = nextSchedule(state, VocabDecision.STUDIED, time, dayStart = dayStart)
            gaps += ((state.dueAt!! - dayStart) / day).toInt()
            time = state.dueAt!! + 3_600_000L
        }
        // İlk "iyi"den sonra FSRS-6'nın başlangıç dayanıklılığı: 2,3 gün.
        assertEquals(2, gaps.first())
        assertTrue("$gaps", gaps.zipWithNext().all { (a, b) -> b > a })
        assertTrue("$gaps", gaps.last() > 60)
    }

    @Test
    fun `anlami acinca aralik buyumez`() {
        val first = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        val later = first.dueAt!! + 3_600_000L
        val second = nextSchedule(first, VocabDecision.STUDIED, later, revealed = true, dayStart = startOfDay(later))
        val firstGap = first.dueAt!! - today
        val secondGap = second.dueAt!! - startOfDay(later)
        assertTrue("$firstGap $secondGap", secondGap <= firstGap)
        assertEquals(1, second.revealCount)
    }

    @Test
    fun `merdivenden gelen kelime yerinden devam eder`() {
        // Merdivende 30 günlük basamakta, zamanında tekrarlanıyor.
        val old = schedule().copy(
            status = VocabStatus.LEARNING,
            box = 5,
            lastReviewedAt = now - 30 * day,
            reviewCount = 5,
        )
        val next = nextSchedule(old, VocabDecision.STUDIED, now, dayStart = today)
        val gap = ((next.dueAt!! - today) / day).toInt()
        assertTrue("$gap", gap > 30)
        assertTrue(next.stability!! > 30.0)
    }

    @Test
    fun `ogrendim programdan cikarir`() {
        val state = nextSchedule(schedule(), VocabDecision.LEARNED, now, dayStart = today)
        assertEquals(VocabStatus.KNOWN, state.status)
        assertNull(state.dueAt)
    }

    @Test
    fun `onemsiz programdan cikarir ama silmez`() {
        val state = nextSchedule(schedule(), VocabDecision.IGNORE, now, dayStart = today)
        assertEquals(VocabStatus.IGNORED, state.status)
        assertNull(state.dueAt)
    }

    @Test
    fun `gec kademeyi tuketmez ama ucuncude dusurur`() {
        var state = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        state = nextSchedule(state, VocabDecision.STUDIED, now, dayStart = today)
        val box = state.box

        state = nextSchedule(state, VocabDecision.POSTPONE, now, dayStart = today)
        assertEquals(box, state.box)
        assertEquals(today + day, state.dueAt)

        state = nextSchedule(state, VocabDecision.POSTPONE, now, dayStart = today)
        assertEquals(box, state.box)

        state = nextSchedule(state, VocabDecision.POSTPONE, now, dayStart = today)
        assertEquals(1, state.box)
        assertEquals(1, state.lapseCount)
        assertEquals(0, state.postponeCount)
    }

    @Test
    fun `hic calisilmamis kelime gecilince programa girmiyor`() {
        // "Bakmadığım kelimenin tekrarı olmaz": sağa atmak kelimeyi yeni
        // bırakıyor, ertesi güne tekrar diye yazmıyor.
        val state = nextSchedule(schedule(), VocabDecision.POSTPONE, now, dayStart = today)
        assertEquals(VocabStatus.NEW, state.status)
        assertNull(state.dueAt)
        assertEquals(0, state.box)
    }

    @Test
    fun `calisilmis kelime gecilince yarina kaliyor`() {
        var state = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        state = nextSchedule(state, VocabDecision.POSTPONE, now, dayStart = today)
        assertEquals(VocabStatus.LEARNING, state.status)
        assertEquals(today + day, state.dueAt)
    }

    @Test
    fun `calismak gec sayacini sifirlar`() {
        var state = nextSchedule(schedule(), VocabDecision.POSTPONE, now, dayStart = today)
        assertEquals(1, state.postponeCount)
        state = nextSchedule(state, VocabDecision.STUDIED, now, dayStart = today)
        assertEquals(0, state.postponeCount)
    }

    @Test
    fun `sapma kelimeden turetiliyor ve sinirli`() {
        // Aynı kelime her zaman aynı sonucu vermeli: iki telefon aynı tarihi
        // bulmazsa kelime iki kez sorulur.
        assertEquals(fuzzedDays(30, "abundant"), fuzzedDays(30, "abundant"))
        listOf("abundant", "cease", "vivid", "yield").forEach { word ->
            val days = fuzzedDays(30, word)
            assertTrue("$word -> $days", days in 26..35)
        }
        // Kısa aralıklara sapma uygulanmıyor.
        assertEquals(3, fuzzedDays(3, "abundant"))
        assertEquals(7, fuzzedDays(7, "abundant"))
    }

    @Test
    fun `gun sabah dortte basliyor`() {
        val zone = 3 * 3_600_000L
        val threeAm = startOfDay(now, zone) + 23 * 3_600_000L
        // 03:00 hâlâ dünkü gün: gece çalışmak yeni gün saymamalı.
        assertEquals(startOfDay(now, zone), startOfDay(threeAm, zone))
        val fiveAm = startOfDay(now, zone) + 25 * 3_600_000L
        assertEquals(startOfDay(now, zone) + day, startOfDay(fiveAm, zone))
    }
}
