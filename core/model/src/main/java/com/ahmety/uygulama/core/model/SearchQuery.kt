package com.ahmety.uygulama.core.model

/**
 * Kullanıcının yazdığı metni SQLite FTS'in anlayacağı sorguya çevirir.
 *
 * Ham metni doğrudan `MATCH`'e vermek iki sorun çıkarır: tırnak, tire, yıldız
 * gibi karakterler FTS sözdizimini bozar ve sorgu çalışmaz; ayrıca kullanıcı
 * kelimenin tamamını yazmadan sonuç görmek ister. Bu yüzden metni sözcüklere
 * ayırıp temizliyor ve her sözcüğe önek eşleşmesi için `*` ekliyoruz.
 */
object SearchQuery {

    /** @return FTS sorgusu, ya da anlamlı bir şey kalmadıysa null */
    fun toFtsQuery(raw: String): String? {
        val tokens = raw
            .split(*DELIMITERS)
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

        if (tokens.isEmpty()) return null
        // Sözcükler AND'lenir: "kant ahlak" ikisini birden içeren kayıtları getirir.
        return tokens.joinToString(" ") { "$it*" }
    }

    private val DELIMITERS = charArrayOf(
        ' ', '\t', '\n', '\r', ',', ';', '.', ':', '!', '?',
        '"', '\'', '(', ')', '[', ']', '{', '}', '/', '\\', '-', '*', '^',
    )
}
