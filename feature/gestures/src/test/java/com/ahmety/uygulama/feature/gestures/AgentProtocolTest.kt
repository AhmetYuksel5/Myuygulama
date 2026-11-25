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
    fun buildStepPromptIncludesGoalHistoryAndSameScreenWarning() {
        val snapshot = ScreenSnapshot(
            width = 1080,
            height = 2400,
            packageName = "com.google.android.apps.chromecast.app",
            keyboardOpen = false,
            treeText = "#1 [540,1200] (100,1150,980,1250) click \"Ayarlar\"",
            nodeCount = 1,
            signature = "sig",
            sensitive = false,
        )
        val prompt = AgentProtocol.buildStepPrompt(
            goal = "Ev üyesi davet et",
            step = 2,
            maxSteps = 25,
            snapshot = snapshot,
            history = listOf("Adım 1: tap(540,1200) → dokunuldu"),
            hasScreenshot = true,
            sameScreen = true,
        )
        assertTrue(prompt.contains("Görev: Ev üyesi davet et"))
        assertTrue(prompt.contains("Adım: 2/25"))
        assertTrue(prompt.contains("Ekran görüntüsü ekli"))
        assertTrue(prompt.contains("ekran değişmedi"))
        assertTrue(prompt.contains("Adım 1: tap(540,1200)"))
    }

    @Test
    fun agentActionDescribeFormatsKeyActions() {
        assertEquals("tap(540,1200)", AgentAction(action = "tap", x = 540, y = 1200).describe())
        assertEquals("scroll(down)", AgentAction(action = "scroll", direction = "down").describe())
        assertEquals(
            "open_app(com.google.android.apps.chromecast.app)",
            AgentAction(action = "open_app", packageName = "com.google.android.apps.chromecast.app").describe(),
        )
        assertEquals("back", AgentAction(action = "back").describe())
    }
}
