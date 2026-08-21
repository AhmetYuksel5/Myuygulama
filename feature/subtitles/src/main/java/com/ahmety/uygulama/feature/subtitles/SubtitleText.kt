package com.ahmety.uygulama.feature.subtitles

/** Altyazıdan çıkarılan bir kelime ve filmde geçtiği cümle. */
data class SubtitleWord(
    val word: String,
    val count: Int,
    /** Filmde ilk geçtiği replik; kelime kartında bağlam olarak duruyor. */
    val context: String,
)

/**
 * Listeye önerilen bir şey: ya bir kelime ya da anlaşılması zor bir cümle.
 *
 * İkisi tek listede duruyor çünkü kullanıcı ikisini birlikte gözden geçirip
 * istemediğini çıkarıyor. Kaydedilirken kelime maviye, cümle kırmızıya
 * gidiyor — kitapta da ayrım bu.
 */
data class SubtitlePick(
    val text: String,
    val context: String,
    val count: Int,
    /** 0-100. Kullanıcının verdiği eşiğin üstünde olanlar seçiliyor. */
    val difficulty: Int,
    val sentence: Boolean,
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
    fun tokensOf(line: String): List<String> {
        val found = ArrayList<String>()
        var index = 0
        while (index < line.length) {
            if (!line[index].isWordLetter()) {
                index++
                continue
            }
            val start = index
            while (index < line.length &&
                (line[index].isWordLetter() || line[index] == '\'' || line[index] == '-')
            ) {
                index++
            }
            found.add(line.substring(start, index))
        }
        return found
    }

    /**
     * Kelime harfi. Latin alfabesi ve Arap alfabesi; ikisi de aynı altyazı
     * akışından geçiyor, ayrı bir ayrıştırıcı yazmak gerekmiyor.
     */
    private fun Char.isWordLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z' ||
        ArabicText.isArabicLetter(this)

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
            tokensOf(line).forEach { raw ->
                if (raw.contains('\'')) return@forEach
                // Arapça harekeli/harekesiz aynı kelime iki ayrı satır
                // olmasın diye yazım sadeleştiriliyor; Latin harflerinde
                // karşılığı küçük harfe indirmek.
                val word = if (ArabicText.isArabic(raw)) {
                    ArabicText.normalize(raw)
                } else {
                    raw.lowercase().trim('-')
                }
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
     * Altyazıyı cümlelere böler.
     *
     * Altyazı satırı cümle değil: bir cümle iki üç repliğe bölünüyor,
     * bazen tek replikte iki cümle oluyor. Nokta/soru/ünlem görene kadar
     * satırları birleştiriyoruz ki zorluk ölçüsü gerçek bir cümleye baksın.
     */
    fun sentences(srt: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        lines(srt).forEach { line ->
            if (current.isNotEmpty()) current.append(' ')
            current.append(line)
            if (line.endsWith('.') || line.endsWith('?') || line.endsWith('!')) {
                out += current.toString().trim()
                current.clear()
            }
            // Cümle bitmeden çok uzarsa (nokta düşmüş altyazılar) kesiyoruz.
            if (current.length > MAX_SENTENCE_CHARS) {
                out += current.toString().trim()
                current.clear()
            }
        }
        if (current.isNotBlank()) out += current.toString().trim()
        return out.filter { it.isNotBlank() }
    }

    /**
     * Altyazının okunabilir hâli: her replik ayrı bir paragraf.
     *
     * Okuma ekranı için cümle birleştirme yanlış birim: Arapça altyazılarda
     * nokta çoğu zaman hiç yok, o yüzden metin iki yüz karakterlik bloklara
     * dönüşüyor ve ekranda tek bir duvar gibi duruyor. Repliğin kendisi
     * zaten doğru birim — filmi izlerken ekranda gördüğün parça o.
     *
     * Bir repliğin birden çok satırı boşlukla birleştiriliyor; replikler
     * arasındaki sınır boş satırdan ya da yeni bir zaman satırından
     * anlaşılıyor.
     */
    fun cues(srt: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()

        fun flush() {
            val text = current.toString().trim()
            if (text.isNotEmpty()) out += text
            current.clear()
        }

        srt.replace("\r\n", "\n").split("\n").forEach { raw ->
            val line = raw.trim()
            when {
                line.isEmpty() -> flush()
                // Zaman satırı yeni repliğin başı; sıra numarası da öyle.
                isTimeLine(line) -> flush()
                line.all { it.isDigit() } -> flush()
                else -> {
                    val text = stripTags(line).replace("- ", "").trim()
                    if (text.isNotEmpty()) {
                        if (current.isNotEmpty()) current.append(' ')
                        current.append(text)
                    }
                }
            }
        }
        flush()
        return out
    }

    /**
     * Filmdeki özel adlar.
     *
     * "Janice" bilinmeyen bir kelime değil, bir kişi. Altyazıda özel ad her
     * geçtiğinde büyük harfle başlıyor ve hiçbir zaman küçük harfle
     * geçmiyor; sıradan kelimeler ise satır başında büyük, satır ortasında
     * küçük görünüyor. Ayrım buradan çıkıyor. Yalnız satır başında görülen
     * kelimeye karar veremiyoruz, onu özel ad saymıyoruz.
     */
    fun properNouns(srt: String): Set<String> {
        val capitalized = HashMap<String, Int>()
        val lowercase = HashMap<String, Int>()
        val midSentence = HashMap<String, Int>()

        lines(srt).forEach { line ->
            tokensOf(line).forEachIndexed { index, raw ->
                val key = raw.lowercase()
                if (raw.first().isUpperCase()) {
                    capitalized[key] = (capitalized[key] ?: 0) + 1
                    if (index > 0) midSentence[key] = (midSentence[key] ?: 0) + 1
                } else {
                    lowercase[key] = (lowercase[key] ?: 0) + 1
                }
            }
        }

        return capitalized.keys
            .filter { lowercase[it] == null }
            .filter { (midSentence[it] ?: 0) > 0 }
            .toSet()
    }

    /** Noktası düşmüş altyazılarda cümlenin sonsuza uzamasını engelliyor. */
    private const val MAX_SENTENCE_CHARS = 220

    /**
     * Zorluk eşiğini geçen kelimeler.
     *
     * Üç elek var. Özel adlar çıkıyor (film karakterinin adını öğrenmek
     * kelime öğrenmek değil). Çekimler köke iniyor, yani "lions" bilinen
     * "lion" sayılıyor. Kalanlar zorluk puanına göre eleniyor.
     *
     * Sıralama sıklığa değil zorluğa göre: bir kelimenin filmde dört kez
     * geçmesi onu zor yapmıyor, yalnızca sık yapıyor. Eşit zorlukta olanlar
     * arasında çok geçen öne alınıyor — onu duyma ihtimalin yüksek.
     */
    fun selectWords(
        words: List<SubtitleWord>,
        ranks: Map<String, Int>,
        properNouns: Set<String>,
        minDifficulty: Int,
        alreadySeen: Set<String>,
        limit: Int,
    ): List<SubtitlePick> = words
        .asSequence()
        .filter { it.word !in properNouns }
        .filter { it.word !in alreadySeen }
        .filter { it.word.length >= 3 }
        // Arapçada özel adı büyük harften ayırt edemiyoruz — öyle bir şey yok.
        // Yerine şu kural: on beş binlik listede hiç geçmeyen bir yazım büyük
        // ihtimalle bir addır ya da yazım hatasıdır, gerçek ama nadir bir
        // kelime değil. İngilizcede tersi geçerli, orada listede olmamak
        // kelimeyi zor yapan şeyin ta kendisi.
        .filter { !ArabicText.isArabic(it.word) || SubtitleDifficulty.rankOf(it.word, ranks) != null }
        .map { it to SubtitleDifficulty.ofRank(SubtitleDifficulty.rankOf(it.word, ranks), ranks.size) }
        .filter { (_, difficulty) -> difficulty >= minDifficulty }
        .sortedWith(
            compareByDescending<Pair<SubtitleWord, Int>> { it.second }
                .thenByDescending { it.first.count },
        )
        .take(limit)
        .map { (word, difficulty) ->
            SubtitlePick(
                text = word.word,
                context = word.context,
                count = word.count,
                difficulty = difficulty,
                sentence = false,
            )
        }
        .toList()

    /**
     * Zorluk eşiğini geçen cümleler.
     *
     * Kalıp listesi yerine bu var: filmde seni durduran şey çoğu zaman tek
     * bir kelime değil, kelimeleri bildiğin hâlde çözemediğin bir cümle.
     */
    fun selectSentences(
        srt: String,
        ranks: Map<String, Int>,
        minDifficulty: Int,
        alreadySeen: Set<String>,
        limit: Int,
    ): List<SubtitlePick> = sentences(srt)
        .asSequence()
        .filter { it.length in MIN_SENTENCE_CHARS..MAX_SENTENCE_CHARS }
        .distinctBy { it.lowercase() }
        .filter { it.lowercase() !in alreadySeen }
        .map { it to SubtitleDifficulty.ofSentence(it, ranks, ranks.size) }
        .filter { (_, difficulty) -> difficulty >= minDifficulty }
        .sortedByDescending { it.second }
        .take(limit)
        .map { (sentence, difficulty) ->
            SubtitlePick(
                text = sentence,
                context = sentence,
                count = 1,
                difficulty = difficulty,
                sentence = true,
            )
        }
        .toList()

    /** Bundan kısa bir "cümle" zaten bir cümle değil. */
    private const val MIN_SENTENCE_CHARS = 25
}
