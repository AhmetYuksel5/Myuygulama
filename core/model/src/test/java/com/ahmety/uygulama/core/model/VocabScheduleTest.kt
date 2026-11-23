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

    private fun daysUntilDue(state: VocabSchedule, dayStart: Long) =
        ((state.dueAt!! - dayStart) / day).toInt()

    @Test
    fun `ogrenme gunundan 1 3 7 30 120 gunlerde gelir`() {
        var state = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        val learnDay = today
        val reviewDays = mutableListOf<Int>()
        while (state.status == VocabStatus.LEARNING) {
            val due = state.dueAt!!
            reviewDays += ((due - learnDay) / day).toInt()
            state = nextSchedule(state, VocabDecision.STUDIED, due + 3_600_000L, dayStart = due)
        }
        assertEquals(listOf(1, 3, 7, 30, 120), reviewDays)
        // 120. gün tekrarı da bilindi: programdan çıktı.
        assertEquals(VocabStatus.KNOWN, state.status)
        assertNull(state.dueAt)
    }

    @Test
    fun `bilemeyince bir basamak geri ve yarin`() {
        var state = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        // 1. ve 3. gün tekrarları bilindi; şimdi 7. gün basamağında (3).
        repeat(2) {
            val due = state.dueAt!!
            state = nextSchedule(state, VocabDecision.STUDIED, due, dayStart = due)
        }
        assertEquals(3, state.box)
        val due = state.dueAt!!
        state = nextSchedule(state, VocabDecision.STUDIED, due, revealed = true, dayStart = due)
        assertEquals(2, state.box)
        assertEquals(1, state.lapseCount)
        assertEquals(1, daysUntilDue(state, due))
        // Ertesi gün bilinince yeniden 7. gün basamağına: 4 gün bekleme.
        val tomorrow = state.dueAt!!
        state = nextSchedule(state, VocabDecision.STUDIED, tomorrow, dayStart = tomorrow)
        assertEquals(3, state.box)
        assertEquals(4, daysUntilDue(state, tomorrow))
    }

    @Test
    fun `ilk basamakta bilemeyen ilk basamakta kalir`() {
        var state = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        val due = state.dueAt!!
        state = nextSchedule(state, VocabDecision.STUDIED, due, revealed = true, dayStart = due)
        assertEquals(1, state.box)
        assertEquals(1, daysUntilDue(state, due))
    }

    @Test
    fun `vadesi gelmeden bilmek basamak atlatmaz`() {
        val first = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        val again = nextSchedule(first, VocabDecision.STUDIED, now + 60_000L, dayStart = today)
        assertEquals(first.box, again.box)
        assertEquals(first.dueAt, again.dueAt)
    }

    @Test
    fun `geciken tekrar cezasiz devam eder`() {
        var state = nextSchedule(schedule(), VocabDecision.STUDIED, now, dayStart = today)
        // 1. gün tekrarı on gün gecikti; kelime yine 3. gün basamağına geçiyor.
        val late = state.dueAt!! + 10 * day
        state = nextSchedule(state, VocabDecision.STUDIED, late, dayStart = late)
        assertEquals(2, state.box)
        assertEquals(2, daysUntilDue(state, late))
    }

    @Test
    fun `fsrs doneminden gelen kelime en yakin basamaga oturur`() {
        // Eski merdivende 30 günlük basamak (5. kutu) → yenide 30. gün basamağı.
        val old = schedule().copy(
            status = VocabStatus.LEARNING,
            box = 5,
            lastReviewedAt = now - 30 * day,
            dueAt = today,
            reviewCount = 5,
            stability = 31.0,
            difficulty = 5.2,
        )
        assertEquals(4, ladderStage(old))
        val next = nextSchedule(old, VocabDecision.STUDIED, now, dayStart = today)
        assertEquals(5, next.box)
        assertEquals(90, daysUntilDue(next, today))
        assertNull(next.stability)
        // Yeni satır artık yeni merdivende: kademesi bir daha çevrilmiyor.
        assertEquals(5, ladderStage(next))
    }

    @Test
    fun `ogrenme gunu ilk calismanin gunu`() {
        val postponed = nextSchedule(schedule(), VocabDecision.POSTPONE, now - 3 * day, dayStart = today - 3 * day)
        val studied = nextSchedule(postponed, VocabDecision.STUDIED, now, dayStart = today)
        assertEquals(now, studied.introducedAt)
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
    fun `gun sabah dortte basliyor`() {
        val zone = 3 * 3_600_000L
        val threeAm = startOfDay(now, zone) + 23 * 3_600_000L
        // 03:00 hâlâ dünkü gün: gece çalışmak yeni gün saymamalı.
        assertEquals(startOfDay(now, zone), startOfDay(threeAm, zone))
        val fiveAm = startOfDay(now, zone) + 25 * 3_600_000L
        assertEquals(startOfDay(now, zone) + day, startOfDay(fiveAm, zone))
    }
}
