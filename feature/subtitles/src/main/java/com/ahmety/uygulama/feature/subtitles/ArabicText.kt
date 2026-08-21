package com.ahmety.uygulama.feature.subtitles

/**
 * Arapça metin işleme.
 *
 * İngilizce için yazdığımız her şey Arapçada çalışmıyor: harf aralığı
 * başka, harekeler aynı kelimeyi farklı gösteriyor, ekler sona değil hem
 * başa hem sona geliyor, ve büyük harf diye bir şey olmadığı için özel ad
 * ayıklama kuralı tümüyle düşüyor. Burası o farkları topluyor.
 */
object ArabicText {

    /** Arap alfabesi harfleri: hemzeden yeye. Rakamlar ve noktalama dışarıda. */
    fun isArabicLetter(char: Char): Boolean = char in 'ء'..'ي' ||
        char == 'ٱ' || char in 'ٲ'..'ۓ'

    /** Metinde Arapça harf var mı: kelimenin hangi dile ait olduğu buradan anlaşılıyor. */
    fun isArabic(text: String): Boolean = text.any { isArabicLetter(it) }

    /**
     * Harekeler ve uzatma işareti. Aynı kelime bir yerde harekeli bir yerde
     * harekesiz yazılıyor; sıklık listesinde eşleşmesi için ikisi de aynı
     * biçime indirilmeli.
     */
    private fun isDiacritic(char: Char): Boolean =
        char in 'ً'..'ٕ' || char == 'ٰ' || char == 'ـ'

    /**
     * Sıklık listesi araması için sadeleştirme.
     *
     * Lucene'in Arapça normalleştirmesiyle aynı kurallar: harekeleri at,
     * elifin bütün biçimlerini tek elife indir, `ى`yi `ي`ye, `ة`yi `ه`ye
     * çevir. Bunlar yazımı değiştirmiyor, yalnızca aramayı hizalıyor —
     * kullanıcıya gösterilen metne dokunmuyoruz.
     */
    fun normalize(word: String): String = buildString(word.length) {
        word.forEach { char ->
            if (isDiacritic(char)) return@forEach
            append(
                when (char) {
                    'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                    'ى' -> 'ي'
                    'ة' -> 'ه'
                    'ؤ' -> 'و'
                    'ئ' -> 'ي'
                    else -> char
                },
            )
        }
    }

    /**
     * Kök adayları.
     *
     * Arapça'nın gerçek biçimbilimi kalıp temelli — kök üç sessizden oluşuyor
     * ve kelime o sessizlerin bir kalıba oturmasıyla kuruluyor. Onu burada
     * çözmüyoruz; yaptığımız şey daha alçakgönüllü: kelimenin başındaki ve
     * sonundaki *yapışkan* ekleri soymak. Sıklık listesinde "وقبل" ve "قبل"
     * ayrı satırlar olduğu için bu kadarı aramayı ciddi biçimde iyileştiriyor.
     *
     * Aday üretmek ucuz: listede yoksa yok sayılıyor.
     */
    fun stems(word: String): List<String> {
        val base = normalize(word)
        val out = mutableListOf(base)

        fun add(value: String) {
            if (value.length >= 2 && value !in out) out += value
        }

        // Baştaki ekler. "ال" belirlilik takısı; tek harfliler bağlaç ve
        // edatlar (ve, fe, be, ke, le, se). Üst üste gelebiliyorlar:
        // "وبالقلم" = و + ب + ال + قلم.
        var head = base
        var peeled = 0
        while (peeled < 3) {
            val next = when {
                head.length > 4 && head.startsWith("وال") -> head.drop(3)
                head.length > 4 && head.startsWith("بال") -> head.drop(3)
                head.length > 4 && head.startsWith("كال") -> head.drop(3)
                head.length > 4 && head.startsWith("فال") -> head.drop(3)
                head.length > 3 && head.startsWith("ال") -> head.drop(2)
                head.length > 3 && head.first() in PREFIX_LETTERS -> head.drop(1)
                else -> null
            } ?: break
            head = next
            add(head)
            peeled++
        }

        // Sondaki ekler: çokluk, dişil, iyelik zamirleri.
        listOf(head, base).forEach { candidate ->
            SUFFIXES.forEach { suffix ->
                if (candidate.length > suffix.length + 2 && candidate.endsWith(suffix)) {
                    add(candidate.dropLast(suffix.length))
                }
            }
        }
        return out
    }

    /** Başa yapışan tek harfli bağlaç ve edatlar. */
    private val PREFIX_LETTERS = setOf('و', 'ف', 'ب', 'ك', 'ل', 'س')

    /**
     * Sona yapışan ekler. Uzundan kısaya sıralı: "هم" varken "م" denenmesin
     * diye değil, uzun ekin önce yakalanması için.
     */
    private val SUFFIXES = listOf(
        "تهما", "تهم", "تها", "هما", "كما", "تكم", "تنا",
        "ونه", "ينه", "ات", "ون", "ين", "ان", "هم", "هن", "ها",
        "كم", "كن", "نا", "ته", "تي", "ه", "ك", "ي", "ا",
    )

    /**
     * Cümle zorluğunda kullanılan yan cümle bağlayıcıları.
     * İngilizcedeki karşılıklarının yerine geçiyor.
     */
    val SUBORDINATORS = setOf(
        "الذي", "التي", "الذين", "اللاتي", "لان", "لانه", "رغم", "بالرغم",
        "بينما", "حين", "عندما", "اذا", "لو", "حتي", "كي", "لكي", "الا",
        "لكن", "ولكن", "بعدما", "قبلما", "منذ", "طالما", "ريثما", "حيث",
    )

    /**
     * Ağız ve konuşma dili işaretleri. Altyazılar çoğu zaman Mısır ya da
     * Şam ağzıyla; bunlar fasih metinde geçmiyor ve öğrenciyi asıl zorlayan
     * kısım oluyor.
     */
    val COLLOQUIAL = setOf(
        "مش", "ايه", "ليه", "كده", "عشان", "بس", "دلوقتي", "ازاي", "فين",
        "دي", "ده", "اهو", "خلاص", "يلا", "طيب", "ماشي", "شو", "هيك",
        "هلق", "منيح", "كتير", "بدي", "بدك", "وين", "ليش", "هاد", "هاي",
    )
}
