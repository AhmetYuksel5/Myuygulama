package com.ahmety.uygulama.feature.library

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Not biçimi: Keep benzeri renk, sabitleme, işaretlenebilir liste ve fotoğraf —
 * hepsi **veritabanı şeması değiştirilmeden**.
 *
 * - Renk ve sabitleme, kaydın serbest metin alanı `source` içinde `k=v;k=v`
 *   olarak duruyor (`color=3;pin=1`).
 * - Liste maddeleri ve fotoğraflar gövde metninin satırlarında:
 *   `[ ] madde`, `[x] madde`, `img=/veri/yolu.jpg`.
 *
 * Böylece göç (migration) riski sıfır; eski notlar olduğu gibi çalışmaya
 * devam ediyor ve senkron değişiklik günlüğü de kendiliğinden taşıyor.
 */
data class NoteStyle(
    val colorIndex: Int = 0,
    val pinned: Boolean = false,
) {
    fun encode(): String = "color=$colorIndex;pin=${if (pinned) 1 else 0}"

    companion object {
        fun decode(source: String?): NoteStyle {
            if (source.isNullOrBlank()) return NoteStyle()
            var color = 0
            var pinned = false
            source.split(';').forEach { part ->
                val pieces = part.split('=', limit = 2)
                if (pieces.size != 2) return@forEach
                when (pieces[0].trim()) {
                    "color" -> color = pieces[1].trim().toIntOrNull() ?: 0
                    "pin" -> pinned = pieces[1].trim() == "1"
                }
            }
            return NoteStyle(colorIndex = color.coerceIn(0, NOTE_COLOR_COUNT - 1), pinned = pinned)
        }
    }
}

const val NOTE_COLOR_COUNT = 8

/** Açık temada pastel, koyu temada derin tonlar — ikisinde de okunur. */
private val lightNoteColors = listOf(
    Color(0xFFF1EFF7), // varsayılan
    Color(0xFFFFF3C4), // sarı
    Color(0xFFFFE0E3), // pembe
    Color(0xFFDFF3E4), // yeşil
    Color(0xFFD9EDFB), // mavi
    Color(0xFFEADDF7), // mor
    Color(0xFFFFE3CC), // turuncu
    Color(0xFFDCF0EF), // deniz
)

private val darkNoteColors = listOf(
    Color(0xFF26252B), // varsayılan
    Color(0xFF4A4326), // sarı
    Color(0xFF4C2E33), // pembe
    Color(0xFF27402F), // yeşil
    Color(0xFF23394A), // mavi
    Color(0xFF382B47), // mor
    Color(0xFF4A3524), // turuncu
    Color(0xFF23403E), // deniz
)

@Composable
fun noteColor(index: Int): Color {
    val palette = if (isSystemInDarkTheme()) darkNoteColors else lightNoteColors
    return palette.getOrElse(index) { palette[0] }
}

@Composable
fun noteTextColor(index: Int): Color =
    if (index == 0) {
        MaterialTheme.colorScheme.onSurface
    } else if (isSystemInDarkTheme()) {
        Color(0xFFE8E5EC)
    } else {
        Color(0xFF1D1B20)
    }

/** Gövdedeki tek bir liste maddesi. */
data class ChecklistItem(val text: String, val checked: Boolean)

private const val IMAGE_PREFIX = "img="
private const val UNCHECKED = "[ ] "
private const val CHECKED = "[x] "

/** Gövde en az bir liste maddesi içeriyor mu. */
fun hasChecklist(body: String): Boolean =
    body.lineSequence().any { it.startsWith(UNCHECKED) || it.startsWith(CHECKED) }

fun parseChecklist(body: String): List<ChecklistItem> =
    body.lineSequence().mapNotNull { line ->
        when {
            line.startsWith(CHECKED) -> ChecklistItem(line.removePrefix(CHECKED), true)
            line.startsWith(UNCHECKED) -> ChecklistItem(line.removePrefix(UNCHECKED), false)
            else -> null
        }
    }.toList()

fun parseImagePaths(body: String): List<String> =
    body.lineSequence()
        .filter { it.startsWith(IMAGE_PREFIX) }
        .map { it.removePrefix(IMAGE_PREFIX) }
        .filter { it.isNotBlank() }
        .toList()

/** Liste ve fotoğraf satırları dışındaki düz metin. */
fun plainBody(body: String): String =
    body.lineSequence()
        .filterNot {
            it.startsWith(UNCHECKED) || it.startsWith(CHECKED) || it.startsWith(IMAGE_PREFIX)
        }
        .joinToString("\n")
        .trim()

fun encodeChecklistItem(item: ChecklistItem): String =
    (if (item.checked) CHECKED else UNCHECKED) + item.text

fun encodeImageLine(path: String): String = IMAGE_PREFIX + path

/**
 * Gövdedeki [index]. liste maddesinin işaretini çevirir; diğer satırlara
 * (düz metin, fotoğraf) dokunmaz, sıralarını bozmaz.
 */
fun toggleChecklistItem(body: String, index: Int): String {
    var seen = -1
    return body.lines().joinToString("\n") { line ->
        val isItem = line.startsWith(UNCHECKED) || line.startsWith(CHECKED)
        if (!isItem) return@joinToString line
        seen++
        if (seen != index) return@joinToString line
        if (line.startsWith(CHECKED)) {
            UNCHECKED + line.removePrefix(CHECKED)
        } else {
            CHECKED + line.removePrefix(UNCHECKED)
        }
    }
}
