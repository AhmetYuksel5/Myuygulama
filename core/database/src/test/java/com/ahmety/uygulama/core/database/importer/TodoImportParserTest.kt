package com.ahmety.uygulama.core.database.importer

import com.ahmety.uygulama.core.model.TaskPriority
import kotlinx.datetime.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TodoImportParserTest {

    private fun day(iso: String): Int = LocalDate.parse(iso).toEpochDays()

    @Test
    fun `graph liste ciktisi listeleri tanir`() {
        val input = """
            {
              "@odata.context": "https://graph.microsoft.com/v1.0/...",
              "value": [
                { "id": "AAA", "displayName": "Tasks", "wellknownListName": "defaultList" },
                { "id": "BBB", "displayName": "Alışveriş", "wellknownListName": "none" }
              ]
            }
        """.trimIndent()

        val result = TodoImportParser.parse(input)

        assertEquals(ImportFormat.GRAPH_LISTS, result.format)
        assertEquals(listOf("Tasks", "Alışveriş"), result.lists.map { it.name })
        assertEquals(0, result.taskCount)
    }

    @Test
    fun `graph gorev ciktisi baslik tarih oncelik ve alt gorevleri alir`() {
        val input = """
            {
              "value": [
                {
                  "id": "T1",
                  "title": "Fatura öde",
                  "status": "notStarted",
                  "importance": "high",
                  "body": { "content": "<p>Elektrik <b>ve</b> su</p>", "contentType": "html" },
                  "dueDateTime": { "dateTime": "2026-08-20T00:00:00.0000000", "timeZone": "UTC" },
                  "checklistItems": [
                    { "displayName": "Elektrik", "isChecked": true },
                    { "displayName": "Su", "isChecked": false }
                  ]
                },
                {
                  "id": "T2",
                  "title": "Rapor gönder",
                  "status": "completed",
                  "importance": "normal"
                }
              ]
            }
        """.trimIndent()

        val result = TodoImportParser.parse(input, fallbackListName = "İş")

        assertEquals(ImportFormat.GRAPH_TASKS, result.format)
        assertEquals(1, result.lists.size)
        assertEquals("İş", result.lists[0].name)

        val first = result.lists[0].tasks[0]
        assertEquals("Fatura öde", first.title)
        assertEquals(TaskPriority.HIGH, first.priority)
        assertEquals(day("2026-08-20"), first.dueDate)
        assertEquals("Elektrik ve su", first.notes)
        assertEquals(2, first.subtasks.size)
        assertTrue(first.subtasks[0].completed)
        assertEquals(false, first.subtasks[1].completed)

        val second = result.lists[0].tasks[1]
        assertTrue(second.completed)
        assertEquals(TaskPriority.NORMAL, second.priority)
    }

    @Test
    fun `graph gorev kimligi mukerrer aktarim icin saklanir`() {
        // Binlerce görevlik liste sayfa sayfa aktarılırken sayfalar üst üste
        // binebiliyor; kimlik olmadan aynı görev birkaç kez eklenirdi.
        val input = """{ "value": [ { "id": "AAMkAD123", "title": "Süt al" } ] }"""
        val result = TodoImportParser.parse(input)
        assertEquals("AAMkAD123", result.lists[0].tasks[0].externalId)
    }

    @Test
    fun `toplu graph yanitinda liste adlari istek kimliginden gelir`() {
        val input = """
            {
              "responses": [
                {
                  "id": "Alışveriş",
                  "status": 200,
                  "body": {
                    "value": [
                      { "title": "Süt al", "status": "notStarted" },
                      { "title": "Ekmek al", "status": "completed" }
                    ]
                  }
                },
                {
                  "id": "İş",
                  "status": 200,
                  "body": { "value": [ { "title": "Rapor yaz", "status": "notStarted" } ] }
                },
                {
                  "id": "Erişilemeyen",
                  "status": 403,
                  "body": { "error": { "code": "Forbidden" } }
                }
              ]
            }
        """.trimIndent()

        val result = TodoImportParser.parse(input)

        assertEquals(ImportFormat.GRAPH_BATCH, result.format)
        // Hatalı yanıt atlanır; kalan iki liste adıyla birlikte gelir.
        assertEquals(listOf("Alışveriş", "İş"), result.lists.map { it.name })
        assertEquals(3, result.taskCount)
        assertTrue(result.lists[0].tasks[1].completed)
    }

    @Test
    fun `toplu yanitta bos liste de olusturulur`() {
        val input = """
            { "responses": [ { "id": "Boş liste", "status": 200, "body": { "value": [] } } ] }
        """.trimIndent()

        val result = TodoImportParser.parse(input)

        // Liste boş olsa da oluşturulmalı; yapı korunuyor.
        assertEquals(listOf("Boş liste"), result.lists.map { it.name })
        assertEquals(0, result.taskCount)
    }

    @Test
    fun `basliksiz gorevler atlanir`() {
        val input = """{ "value": [ { "id": "X", "title": "   " }, { "id": "Y", "title": "Gerçek" } ] }"""
        val result = TodoImportParser.parse(input)
        assertEquals(listOf("Gerçek"), result.lists[0].tasks.map { it.title })
    }

    @Test
    fun `duz metin liste basligi gorev ve alt gorevleri ayirir`() {
        val input = """
            # Alışveriş
            - [ ] Süt al
            - [x] Ekmek al
                - Tam buğday
            # İş
            Rapor yaz
        """.trimIndent()

        val result = TodoImportParser.parse(input)

        assertEquals(ImportFormat.PLAIN_TEXT, result.format)
        assertEquals(listOf("Alışveriş", "İş"), result.lists.map { it.name })

        val alisveris = result.lists[0]
        assertEquals(2, alisveris.tasks.size)
        assertEquals("Süt al", alisveris.tasks[0].title)
        assertTrue(alisveris.tasks[1].completed)
        assertEquals(listOf("Tam buğday"), alisveris.tasks[1].subtasks.map { it.title })

        assertEquals("Rapor yaz", result.lists[1].tasks[0].title)
    }

    @Test
    fun `basliksiz duz metin varsayilan listeye gider`() {
        val result = TodoImportParser.parse("Süt al\nEkmek al", fallbackListName = "Yapılacaklar")
        assertEquals(1, result.lists.size)
        assertEquals("Yapılacaklar", result.lists[0].name)
        assertEquals(2, result.lists[0].tasks.size)
    }

    @Test
    fun `bos girdi bos sonuc doner`() {
        val result = TodoImportParser.parse("   \n  ")
        assertEquals(ImportFormat.EMPTY, result.format)
        assertEquals(0, result.taskCount)
    }

    @Test
    fun `bozuk json duz metne dusulerek kurtarilir`() {
        // Yapıştırırken kırpılmış bir çıktı: JSON gibi başlıyor ama kapanmıyor.
        val result = TodoImportParser.parse("{ bozuk\nSüt al")
        assertEquals(ImportFormat.PLAIN_TEXT, result.format)
        assertTrue(result.taskCount > 0)
    }

    @Test
    fun `tarih ayristirma sadece gun kismini alir`() {
        assertEquals(
            day("2026-12-31"),
            TodoImportParser.parseIsoDateToEpochDay("2026-12-31T23:59:00.0000000"),
        )
        assertEquals(null, TodoImportParser.parseIsoDateToEpochDay("bozuk"))
    }
}
