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
/** Bir PDF işaretinin sayfası ve sayfadaki yeri (oran olarak). */
data class PdfSpot(
    val page: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

object HighlightRef {

    const val KIND_BOOK = "book"
    const val KIND_ARTICLE = "article"

    /** Başka bir uygulamada seçilip buraya gönderilen metin. */
    const val KIND_SELECTION = "selection"

    /** Film altyazısından çıkarılan kelime. */
    const val KIND_SUBTITLE = "subtitle"

    /** Dışarıdan yüklenen bir listeden gelen kelime ya da ifade. */
    const val KIND_LIST = "list"

    /** Uygulamanın içinde elle yazılan kelime ya da ifade. */
    const val KIND_MANUAL = "manual"

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

    /**
     * PDF kaydının işareti: `pdf:/…/kitap_1.pdf`.
     *
     * PDF de kitaplıkta duruyor ama okuyucusu ayrı — metni yok, sayfaları
     * resim olarak çiziliyor. Kayıt türünü değiştirmek yerine kaynağın
     * önüne işaret koyuyoruz; film için de aynı yol izlenmişti.
     */
    const val PDF_SOURCE_MARKER = "pdf"

    fun isPdfDocument(source: String?): Boolean =
        source?.startsWith("$PDF_SOURCE_MARKER:") == true

    fun isListDocument(source: String?): Boolean = source == WORDLIST_SOURCE_MARKER

    fun encode(kind: String, sourceId: Long, color: HighlightColor): String =
        "$kind:$sourceId;color=${color.name}"

    fun kind(source: String?): String? =
        source?.substringBefore(':')?.takeIf { it.isNotBlank() }

    fun sourceId(source: String?): Long? =
        source?.substringAfter(':', "")?.substringBefore(';')?.toLongOrNull()

    fun color(source: String?): HighlightColor? {
        // Renkten sonra başka alanlar gelebiliyor (PDF'te sayfa ve
        // dikdörtgen); noktalı virgülde kesmezsek renk okunmuyor.
        val raw = source?.substringAfter("color=", "")
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        return runCatching { HighlightColor.valueOf(raw) }.getOrNull()
    }

    /**
     * PDF işaretinin yeri: hangi sayfa ve sayfanın neresi.
     *
     * Kayıt biçimi değişmiyor, kaynağın sonuna bir alan daha ekleniyor:
     * `book:12;color=BLUE;yer=17:0.11,0.32,0.28,0.35`. Dikdörtgen sayfanın
     * oranı olarak duruyor — yakınlaştırma ve kırpma değişince işaret
     * yerinden oynamasın diye.
     */
    fun encodeSpot(
        source: String,
        page: Int,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
    ): String = "$source;$SPOT_KEY=$page:$left,$top,$right,$bottom"

    /** Sayfa numarası ve dikdörtgen; işaret PDF'ten değilse null. */
    fun spot(source: String?): PdfSpot? {
        val raw = source?.substringAfter("$SPOT_KEY=", "")
            ?.substringBefore(';')
            ?.takeIf { it.isNotBlank() }
            ?: return null
        val page = raw.substringBefore(':').toIntOrNull() ?: return null
        val parts = raw.substringAfter(':').split(',')
        if (parts.size != 4) return null
        val numbers = parts.map { it.toFloatOrNull() ?: return null }
        return PdfSpot(page, numbers[0], numbers[1], numbers[2], numbers[3])
    }

    private const val SPOT_KEY = "yer"
}
