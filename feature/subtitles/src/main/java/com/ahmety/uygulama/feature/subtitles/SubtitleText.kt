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
     * Filmdeki kalıplar: öbek fiiller ve deyimler.
     *
     * Tek kelimelerden ayrı toplanıyorlar çünkü anlamları parçalarından
     * çıkmıyor — "put up with"i bilmek "put", "up" ve "with"i bilmekle
     * olmuyor. Filmde geçen ifadeler zaten kitaptakinden farklı.
     *
     * İki yol var: bilinen kalıp listesiyle eşleşme ve fiil + edat
     * kalıbının kendisi. İkincisi listede olmayanları da yakalıyor.
     */
    fun phrases(srt: String): List<SubtitleWord> {
        val all = lines(srt)
        val counts = LinkedHashMap<String, Int>()
        val contexts = HashMap<String, String>()

        all.forEachIndexed { index, line ->
            val sentence = listOfNotNull(
                all.getOrNull(index - 1),
                line,
                all.getOrNull(index + 1),
            ).joinToString(" ")
            val tokens = WORD.findAll(line).map { it.value.lowercase() }.toList()

            IDIOMS.forEach { idiom ->
                if (line.contains(idiom, ignoreCase = true)) {
                    counts[idiom] = (counts[idiom] ?: 0) + 1
                    contexts.putIfAbsent(idiom, sentence)
                }
            }

            tokens.forEachIndexed { position, token ->
                if (token !in PARTICLES) return@forEachIndexed
                val verb = tokens.getOrNull(position - 1) ?: return@forEachIndexed
                if (verb.length < 3 || verb in PARTICLES || verb in STOP_BEFORE) {
                    return@forEachIndexed
                }
                // Üç kelimelik olanlar ("put up with") ikiliyi de içeriyor;
                // uzun olanı tercih ediyoruz.
                val third = tokens.getOrNull(position + 1)
                val phrase = if (third != null && third in SECOND_PARTICLES) {
                    "$verb $token $third"
                } else {
                    "$verb $token"
                }
                counts[phrase] = (counts[phrase] ?: 0) + 1
                contexts.putIfAbsent(phrase, sentence)
            }
        }

        return counts
            .filterValues { it >= MIN_PHRASE_COUNT }
            .map { (phrase, count) ->
                SubtitleWord(word = phrase, count = count, context = contexts[phrase].orEmpty())
            }
            .sortedByDescending { it.count }
    }

    /** Aynı kalıp en az bu kadar geçmeliyse listeye giriyor; tek seferlikler gürültü. */
    private const val MIN_PHRASE_COUNT = 2

    /** Öbek fiillerin ikinci parçası. */
    private val PARTICLES = setOf(
        "up", "out", "off", "down", "in", "on", "away", "back", "over",
        "through", "around", "along", "apart", "aside", "ahead",
    )

    /** Üç kelimelik öbek fiillerin son parçası: "put up with", "look out for". */
    private val SECOND_PARTICLES = setOf("with", "for", "to", "of", "on")

    /** Fiil olamayacak, kalıp üretmeyecek kelimeler. */
    private val STOP_BEFORE = setOf(
        "the", "a", "an", "and", "but", "that", "this", "there", "here",
        "was", "were", "been", "being", "his", "her", "its", "their", "our",
        "your", "some", "any", "all", "not", "just", "right", "way", "one",
    )

    /**
     * Filmlerde sık geçen, parçalarından anlaşılmayan kalıplar. Liste kısa
     * ama seçici: her biri gerçekten deyim.
     */
    private val IDIOMS = listOf(
        "on purpose", "for good", "no big deal", "big deal", "make sense",
        "no way", "come on", "hold on", "hang on", "take care", "never mind",
        "by the way", "at least", "as well", "in charge", "out of hand",
        "keep an eye on", "make up your mind", "get rid of", "on my own",
        "out of nowhere", "for a while", "in the first place", "at all costs",
        "no matter what", "sooner or later", "in the middle of", "on the run",
        "up to you", "what if", "as if", "let alone", "take it easy",
        "give it a shot", "hang in there", "cut it out", "back off",
        "settle down", "figure out", "the whole point", "on the line",
        "out of the question", "in the long run", "for the record",
        "beside the point", "call it a day", "get away with", "look forward to",
        "run out of", "put up with", "come up with", "get along with",
        "keep up with", "stand up for", "watch out for", "make up for",
    )

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
