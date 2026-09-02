package com.ahmety.uygulama.feature.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kaydedilen sayfaların önizleme resimleri.
 *
 * Amaç kartın tanınması; sayfanın kendi kapak görselini olduğu gibi
 * saklamak değil. Gelen dosya çoğu zaman iki bin piksel genişliğinde ve
 * yüzlerce kilobayt — telefonda kaplayacağı yer bunun onda biri kadar, o
 * yüzden küçültülüp JPEG olarak yazılıyor.
 *
 * Resim veritabanına girmiyor: makale silinirse dosyası da gidiyor,
 * ikinci telefona taşınacak bir şey de değil.
 */
@Singleton
class ArticleImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir: File by lazy {
        File(context.filesDir, "makale_gorsel").apply { mkdirs() }
    }

    fun fileFor(entryId: Long): File? = pathFor(entryId).takeIf { it.exists() }

    fun load(entryId: Long): Bitmap? {
        val file = fileFor(entryId) ?: return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /** Ham baytları küçültüp kaydeder. Başarısız olursa yarım dosya bırakmıyor. */
    fun save(entryId: Long, bytes: ByteArray): Boolean = runCatching {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@runCatching false
        val scaled = downscale(source)
        pathFor(entryId).outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        if (scaled !== source) scaled.recycle()
        source.recycle()
        true
    }.getOrElse {
        runCatching { pathFor(entryId).delete() }
        false
    }

    fun delete(entryId: Long) {
        runCatching { pathFor(entryId).delete() }
    }

    private fun downscale(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= MAX_EDGE) return source
        val ratio = MAX_EDGE.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun pathFor(entryId: Long): File = File(dir, "$entryId.jpg")

    private companion object {
        /**
         * Kart tam genişlikte bile 400 piksel civarı yer kaplıyor; bunun
         * iki katı, iyi ekranda da yumuşak görünmesi için yeterli.
         */
        const val MAX_EDGE = 800

        const val QUALITY = 72
    }
}
