package com.ahmety.uygulama.core.model

/**
 * İşaretleme rengi. Renkler kullanıcının kendi anlamlarını yüklediği
 * etiketler: mavi "bilmediğim kelime" demek ve kelime çalışma modülüne
 * bu renk aktarılıyor.
 */
enum class HighlightColor(val label: String) {
    YELLOW("Sarı"),
    BLUE("Mavi — bilmediğim kelime"),
    GREEN("Yeşil"),
    RED("Kırmızı"),
}

/**
 * İşaretlemenin nereden geldiğini ve rengini kaydın serbest metin alanında
 * (`Entry.source`) taşıyoruz; böylece veritabanı şeması değişmiyor.
 *
 * Biçim: `book:12;color=BLUE` veya `article:34;color=YELLOW`
 */
object HighlightRef {

    const val KIND_BOOK = "book"
    const val KIND_ARTICLE = "article"

    /** Başka bir uygulamada seçilip buraya gönderilen metin. */
    const val KIND_SELECTION = "selection"

    fun encode(kind: String, sourceId: Long, color: HighlightColor): String =
        "$kind:$sourceId;color=${color.name}"

    fun kind(source: String?): String? =
        source?.substringBefore(':')?.takeIf { it.isNotBlank() }

    fun sourceId(source: String?): Long? =
        source?.substringAfter(':', "")?.substringBefore(';')?.toLongOrNull()

    fun color(source: String?): HighlightColor? {
        val raw = source?.substringAfter("color=", "")?.takeIf { it.isNotBlank() } ?: return null
        return runCatching { HighlightColor.valueOf(raw) }.getOrNull()
    }
}
