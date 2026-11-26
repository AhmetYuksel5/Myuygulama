package com.ahmety.uygulama.feature.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProtocolTest {

    @Test
    fun extractJsonObjectStripsCodeFencesAndPreamble() {
        val raw = """
            İşte sonraki adım:
            ```json
            {"thought":"Ayarlara dokun","action":"tap","x":540,"y":1200}
            ```
        """.trimIndent()
        assertEquals(
            """{"thought":"Ayarlara dokun","action":"tap","x":540,"y":1200}""",
            AgentProtocol.extractJsonObject(raw),
        )
    }

    @Test
    fun extractJsonObjectReturnsNullWhenMissingBraces() {
        assertNull(AgentProtocol.extractJsonObject("sadece metin"))
    }

    @Test
    fun buildStepPromptIncludesGoalStateUserNotesHistoryAndSameScreenWarning() {
        val snapshot = ScreenSnapshot(
            width = 1080,
            height = 2400,
            packageName = "com.example.app",
            keyboardOpen = false,
            treeText = "#1 [540,1200] (100,1150,980,1250) click \"Ayarlar\"",
            nodeCount = 1,
            signature = "sig",
            sensitive = false,
        )
        val prompt = AgentProtocol.buildStepPrompt(
            goal = "Kayıtlı ayarı güncelle",
            step = 2,
            maxSteps = 20,
            snapshot = snapshot,
            stateSummary = "Arama sonuçlarında birden fazla eşleşme açıldı; seçim bekleniyor.",
            userNotes = listOf("İkinci seçeneği kullan"),
            history = listOf("Adım 1: tap(540,1200) → dokunuldu"),
            hasScreenshot = true,
            sameScreen = true,
        )
        assertTrue(prompt.contains("Görev: Kayıtlı ayarı güncelle"))
        assertTrue(prompt.contains("Mevcut durum özeti: Arama sonuçlarında birden fazla eşleşme açıldı"))
        assertTrue(prompt.contains("İkinci seçeneği kullan"))
        assertTrue(prompt.contains("Adım: 2/20"))
        assertTrue(prompt.contains("Ekran görüntüsü ekli"))
        assertTrue(prompt.contains("ekran değişmedi"))
        assertTrue(prompt.contains("Adım 1: tap(540,1200)"))
    }

    @Test
    fun systemPromptDocumentsGeneralAskActionAndRollingState() {
        val sys = AgentProtocol.systemPrompt()
        assertTrue(sys.contains("\"ask\""))
        assertTrue(sys.contains("\"options\""))
        assertTrue(sys.contains("\"state\""))
    }

    @Test
    fun agentActionDescribeFormatsKeyActions() {
        assertEquals("tap(540,1200)", AgentAction(action = "tap", x = 540, y = 1200).describe())
        assertEquals("scroll(down)", AgentAction(action = "scroll", direction = "down").describe())
        assertEquals(
            "open_app(com.example.app)",
            AgentAction(action = "open_app", packageName = "com.example.app").describe(),
        )
        assertEquals("back", AgentAction(action = "back").describe())
    }
}
