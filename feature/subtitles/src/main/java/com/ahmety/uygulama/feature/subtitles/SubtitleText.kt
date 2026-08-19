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

    // Burada Regex kullanmıyoruz. Bu sınıfa erişince "Beklenmedik hata:
    // ...SubtitleText" alıyorduk; sebebi kesin olarak bilinmiyor ama nesne
    // kurulurken yapılan iş ne kadar azsa o kadar iyi. Elle yazılan
    // tarayıcılar hem daha hızlı hem de tek tek denenebiliyor.

    /**
     * Zaman satırı: "00:01:02,500 --> 00:01:04,000".
     *
     * Yalnızca oka bakmak yetmiyor: replikte de ok geçebiliyor
     * ("The pointer --> north"). Rakam ve iki nokta da arıyoruz.
     */
    private fun isTimeLine(line: String): Boolean {
        val arrow = line.indexOf("-->")
        if (arrow < 0) return false
        val before = line.take(arrow)
        return before.contains(':') && before.any(Char::isDigit)
    }

    /**
     * `<i>`, `{\an8}` gibi biçim etiketlerini atar.
     *
     * Yalnızca kapanışı olan etiketi atıyoruz. Kapanmayan bir işareti etiket
     * saymak "It's 5 < 10, believe me." satırının yarısını yutuyordu.
     */
    private fun stripTags(line: String): String {
        if (!line.contains('<') && !line.contains('{')) return line
        val out = StringBuilder(line.length)
        var index = 0
        while (index < line.length) {
            val char = line[index]
            val closing = when (char) {
                '<' -> '>'
                '{' -> '}'
                else -> null
            }
            val end = closing?.let { line.indexOf(it, index + 1) } ?: -1
            if (end >= 0) {
                index = end + 1
            } else {
                out.append(char)
                index++
            }
        }
        return out.toString()
    }

    /**
     * Satırdaki kelimeler. Harfle başlayan, harf/kesme/tire ile süren
     * diziler. Rakam ve noktalama kelime değil.
     */
    private fun tokens(line: String): List<String> {
        val found = ArrayList<String>()
        var index = 0
        while (index < line.length) {
            if (!line[index].isAsciiLetter()) {
                index++
                continue
            }
            val start = index
            while (index < line.length &&
                (line[index].isAsciiLetter() || line[index] == '\'' || line[index] == '-')
            ) {
                index++
            }
            found.add(line.substring(start, index))
        }
        return found
    }

    private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

    /** Altyazıdaki konuşma satırları, sırayla. */
    fun lines(srt: String): List<String> = srt
        .replace("\r\n", "\n")
        .split("\n")
        .asSequence()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.all { char -> char.isDigit() } }
        .filterNot { isTimeLine(it) }
        .map { stripTags(it).replace("- ", "").trim() }
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
            tokens(line).forEach { raw ->
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
            val tokens = tokens(line).map { it.lowercase() }

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
