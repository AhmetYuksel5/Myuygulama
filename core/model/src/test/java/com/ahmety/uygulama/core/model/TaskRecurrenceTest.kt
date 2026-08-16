package com.ahmety.uygulama.core.model

import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class TaskRecurrenceTest {

    private fun day(iso: String): Int = LocalDate.parse(iso).toEpochDays()

    @Test
    fun `gunluk tekrar planlanan tarihten sayar`() {
        val rule = RecurrenceRule(RecurrenceUnit.DAY, interval = 3)
        // Görev 10 Ağustos'ta planlanmış, 14'ünde geç tamamlanmış:
        // sonraki tarih planlanandan sayılır → 13 Ağustos.
        val next = TaskRecurrence.nextDueDate(
            rule = rule,
            currentDue = day("2026-08-10"),
            completedOn = day("2026-08-14"),
        )
        assertEquals(day("2026-08-13"), next)
    }

    @Test
    fun `tamamlanmadan sayan kural bugunden baslar`() {
        val rule = RecurrenceRule(RecurrenceUnit.DAY, interval = 3, fromCompletion = true)
        val next = TaskRecurrence.nextDueDate(
            rule = rule,
            currentDue = day("2026-08-10"),
            completedOn = day("2026-08-14"),
        )
        assertEquals(day("2026-08-17"), next)
    }

    @Test
    fun `tarihsiz gorevde sayac tamamlanma gununden baslar`() {
        val rule = RecurrenceRule(RecurrenceUnit.DAY, interval = 1)
        val next = TaskRecurrence.nextDueDate(rule, currentDue = null, completedOn = day("2026-08-14"))
        assertEquals(day("2026-08-15"), next)
    }

    @Test
    fun `haftalik tekrar ayni hafta icindeki sonraki gune gider`() {
        // Pazartesi + Perşembe
        val rule = RecurrenceRule(
            RecurrenceUnit.WEEK,
            interval = 1,
            daysMask = (1 shl 0) or (1 shl 3),
        )
        // 2026-08-17 Pazartesi → sıradaki 2026-08-20 Perşembe
        val next = TaskRecurrence.nextDueDate(rule, day("2026-08-17"), day("2026-08-17"))
        assertEquals(day("2026-08-20"), next)
    }

    @Test
    fun `haftalik tekrar haftayi devrederken basa doner`() {
        val rule = RecurrenceRule(
            RecurrenceUnit.WEEK,
            interval = 1,
            daysMask = (1 shl 0) or (1 shl 3),
        )
        // Perşembeden sonra sıradaki seçili gün gelecek haftanın Pazartesisi
        val next = TaskRecurrence.nextDueDate(rule, day("2026-08-20"), day("2026-08-20"))
        assertEquals(day("2026-08-24"), next)
    }

    @Test
    fun `iki haftada bir kuralinda hafta devredince fazladan hafta atlanir`() {
        val rule = RecurrenceRule(RecurrenceUnit.WEEK, interval = 2, daysMask = 1 shl 0)
        // Pazartesiden sonraki Pazartesi + 1 hafta = iki hafta sonra
        val next = TaskRecurrence.nextDueDate(rule, day("2026-08-17"), day("2026-08-17"))
        assertEquals(day("2026-08-31"), next)
    }

    @Test
    fun `gun secilmemis haftalik kural yedi gun ekler`() {
        val rule = RecurrenceRule(RecurrenceUnit.WEEK, interval = 1, daysMask = 0)
        val next = TaskRecurrence.nextDueDate(rule, day("2026-08-17"), day("2026-08-17"))
        assertEquals(day("2026-08-24"), next)
    }

    @Test
    fun `aylik tekrar ayin gununu korur`() {
        val rule = RecurrenceRule(RecurrenceUnit.MONTH, interval = 1)
        val next = TaskRecurrence.nextDueDate(rule, day("2026-08-15"), day("2026-08-15"))
        assertEquals(day("2026-09-15"), next)
    }

    @Test
    fun `aylik tekrar kisa ayda son gune kirpilir`() {
        val rule = RecurrenceRule(RecurrenceUnit.MONTH, interval = 1)
        // 31 Ocak + 1 ay → 28 Şubat (2027 artık yıl değil)
        val next = TaskRecurrence.nextDueDate(rule, day("2027-01-31"), day("2027-01-31"))
        assertEquals(day("2027-02-28"), next)
    }

    @Test
    fun `yillik tekrar bir yil ekler`() {
        val rule = RecurrenceRule(RecurrenceUnit.YEAR, interval = 1)
        val next = TaskRecurrence.nextDueDate(rule, day("2026-08-15"), day("2026-08-15"))
        assertEquals(day("2027-08-15"), next)
    }
}
