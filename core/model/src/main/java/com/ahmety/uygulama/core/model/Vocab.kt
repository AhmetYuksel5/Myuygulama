package com.ahmety.uygulama.core.model

/** Uygulamayla gelen sabit kelime; asset'ten yüklenir, değişmez. */
data class VocabWord(
    val word: String,
    val meaning: String,
    val example: String,
)

enum class VocabStatus {
    /** Henüz gösterilmedi / karar verilmedi. */
    NEW,

    /** Sola sürüklendi: biliyorum. */
    KNOWN,

    /** Sağa sürüklendi: bilmiyorum, çalışılacak. */
    LEARNING,
}

data class VocabCard(
    val word: VocabWord,
    val status: VocabStatus,
)
