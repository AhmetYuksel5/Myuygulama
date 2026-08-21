package com.ahmety.uygulama.feature.vocab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sıklık listeleri.
 *
 * Kelimeler bir derlemede ne kadar sık geçtiklerine göre sıralı; listedeki
 * yer, kelimenin ne kadar bilindik olduğunun ölçüsü. Altyazıdan kelime ve
 * cümle seçerken zorluk buradan hesaplanıyor.
 *
 * Derlem OpenSubtitles: ölçmek istediğimiz şey tam olarak film dili.
 *
 * Eskiden burada bir de seviye tespit sınavı vardı — kelimeler tek tek
 * gösterilip "biliyorum/bilmiyorum" işaretleniyordu. Kaldırıldı; listeler
 * kaldı, çünkü asıl işi gören onlar.
 */
@Singleton
class WordFrequencyStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    @Volatile
    private var cached: List<String>? = null

    @Volatile
    private var cachedArabic: List<String>? = null

    /** Sıklık sırasına göre 10.000 İngilizce kelime. */
    fun words(): List<String> = cached ?: load(ASSET_NAME).also { cached = it }

    /**
     * Sıklık sırasına göre 15.000 Arapça kelime.
     *
     * İngilizce listesinden uzun olmasının sebebi Arapçanın çekim ve ek
     * zenginliği: aynı kelime listede birkaç yazımla görünüyor ve
     * kısaltmak arama isabetini düşürüyor.
     */
    fun arabicWords(): List<String> =
        cachedArabic ?: load(ARABIC_ASSET_NAME).also { cachedArabic = it }

    private fun load(asset: String): List<String> = runCatching {
        context.assets.open(asset).bufferedReader().useLines { lines ->
            lines.map { it.trim() }.filter { it.isNotEmpty() }.toList()
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val ASSET_NAME = "vocab_level_10k.txt"
        const val ARABIC_ASSET_NAME = "vocab_ar_15k.txt"
    }
}
