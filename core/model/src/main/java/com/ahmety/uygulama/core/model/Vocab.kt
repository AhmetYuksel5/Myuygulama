package com.ahmety.uygulama.core.model

/**
 * Bir kelime kartı.
 *
 * Kart yüzünde yalnızca kelime durur; anlam, tanım, örnekler ve ilgili
 * kelimeler kelimeye dokununca açılır. Böylece önce hatırlamayı deneyip
 * sonra kontrol edebiliyorsun.
 */
data class VocabWord(
    val word: String,
    val meaning: String,
    /** Kısa İngilizce tanım. */
    val definition: String = "",
    val examples: List<String> = emptyList(),
    /** Eş/zıt anlamlılar ve aynı kökten türeyenler. */
    val related: List<String> = emptyList(),
    /** Kelimenin yaygın kullanıldığı öbekler ("abundant supply — bol arz"). */
    val phrases: List<String> = emptyList(),
    /** Kitaptan/filmden aktarıldıysa kelimenin geçtiği cümle. */
    val context: String = "",
    /** Kelimenin nereden geldiği. */
    val source: VocabSource = VocabSource.DECK,
    /** Geldiği kitabın ya da filmin adı; kaynağa göre süzmek için. */
    val sourceName: String = "",
) {
    /** Sabit desteden değil, kendi okuduğun/izlediğin şeyden gelen kelime. */
    val fromLibrary: Boolean get() = source != VocabSource.DECK
}

/** Kelimenin hangi kaynaktan geldiği. */
enum class VocabSource(val label: String) {
    /** Uygulamayla gelen sabit deste. */
    DECK("Deste"),

    /** Kitapta mavi işaretlenen kelime. */
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
