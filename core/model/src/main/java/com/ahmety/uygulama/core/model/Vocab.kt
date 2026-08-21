package com.ahmety.uygulama.core.model

/**
 * Bir kelime kartı.
 *
 * Kart yüzünde yalnızca kelime durur; anlam, tanım, örnekler ve eşdizimler
 * kelimeye dokununca açılır. Böylece önce hatırlamayı deneyip sonra kontrol
 * edebiliyorsun.
 */
data class VocabWord(
    val word: String,
    val meaning: String,
    /** Kısa İngilizce tanım. */
    val definition: String = "",
    /**
     * Okunuş künyesi. Arapçada kelimenin harekeli yazımı, Latin okunuşu ve
     * ezberlenmesi gereken biçimleri (isimde çoğul, fiilde mastar ve
     * muzari). Latin alfabeli dillerde boş: orada yazım okunuşu veriyor.
     */
    val reading: String = "",
    val examples: List<String> = emptyList(),
    /** Aynı anlam alanından kelimeler — aynı konuda doğal olarak geçenler. */
    val related: List<String> = emptyList(),
    /** Eş anlamlılar; kartta mavi rozette. */
    val synonyms: List<String> = emptyList(),
    /** Zıt anlamlılar; kartta kırmızı rozette. */
    val antonyms: List<String> = emptyList(),
    /**
     * Kelimenin kökeni: "morph- (Yun. morphē = şekil)". Asıl öğrenilecek şey
     * bu; bir kökü bilmek ona bağlı onlarca kelimeyi açıyor.
     */
    val root: String = "",
    /**
     * Kökendaş kelimeler: aynı kökten gelen **başka** İngilizce kelimeler,
     * anlamları birbirinden uzaklaşmış olsa bile — morph, morphology,
     * metamorphosis. Kelimenin kendi çekimleri değil.
     */
    val family: List<String> = emptyList(),
    /**
     * Şekilce benzeyen kelimeler — anlam ve köken olarak alakasız olsalar da.
     * Amaç karışıklığı önlemek değil, kelimeyi zihinde keskin sınırlarla
     * ayırmak: "zero" ile "Nero" birbirini tanımlar. Her satır
     * "kelime — anlamı; fark" biçiminde.
     */
    val confusions: List<String> = emptyList(),
    /**
     * Kelimenin hangi kelimelerle birlikte kullanıldığı, dilbilgisi kalıbına
     * göre gruplanmış. Oxford Collocations Dictionary mantığı: "make a
     * decision" mi "do a decision" mı sorusunun cevabı burada.
     */
    val collocations: List<Collocation> = emptyList(),
    /** Kitaptan/filmden aktarıldıysa kelimenin geçtiği cümle. */
    /** Kart üstünde sorup kaydettiğin sorular ve yanıtları. */
    val answers: List<String> = emptyList(),
    val context: String = "",
    /** Kelimenin nereden geldiği. */
    val source: VocabSource = VocabSource.SELECTION,
    /** Geldiği kitabın ya da filmin adı; kaynağa göre süzmek için. */
    val sourceName: String = "",
    /**
     * Kitapta **kırmızı** işaretlenen parça: tek kelime değil, anlaşılmayan
     * bir cümle ya da cümlecik. Anlamı sözlük maddesi gibi değil, çeviri ve
     * açıklama olarak isteniyor.
     */
    val isPassage: Boolean = false,
)

/**
 * Bir kullanım kalıbı ve o kalıptaki kelimeler.
 *
 * [pattern] kalıbın adı ("fiil +", "+ isim", "sıfat +", "+ edat"), [words] ise
 * o kalıpta kelimeyle birlikte kullanılanlar. Türkçe karşılık koymuyoruz:
 * kelimenin anlamı kartın üstünde zaten var, buradaki mesele doğru eşdizim.
 */
data class Collocation(
    val pattern: String,
    val words: List<String>,
)

/** Kelimenin nereden geldiği. */
enum class VocabSource(val label: String) {
    /** Başka bir uygulamada seçip gönderdiğin metin. */
    SELECTION("Seçtiklerim"),

    /** Kitapta işaretlenen kelime ya da cümle. */
    BOOK("Kitaptan"),

    /** Film altyazısından çıkarılan kelime. */
    SUBTITLE("Filmden"),
}

/**
 * Kelime hakkındaki karar.
 *
 * Kelime zaten bilinmediği için listeye giriyor; bu yüzden "biliyorum /
 * bilmiyorum" ayrımı yok. Kararlar öğrenme sürecinin neresinde olduğunu
 * söylüyor.
 */
enum class VocabStatus {
    /** Henüz karar verilmedi. */
    NEW,

    /** Yukarı: öğrendim, bir daha çıkmasın. */
    KNOWN,

    /** Sol: çalıştım, tekrar karşıma çıksın. */
    LEARNING,

    /** Aşağı: önemsiz, desteden çıksın — ama silinmesin. */
    IGNORED,

    /**
     * Eski sürümlerde "emin değilim" vardı. Kayıtlı veriyi bozmamak için
     * duruyor; okurken [LEARNING] gibi ele alınıyor, yeniden yazılmıyor.
     */
    UNSURE,
    ;

    /** Kaydedilmiş eski değerleri bugünkü anlamlarına indirger. */
    fun normalized(): VocabStatus = if (this == UNSURE) LEARNING else this
}

data class VocabCard(
    val word: VocabWord,
    val status: VocabStatus,
)
