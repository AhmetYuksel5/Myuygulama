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
 * Bir maddenin kalemi: tek kelime mavi, boşluk içeren her şey kırmızı.
 *
 * Kart ikisine başka türlü davranıyor — kelimeye sözlük maddesi, ifadeye
 * bağlam, sade İngilizce, çeviri ve içindeki kalıplar. Kural tek yerde
 * duruyor çünkü aynı ayrım üç ayrı kapıdan giriyor: yüklenen liste, başka
 * uygulamadan paylaşılan metin ve kitapta elle işaretleme.
 */
fun penFor(text: String): HighlightColor =
    if (text.trim().any { it.isWhitespace() }) HighlightColor.RED else HighlightColor.BLUE

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

    /** Film altyazısından çıkarılan kelime. */
    const val KIND_SUBTITLE = "subtitle"

    /** Dışarıdan yüklenen bir listeden gelen kelime ya da ifade. */
    const val KIND_LIST = "list"

    /**
     * Filmler de kitaplarla aynı kayıt türünü kullanıyor (kelimeyi kaynağına
     * göre süzebilmek için); kitaplıkta görünmesinler diye kaydın serbest
     * metin alanına bu işaret konuyor.
     */
    const val SUBTITLE_SOURCE_MARKER = "film"

    /**
     * Bu kayıt bir film mi — `Entry.source` alanına bakarak.
     *
     * Eski kayıtlarda yalnız "film" yazıyor, yenilerde metnin yolu da
     * ekli ("film:/…/film_1.json").
     */
    fun isFilmDocument(source: String?): Boolean {
        val value = source ?: return false
        return value == SUBTITLE_SOURCE_MARKER ||
            value.startsWith("$SUBTITLE_SOURCE_MARKER:")
    }

    /**
     * Yüklenen kelime listesinin kaydında duran işaret.
     *
     * Listeler de kitaplarla aynı kayıt türünü kullanıyor (kelimeyi kaynağına
     * göre süzebilmek için) ama okunacak bir metinleri yok; kitaplıkta
     * görünmesinler diye ayırt ediliyorlar.
     */
    const val WORDLIST_SOURCE_MARKER = "liste"

    fun isListDocument(source: String?): Boolean = source == WORDLIST_SOURCE_MARKER

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
