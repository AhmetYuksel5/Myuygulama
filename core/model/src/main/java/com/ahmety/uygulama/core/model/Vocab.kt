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
    /** Kitaptan aktarıldıysa kelimenin geçtiği cümle. */
    val context: String = "",
    /** Kitapta mavi işaretlenip aktarılan kelime mi. */
    val fromBook: Boolean = false,
)

enum class VocabStatus {
    /** Henüz gösterilmedi / karar verilmedi. */
    NEW,

    /** Sola sürüklendi: biliyorum. */
    KNOWN,

    /** Sağa sürüklendi: bilmiyorum, çalışılacak. */
    LEARNING,

    /** Aşağı sürüklendi: emin olamadım, şimdilik dursun. */
    UNSURE,
}

data class VocabCard(
    val word: VocabWord,
    val status: VocabStatus,
)
