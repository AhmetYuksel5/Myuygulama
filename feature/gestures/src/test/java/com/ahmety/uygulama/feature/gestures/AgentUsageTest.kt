package com.ahmety.uygulama.feature.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentUsageTest {

    @Test
    fun `dusunme seviyesi yalniz tanıyan modellere gidiyor`() {
        assertTrue(AgentUsage.supportsEffort("claude-haiku-5-5"))
        assertTrue(AgentUsage.supportsEffort("claude-opus-5-5"))
        assertTrue(AgentUsage.supportsEffort("claude-sonnet-4-6"))
        assertFalse(AgentUsage.supportsEffort("claude-haiku-4-5"))
        assertFalse(AgentUsage.supportsEffort("gemini-3.8-flash"))
    }

    @Test
    fun `model etiketi`() {
        assertEquals("Haiku", AgentUsage.label("claude-haiku-5-5"))
        assertEquals("Opus", AgentUsage.label("claude-opus-5-5"))
        assertEquals("Gemini", AgentUsage.label("gemini-3.8-flash"))
    }

    @Test
    fun `kart ozeti`() {
        val session = AgentSession(goal = "deneme", provider = "claude", alwaysScreenshot = false)
        session.modelSteps["Haiku"] = 9
        session.modelSteps["Opus"] = 2
        session.costUsd = 0.0512
        assertEquals("Haiku 9 adım · Opus 2 adım · ~0,051 $", AgentUsage.summary(session))
    }
}

class BankLockTest {

    @Test
    fun `para cikaran dugmeler kilitli`() {
        listOf("Onayla", "GÖNDER", "Ödemeyi Tamamla", "Öde", "Satın Al", "İşlemi Onayla", "Para Gönder", "", null)
            .forEach { assertTrue("$it", BankAccess.isMoneyButton(it)) }
    }

    @Test
    fun `gezinme dugmeleri serbest`() {
        listOf("Hesaplarım", "Hesap Hareketleri", "Ödemeler", "Kartlarım", "Dekont", "Geri", "Ara")
            .forEach { assertFalse("$it", BankAccess.isMoneyButton(it)) }
    }
}
