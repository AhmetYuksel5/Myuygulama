package com.ahmety.uygulama.feature.reader

import android.content.Context
import com.ahmety.uygulama.core.designsystem.ImageCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kaydedilen sayfaların önizleme resimleri.
 *
 * Amaç kartın tanınması; sayfanın kendi kapak görselini olduğu gibi
 * saklamak değil. Gelen dosya çoğu zaman iki bin piksel genişliğinde ve
 * yüzlerce kilobayt — telefonda kaplayacağı yer bunun onda biri kadar.
 */
@Singleton
class ArticleImageStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cache = ImageCache(context, folder = "makale_gorsel", maxEdge = 800, quality = 72)

    fun fileFor(entryId: Long): File? = cache.fileFor(entryId)

    fun save(entryId: Long, bytes: ByteArray): Boolean = cache.save(entryId, bytes)

    fun delete(entryId: Long) = cache.delete(entryId)
}
