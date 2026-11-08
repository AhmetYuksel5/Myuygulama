package com.ahmety.uygulama.feature.ebook

/**
 * Bir kitabın ya da altyazının ne kadarının anlaşılacağı.
 *
 * [percent] bilinen kelimelerin metindeki payı. Araştırmalara göre metnin
 * %98'ini bilen okur rahat anlıyor, %95 asgari eşik.
 */
data class BookCoverage(
    val percent: Int,
    /** Hesaba giren kelime sayısı (özel isimler hariç). */
    val counted: Int,
    /** En sık geçen bilinmeyen kelimeler ve kaç kez geçtikleri. */
    val unknown: List<Pair<String, Int>>,
)

/**
 * Hesabı yapan taraf.
 *
 * Arayüz burada, hesap altyazı modülünde: kelime köklerini bulan kurallar,
 * Arapça desteği ve sıklık listeleri orada; bu modül oraya bağlanırsa
 * modüller birbirine döngüyle bağlanırdı.
 */
interface BookCoverageCalculator {
    /** Destede olmayan kelimelerden en sık kaç tanesinin bilindiği varsayılıyor. */
    var assumedKnown: Int

    suspend fun coverage(bookId: Long): BookCoverage?
}
