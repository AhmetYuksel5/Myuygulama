package com.ahmety.uygulama.core.database.importer

import com.ahmety.uygulama.core.model.TaskPriority
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray

data class ImportedSubtask(val title: String, val completed: Boolean = false)

data class ImportedTask(
    val title: String,
    val notes: String = "",
    val dueDate: Int? = null,
    val completed: Boolean = false,
    val priority: TaskPriority = TaskPriority.NONE,
    val subtasks: List<ImportedSubtask> = emptyList(),
)

data class ImportedList(val name: String, val tasks: List<ImportedTask> = emptyList())

data class ImportResult(
    val lists: List<ImportedList>,
    val format: ImportFormat,
) {
    val taskCount: Int get() = lists.sumOf { it.tasks.size }
}

enum class ImportFormat { GRAPH_BATCH, GRAPH_LISTS, GRAPH_TASKS, PLAIN_TEXT, EMPTY }

/**
 * Microsoft To Do'nun dışa aktarma aracı yok. Elimizdeki iki pratik yol var:
 * Graph Explorer'dan alınan JSON, ya da elle yapıştırılan düz metin listesi.
 * Ayrıştırıcı ikisini de kabul eder ve hangisi olduğunu kendisi anlar.
 *
 * Saf bir nesne — veritabanına dokunmaz, bu yüzden test edilebilir.
 */
object TodoImportParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parse(input: String, fallbackListName: String = "İçe aktarılan"): ImportResult {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return ImportResult(emptyList(), ImportFormat.EMPTY)

        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            runCatching { parseJson(trimmed, fallbackListName) }
                .getOrNull()
                ?.let { return it }
            // JSON gibi görünüp ayrıştırılamıyorsa düz metne düşüyoruz;
            // yapıştırma sırasında kırpılmış bir çıktı da olsa bir şey kurtarılsın.
        }
        return parsePlainText(trimmed, fallbackListName)
    }

    // --- Graph JSON ---

    private fun parseJson(input: String, fallbackListName: String): ImportResult? {
        val root = json.parseToJsonElement(input)

        // $batch yanıtı: tek istekte tüm listelerin görevleri.
        if (root is JsonObject && root["responses"] is JsonArray) {
            return parseBatch(root["responses"]!!.jsonArray)
        }

        val items: List<JsonElement> = when {
            root is JsonArray -> root
            root is JsonObject && root["value"] is JsonArray -> root["value"]!!.jsonArray
            root is JsonObject -> listOf(root)
            else -> return null
        }
        if (items.isEmpty()) return ImportResult(emptyList(), ImportFormat.EMPTY)

        val objects = items.filterIsInstance<JsonObject>()
        if (objects.isEmpty()) return null

        val looksLikeTasks = objects.any { it["title"] != null }
        val looksLikeLists = objects.any { it["displayName"] != null && it["title"] == null }

        return when {
            looksLikeTasks -> ImportResult(
                lists = listOf(ImportedList(fallbackListName, objects.mapNotNull(::parseGraphTask))),
                format = ImportFormat.GRAPH_TASKS,
            )

            looksLikeLists -> ImportResult(
                lists = objects.mapNotNull { obj ->
                    val name = obj["displayName"].asStringOrNull()?.trim()
                    if (name.isNullOrEmpty()) null else ImportedList(name)
                },
                format = ImportFormat.GRAPH_LISTS,
            )

            else -> null
        }
    }

    /**
     * Toplu istek yanıtı. Liste adını `id` alanından okuyoruz — toplu istekte
     * her alt isteğe istediğimiz kimliği verebildiğimiz için, oraya liste adını
     * yazıyoruz ve görevlerin hangi listeye ait olduğu kaybolmuyor.
     */
    private fun parseBatch(responses: JsonArray): ImportResult {
        val lists = responses.filterIsInstance<JsonObject>().mapNotNull { response ->
            val status = response["status"].asStringOrNull()?.toIntOrNull() ?: 200
            if (status !in 200..299) return@mapNotNull null

            val name = response["id"].asStringOrNull()?.trim()
            if (name.isNullOrEmpty()) return@mapNotNull null

            val values = (response["body"] as? JsonObject)?.get("value") as? JsonArray
                ?: return@mapNotNull null

            ImportedList(
                name = name,
                tasks = values.filterIsInstance<JsonObject>().mapNotNull(::parseGraphTask),
            )
        }
        return ImportResult(lists, ImportFormat.GRAPH_BATCH)
    }

    private fun parseGraphTask(obj: JsonObject): ImportedTask? {
        val title = obj["title"].asStringOrNull()?.trim().orEmpty()
        if (title.isEmpty()) return null

        val status = obj["status"].asStringOrNull()
        val completed = status == "completed" || obj["completedDateTime"] is JsonObject

        val notes = (obj["body"] as? JsonObject)?.get("content").asStringOrNull()
            ?.let(::stripHtml)
            ?.trim()
            .orEmpty()

        val due = (obj["dueDateTime"] as? JsonObject)?.get("dateTime").asStringOrNull()
            ?.let(::parseIsoDateToEpochDay)

        val priority = when (obj["importance"].asStringOrNull()) {
            "high" -> TaskPriority.HIGH
            "low" -> TaskPriority.LOW
            "normal" -> TaskPriority.NORMAL
            else -> TaskPriority.NONE
        }

        val subtasks = (obj["checklistItems"] as? JsonArray)
            ?.filterIsInstance<JsonObject>()
            ?.mapNotNull { item ->
                val name = item["displayName"].asStringOrNull()?.trim()
                if (name.isNullOrEmpty()) {
                    null
                } else {
                    ImportedSubtask(name, item["isChecked"].asStringOrNull() == "true")
                }
            }
            .orEmpty()

        return ImportedTask(
            title = title,
            notes = notes,
            dueDate = due,
            completed = completed,
            priority = priority,
            subtasks = subtasks,
        )
    }

    // --- Düz metin ---

    /**
     * Kabul edilen biçim:
     * ```
     * # Alışveriş            → liste başlığı
     * - [ ] Süt al           → görev
     * - [x] Ekmek al         → tamamlanmış görev
     *     - Tam buğday       → alt görev (girinti)
     * ```
     * Başındaki `-`, `*`, `[ ]` işaretleri isteğe bağlı; sade bir satır listesi de çalışır.
     */
    private fun parsePlainText(input: String, fallbackListName: String): ImportResult {
        val lists = mutableListOf<ImportedList>()
        var currentName = fallbackListName
        var currentTasks = mutableListOf<ImportedTask>()

        fun flush() {
            if (currentTasks.isNotEmpty() || lists.isEmpty()) {
                lists += ImportedList(currentName, currentTasks.toList())
            }
            currentTasks = mutableListOf()
        }

        input.lines().forEach { rawLine ->
            val line = rawLine.trimEnd()
            if (line.isBlank()) return@forEach

            val indent = line.takeWhile { it == ' ' || it == '\t' }.length
            val content = line.trim()

            if (content.startsWith("#")) {
                flush()
                currentName = content.trimStart('#').trim().ifEmpty { fallbackListName }
                return@forEach
            }

            val (completed, text) = stripTaskMarkers(content)
            if (text.isEmpty()) return@forEach

            // Girintili satır, bir önceki görevin alt görevi.
            if (indent >= 2 && currentTasks.isNotEmpty()) {
                val last = currentTasks.removeAt(currentTasks.lastIndex)
                currentTasks += last.copy(
                    subtasks = last.subtasks + ImportedSubtask(text, completed),
                )
            } else {
                currentTasks += ImportedTask(title = text, completed = completed)
            }
        }
        flush()

        return ImportResult(
            lists = lists.filter { it.tasks.isNotEmpty() },
            format = ImportFormat.PLAIN_TEXT,
        )
    }

    private fun stripTaskMarkers(line: String): Pair<Boolean, String> {
        var text = line.removePrefix("-").removePrefix("*").removePrefix("•").trim()
        var completed = false
        when {
            text.startsWith("[x]", ignoreCase = true) -> {
                completed = true
                text = text.removeRange(0, 3).trim()
            }

            text.startsWith("[ ]") -> text = text.removeRange(0, 3).trim()
        }
        return completed to text
    }

    // --- Yardımcılar ---

    internal fun parseIsoDateToEpochDay(value: String): Int? {
        val datePart = value.take(10)
        return runCatching { LocalDate.parse(datePart).toEpochDays() }.getOrNull()
    }

    private fun stripHtml(value: String): String =
        value.replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
            .replace(Regex("<[^>]+>"), "")
            .replace("&nbsp;", " ")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")

    private fun JsonElement?.asStringOrNull(): String? =
        (this as? JsonPrimitive)?.takeIf { it.isString || it.content != "null" }?.content
}
