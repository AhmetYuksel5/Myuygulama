package com.ahmety.uygulama.ui.update

/** Sürüm notunun tek bir parçası. */
sealed interface NoteBlock {
    /** İlk satır: sürümün özeti. */
    data class Heading(val text: String) : NoteBlock

    data class Paragraph(val text: String) : NoteBlock

    /** `- ` ile başlayan satır. */
    data class Bullet(val text: String) : NoteBlock
}

/**
 * Sürüm notunu okunur parçalara böler.
 *
 * Not, commit iletisinin kendisi ve commit iletileri yetmiş iki sütunda
 * elle sarılıyor. Ham hâlini basınca o sarmalar ekranda satır sonu olarak
 * çıkıyor ve metin olmaması gereken yerlerde bölünüyordu. Burada tek satır
 * sonları geri açılıyor: paragrafı boş satır ayırıyor, satır sonu değil.
 *
 * Commit'in altındaki künye satırları (yardımcı yazar, oturum bağlantısı,
 * `[skip ci]`) nota ait değil; atılıyorlar.
 */
fun formatReleaseNotes(raw: String): List<NoteBlock> {
    val blocks = mutableListOf<NoteBlock>()
    val current = StringBuilder()
    var bullet = false
    var first = true

    fun flush() {
        val text = current.toString().trim()
        current.clear()
        if (text.isEmpty()) {
            bullet = false
            return
        }
        blocks += when {
            bullet -> NoteBlock.Bullet(text)
            first -> NoteBlock.Heading(text)
            else -> NoteBlock.Paragraph(text)
        }
        first = false
        bullet = false
    }

    raw.replace("\r\n", "\n").split('\n').forEach { line ->
        val trimmed = line.trim()
        when {
            trimmed.isEmpty() -> flush()
            isTrailer(trimmed) -> flush()
            isBulletStart(trimmed) -> {
                flush()
                bullet = true
                current.append(trimmed.drop(1).trim())
            }
            else -> {
                if (current.isNotEmpty()) current.append(' ')
                current.append(trimmed)
            }
        }
    }
    flush()
    return blocks
}

private fun isBulletStart(line: String): Boolean =
    line.length > 2 && line[1] == ' ' && (line[0] == '-' || line[0] == '*' || line[0] == '•')

private fun isTrailer(line: String): Boolean =
    line.startsWith("Co-Authored-By:", ignoreCase = true) ||
        line.startsWith("Claude-Session:", ignoreCase = true) ||
        line.startsWith("Generated with", ignoreCase = true) ||
        line == "[skip ci]"
