package com.ahmety.uygulama.feature.subtitles

/**
 * Zorluk ölçütü.
 *
 * Gerçek bir CEFR ölçümü değil — o, cümle başına bir yapay zekâ sorgusu
 * demek olurdu ve bir filmde bin beş yüz replik var. Bunun yerine
 * ölçülebilir birkaç işaret birleştiriliyor. Ölçek 0-100: kullanıcı bir eşik
 * veriyor, eşiğin üstündekiler çıkarılıyor.
 */
object SubtitleDifficulty {

    /**
     * Sıklık listesindeki *oran* → zorluk.
     *
     * Doğrusal değil: bir dilin ilk yüzde beşi metnin çoğunu kaplıyor, yani
     * 500. kelimeyle 600. arasındaki fark 5000. ile 6000. arasındakinden çok
     * daha küçük. Oran üzerinden tanımlı olması listenin uzunluğundan
     * bağımsız kılıyor — İngilizce listesi on bin, Arapça listesi on beş bin
     * kelime ve ikisi de aynı ölçeği kullanabiliyor.
     */
    private val ANCHORS = listOf(
        0.0f to 0,
        0.05f to 20,
        0.10f to 30,
        0.20f to 40,
        0.40f to 60,
        0.70f to 75,
        1.00f to 85,
    )

    /** Sıklık listesinde hiç geçmeyen kelimenin zorluğu. */
    private const val UNLISTED = 95

    fun ofRank(rank: Int?, listSize: Int): Int {
        if (rank == null || rank <= 0 || listSize <= 0) return UNLISTED
        val position = rank.toFloat() / listSize
        if (position >= 1f) return UNLISTED
        for (index in 1 until ANCHORS.size) {
            val (high, highScore) = ANCHORS[index]
            if (position > high) continue
            val (low, lowScore) = ANCHORS[index - 1]
            val span = (high - low).coerceAtLeast(0.0001f)
            return (lowScore + (position - low) / span * (highScore - lowScore)).toInt()
        }
        return UNLISTED
    }

    /**
     * Kelimenin sıklık listesindeki yeri — çekimlerini köküne indirerek.
     *
     * "lions" listede yok ama "lion" var; ikisini ayrı kelime saymak
     * bilinen bir kelimeyi "çok nadir" diye listeye sokuyordu. Kök adayları
     * içinde listede geçen en sık olanı alıyoruz: bir kelimeyi biliyorsan
     * çekimini de biliyorsundur.
     */
    fun rankOf(word: String, ranks: Map<String, Int>): Int? {
        val candidates = if (ArabicText.isArabic(word)) {
            ArabicText.stems(word)
        } else {
            stems(word)
        }
        return candidates.mapNotNull { ranks[it] }.minOrNull()
    }

    /**
     * Kök adayları. Gerçek bir biçimbilim çözümleyicisi değil; İngilizcenin
     * düzenli çekimlerini geri alan birkaç kural. Yanlış bir aday üretmesi
     * zararsız: listede yoksa yok sayılıyor.
     */
    internal fun stems(word: String): List<String> {
        val base = word.lowercase()
        val out = mutableListOf(base)
        fun add(value: String) {
            if (value.length >= 2 && value !in out) out += value
        }

        val length = base.length
        val doubled = length >= 4 && base[length - 3] == base[length - 4]

        when {
            base.endsWith("ies") && length > 4 -> {
                add(base.dropLast(3) + "y")
                add(base.dropLast(2))
            }

            base.endsWith("ves") && length > 4 -> {
                add(base.dropLast(3) + "f")
                add(base.dropLast(3) + "fe")
            }

            base.endsWith("es") && length > 3 -> {
                add(base.dropLast(1))
                add(base.dropLast(2))
            }

            base.endsWith("s") && !base.endsWith("ss") && length > 3 -> add(base.dropLast(1))
        }

        if (base.endsWith("ed") && length > 3) {
            // owed → owe, straightened → straighten, stopped → stop
            add(base.dropLast(1))
            add(base.dropLast(2))
            if (doubled) add(base.dropLast(3))
            if (base.endsWith("ied")) add(base.dropLast(3) + "y")
        }

        if (base.endsWith("ing") && length > 4) {
            // making → make, running → run
            add(base.dropLast(3))
            add(base.dropLast(3) + "e")
            if (length >= 5 && base[length - 4] == base[length - 5]) add(base.dropLast(4))
        }

        if (base.endsWith("ly") && length > 4) add(base.dropLast(2))

        if (base.endsWith("est") && length > 4) {
            add(base.dropLast(3))
            add(base.dropLast(2))
        } else if (base.endsWith("er") && length > 4) {
            add(base.dropLast(2))
            add(base.dropLast(1))
        }

        return out
    }

