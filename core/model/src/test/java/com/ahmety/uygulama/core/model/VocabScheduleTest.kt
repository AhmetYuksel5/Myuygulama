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
    fun `pes pese calismak merdiveni tirmanir`() {
        var state = schedule()
        val gaps = mutableListOf<Int>()
        repeat(VOCAB_LADDER.size) {
            state = nextSchedule(state, VocabDecision.STUDIED, now, dayStart = today)
            gaps += ((state.dueAt!! - today) / day).toInt()
        }
        // İlk üç kademe sapmasız: kullanıcının istediği 1, 3, 7 gün.
        assertEquals(listOf(1, 3, 7), gaps.take(3))
        // Dördüncüsü 16 gün, sapmayla birlikte.
        assertTrue("${gaps[3]}", gaps[3] in 14..18)
        // Sonrakiler artan sırada ve son kademede takılı kalıyor.
        assertTrue(gaps.zipWithNext().all { (a, b) -> b >= a })
        assertEquals(VOCAB_LADDER.size, state.box)
    }

    @Test
    fun `anlami acinca kademe ilerlemez`() {
        val first = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        val second = nextSchedule(first, VocabDecision.STUDIED, now, revealed = true, dayStart = today)
        assertEquals(first.box, second.box)
        assertEquals(1, second.revealCount)
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
