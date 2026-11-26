package com.ahmety.uygulama.feature.gestures

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class AgentSchedulerTest {

    private fun at(millis: Long) = LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())

    @Test
    fun `tekrar kurali`() {
        assertEquals("none", AgentScheduler.normalizeRepeat(""))
        assertEquals("daily", AgentScheduler.normalizeRepeat("daily"))
        assertEquals("weekdays", AgentScheduler.normalizeRepeat("weekdays"))
        assertEquals("mon,fri", AgentScheduler.normalizeRepeat("mon, fri"))
        assertEquals("mon,wed", AgentScheduler.normalizeRepeat("pzt,çar"))
    }

    @Test
    fun `her gun bir sonraki gune kayar`() {
        // Perşembe 14:00'te "her gün 08:30" → cuma 08:30.
        val after = LocalDateTime.of(2026, 11, 26, 14, 0)
        val next = at(AgentScheduler.nextAfter(LocalTime.of(8, 30), "daily", after))
        assertEquals(LocalDateTime.of(2026, 11, 27, 8, 30), next)
    }

    @Test
    fun `hafta ici cumartesiyi atlar`() {
        // Cuma 09:00'dan sonra hafta içi 08:30 → pazartesi.
        val after = LocalDateTime.of(2026, 11, 27, 9, 0)
        val next = at(AgentScheduler.nextAfter(LocalTime.of(8, 30), "weekdays", after))
        assertEquals(LocalDateTime.of(2026, 11, 30, 8, 30), next)
    }
}
