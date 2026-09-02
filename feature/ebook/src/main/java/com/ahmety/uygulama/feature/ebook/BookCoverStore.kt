package com.ahmety.uygulama.feature.ebook

import android.content.Context
import com.ahmety.uygulama.core.designsystem.ImageCache
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kitap kapakları.
 *
 * EPUB'ın içinden bir kez çıkarılıp küçültülmüş hâlde saklanıyor: rafı her
 * açışta arşivi açıp kapak aramak, on kitapta gözle görülür bir bekleme
 * demek.
 *
 * Kapak dikey olduğu için uzun kenar biraz daha büyük tutuluyor; rafta
 * küçük görünse de detayda büyütülebilir.
 */
@Singleton
class BookCoverStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val cache = ImageCache(context, folder = "kitap_kapak", maxEdge = 640, quality = 78)

    fun fileFor(bookId: Long): File? = cache.fileFor(bookId)

    fun save(bookId: Long, bytes: ByteArray): Boolean = cache.save(bookId, bytes)

    fun delete(bookId: Long) = cache.delete(bookId)
}