    /**
     * Cümlenin zorluğu.
     *
     * Beş işaretin ağırlıklı toplamı. Ağırlıklar okuma güçlüğünün nereden
     * geldiğine göre: en çok bilinmeyen kelime zorlar, sonra cümlenin
     * uzunluğu ve iç içe geçmişliği, sonra parçalarından anlaşılmayan
     * kalıplar ve konuşma dili.
     */
    fun ofSentence(sentence: String, ranks: Map<String, Int>, listSize: Int): Int {
        val tokens = SubtitleText.tokensOf(sentence)
        if (tokens.isEmpty()) return 0
        val arabic = ArabicText.isArabic(sentence)
        val words = tokens.map { if (arabic) ArabicText.normalize(it) else it.lowercase() }

        // 1. En zor kelime. Tek bilinmeyen kelime bile cümleyi durduruyor.
        val hardestWord = words
            .filter { it.length >= 3 && !it.contains('\'') }
            .maxOfOrNull { ofRank(rankOf(it, ranks), listSize) } ?: 0

        // 2. Uzunluk. Altı kelimelik replik kolay, yirmi beş kelimelik değil.
        val lengthScore = ((words.size - 6) * 100 / 19).coerceIn(0, 100)

        // 3. İç içe geçmişlik: yan cümle bağlayıcıları ve virgüller.
        val subordinators = if (arabic) ArabicText.SUBORDINATORS else SUBORDINATORS
        val clauses = words.count { it in subordinators } +
            sentence.count { it == ',' || it == '،' }
        val clauseScore = (clauses * 30).coerceAtMost(100)

        // 4. Parçalarından anlaşılmayan kalıplar.
        // Arapça için hazır bir deyim listemiz yok; İngilizce kalıpları Arapça
        // bir cümlede aramanın anlamı da yok.
        val idioms = if (arabic) 0 else IDIOMS.count { sentence.contains(it, ignoreCase = true) }
        val idiomScore = (idioms * 50).coerceAtMost(100)

        // 5. Konuşma dili: kısaltmalar ve ağız. Yazıda görmediğin biçimler.
        val speech = if (arabic) ArabicText.COLLOQUIAL else COLLOQUIAL
        val colloquial = tokens.count { it.contains('\'') } + words.count { it in speech }
        val colloquialScore = (colloquial * 100 / words.size.coerceAtLeast(1)).coerceAtMost(100)

        val total = hardestWord * 0.50f +
            lengthScore * 0.20f +
            clauseScore * 0.15f +
            idiomScore * 0.10f +
            colloquialScore * 0.05f
        return total.toInt().coerceIn(0, 100)
    }

    private val SUBORDINATORS = setOf(
        "that", "which", "who", "whom", "whose", "because", "although",
        "though", "while", "unless", "until", "whereas", "since", "if",
        "when", "whenever", "wherever", "before", "after", "as", "so",
    )

    private val COLLOQUIAL = setOf(
        "gonna", "wanna", "gotta", "ain", "yeah", "nah", "kinda", "sorta",
        "lemme", "gimme", "outta", "dunno", "y", "em", "cause",
    )

    /**
     * Filmlerde sık geçen, parçalarından anlaşılmayan kalıplar. Artık kendi
     * başlarına listeye girmiyorlar; cümle zorluğunda bir işaret olarak
     * kullanılıyorlar.
     */
    private val IDIOMS = listOf(
        "on purpose", "for good", "no big deal", "big deal", "make sense",
        "never mind", "by the way", "in charge", "out of hand",
        "keep an eye on", "make up your mind", "get rid of", "on my own",
        "out of nowhere", "in the first place", "at all costs",
        "no matter what", "sooner or later", "in the middle of", "on the run",
        "up to you", "let alone", "take it easy", "give it a shot",
        "hang in there", "cut it out", "back off", "settle down",
        "the whole point", "on the line", "out of the question",
        "in the long run", "for the record", "beside the point",
        "call it a day", "get away with", "look forward to", "run out of",
        "put up with", "come up with", "get along with", "keep up with",
        "stand up for", "watch out for", "make up for",
    )
}
