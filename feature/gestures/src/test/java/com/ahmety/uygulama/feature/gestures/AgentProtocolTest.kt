package com.ahmety.uygulama.feature.gestures

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        val sysWithoutBridges = AgentProtocol.systemPrompt(driveEnabled = false, personalGmailEnabled = false)
        assertTrue(sysWithoutBridges.contains("\"ask\""))
        assertTrue(sysWithoutBridges.contains("\"options\""))
        assertTrue(sysWithoutBridges.contains("\"state\""))
        assertTrue(sysWithoutBridges.contains("remember"))
        assertFalse(sysWithoutBridges.contains("drive_search"))
        assertFalse(sysWithoutBridges.contains("gmail_search"))

        val sysWithWorkOnly = AgentProtocol.systemPrompt(driveEnabled = true, personalGmailEnabled = false)
        assertTrue(sysWithWorkOnly.contains("drive_search"))
        assertTrue(sysWithWorkOnly.contains("drive_read"))
        assertTrue(sysWithWorkOnly.contains("open_url"))
        assertTrue(sysWithWorkOnly.contains("gmail_search"))
        assertTrue(sysWithWorkOnly.contains("gmail_read"))
        assertTrue(sysWithWorkOnly.contains("gmail_draft"))
        assertTrue(sysWithWorkOnly.contains("yesiltemizlik@gmail.com"))
        assertFalse(sysWithWorkOnly.contains("ahmetyuksel5@gmail.com"))

        val sysWithBothAccounts = AgentProtocol.systemPrompt(driveEnabled = true, personalGmailEnabled = true)
        assertTrue(sysWithBothAccounts.contains("yesiltemizlik@gmail.com"))
        assertTrue(sysWithBothAccounts.contains("ahmetyuksel5@gmail.com"))
        assertTrue(sysWithBothAccounts.contains("asla doğrudan gönderme"))

        val sysPersonalOnly = AgentProtocol.systemPrompt(driveEnabled = false, personalGmailEnabled = true)
        assertFalse(sysPersonalOnly.contains("drive_search"))
        assertTrue(sysPersonalOnly.contains("gmail_search"))
        assertTrue(sysPersonalOnly.contains("ahmetyuksel5@gmail.com"))
        assertFalse(sysPersonalOnly.contains("yesiltemizlik@gmail.com"))
    }

    @Test
    fun buildStepPromptIncludesMemoryAndToolOutputWhenPresent() {
        val snapshot = ScreenSnapshot(
            width = 1080,
            height = 2400,
            packageName = "com.example.app",
            keyboardOpen = false,
            treeText = "#1 [540,1200] (0,0,1080,2400) click \"Tamam\"",
            nodeCount = 1,
            signature = "sig2",
            sensitive = false,
        )
        val prompt = AgentProtocol.buildStepPrompt(
            goal = "Faturayı bul",
            step = 1,
            maxSteps = 20,
            snapshot = snapshot,
            memoryText = "- Drive dosyalarını önce drive_search ile ara.",
            toolOutput = "1. Fatura.pdf (PDF) | id=abc123 | url=https://drive.google.com/file/d/abc123/view",
        )
        assertTrue(prompt.contains("Hafıza / Merdiven metotları:"))
        assertTrue(prompt.contains("drive_search ile ara"))
        assertTrue(prompt.contains("Arka plan araç sonucu:"))
        assertTrue(prompt.contains("Fatura.pdf"))
    }

    @Test
    fun agentActionDescribeFormatsKeyActions() {
        assertEquals("tap(540,1200)", AgentAction(action = "tap", x = 540, y = 1200).describe())
        assertEquals("scroll(down)", AgentAction(action = "scroll", direction = "down").describe())
        assertEquals(
            "open_app(com.example.app)",
            AgentAction(action = "open_app", packageName = "com.example.app").describe(),
        )
        assertEquals(
            "drive_search(\"teklif\")",
            AgentAction(action = "drive_search", query = "teklif").describe(),
        )
        assertEquals(
            "drive_read(abc123)",
            AgentAction(action = "drive_read", fileId = "abc123").describe(),
        )
        assertEquals(
            "gmail_search(is, \"fatura\")",
            AgentAction(action = "gmail_search", query = "fatura").describe(),
        )
        assertEquals(
            "gmail_read(kisisel, th42)",
            AgentAction(action = "gmail_read", account = "kisisel", threadId = "th42").describe(),
        )
        assertEquals(
            "gmail_draft(is, \"ali@ornek.com\")",
            AgentAction(action = "gmail_draft", account = "is", to = "ali@ornek.com").describe(),
        )
        assertEquals(
            "open_url(https://drive.google.com/file/d/1/view)",
            AgentAction(action = "open_url", url = "https://drive.google.com/file/d/1/view").describe(),
        )
        assertEquals("back", AgentAction(action = "back").describe())
    }

    @Test
    fun personalAccountRoutingRecognizesAliasesAndDefaultsToWork() {
        assertTrue(ScreenAgentStorage.isPersonalAccount("kisisel"))
        assertTrue(ScreenAgentStorage.isPersonalAccount("kişisel"))
        assertTrue(ScreenAgentStorage.isPersonalAccount("KİŞİSEL"))
        assertTrue(ScreenAgentStorage.isPersonalAccount("personal"))
        assertTrue(ScreenAgentStorage.isPersonalAccount("ahmetyuksel5@gmail.com"))

        assertFalse(ScreenAgentStorage.isPersonalAccount(""))
        assertFalse(ScreenAgentStorage.isPersonalAccount("is"))
        assertFalse(ScreenAgentStorage.isPersonalAccount("iş"))
        assertFalse(ScreenAgentStorage.isPersonalAccount("yesiltemizlik@gmail.com"))
    }
}
