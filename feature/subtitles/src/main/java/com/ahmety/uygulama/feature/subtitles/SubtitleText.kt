package com.ahmety.uygulama.feature.subtitles

/** Altyazıdan çıkarılan bir kelime ve filmde geçtiği cümle. */
data class SubtitleWord(
    val word: String,
    val count: Int,
    /** Filmde ilk geçtiği replik; kelime kartında bağlam olarak duruyor. */
    val context: String,
)

/**
 * SRT çözümleme ve kelime çıkarma.
 *
 * SRT biçimi: sıra numarası, zaman satırı, bir ya da birkaç metin satırı,
 * boş satır. Bize yalnızca metin satırları gerekiyor.
 */
object SubtitleText {

    private val TIME_LINE = Regex("""\d{1,2}:\d{2}:\d{2}[,.]\d{3}\s*-->""")
    private val TAG = Regex("""<[^>]+>|\{[^}]*}""")
    private val WORD = Regex("""[A-Za-z][A-Za-z'-]*""")

    /** Altyazıdaki konuşma satırları, sırayla. */
    fun lines(srt: String): List<String> = srt
        .replace("\r\n", "\n")
        .split("\n")
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.all { char -> char.isDigit() } }
        .filterNot { TIME_LINE.containsMatchIn(it) }
        .map { TAG.replace(it, "").replace("- ", "").trim() }
        .filter { it.isNotEmpty() }
        .toList()

    /**
     * Filmdeki kelimeler ve ilk geçtikleri replik.
     *
     * Kelimeler küçük harfe indiriliyor; kesme işaretli kısaltmalar
     * ("don't", "we're") ayıklanıyor çünkü sözlükte karşılıkları yok.
     */
    fun words(srt: String): List<SubtitleWord> {
        val counts = LinkedHashMap<String, Int>()
        val contexts = HashMap<String, String>()
        // Bağlam olarak tek satır yerine komşularıyla birlikte veriyoruz:
        // altyazı satırları cümlenin ortasından bölünüyor.
        val all = lines(srt)
        all.forEachIndexed { index, line ->
            val sentence = listOfNotNull(
                all.getOrNull(index - 1),
                line,
                all.getOrNull(index + 1),
            ).joinToString(" ")
            WORD.findAll(line).forEach { match ->
                val raw = match.value
                if (raw.contains('\'')) return@forEach
                val word = raw.lowercase().trim('-')
                if (word.length < 3) return@forEach
                counts[word] = (counts[word] ?: 0) + 1
                contexts.putIfAbsent(word, sentence)
            }
        }
        return counts.map { (word, count) ->
            SubtitleWord(word = word, count = count, context = contexts[word].orEmpty())
        }
    }

    /**
     * Bilmediğin kelimeleri seçer.
     *
     * [knownUpToRank] seviye sınavından geliyor: bu sıklık sırasına kadar olan
     * kelimeleri zaten biliyorsun, onları çıkarmanın anlamı yok. Sıralamada
     * olmayan kelimeler (çok nadir) en sona değil en başa geliyor — filmi
     * anlamanı asıl onlar engelliyor.
     *
     * [alreadySeen] daha önce işaretlediğin ya da öğrendiğin kelimeler.
     */
    fun selectUnknown(
        words: List<SubtitleWord>,
        frequencyRank: Map<String, Int>,
        knownUpToRank: Int,
        alreadySeen: Set<String>,
        limit: Int,
    ): List<SubtitleWord> = words
        .asSequence()
        .filter { it.word !in alreadySeen }
        .filter { (frequencyRank[it.word] ?: Int.MAX_VALUE) > knownUpToRank }
        .sortedWith(
            compareByDescending<SubtitleWord> { it.count }
                .thenByDescending { frequencyRank[it.word] ?: Int.MAX_VALUE },
        )
        .take(limit)
        .toList()
}
