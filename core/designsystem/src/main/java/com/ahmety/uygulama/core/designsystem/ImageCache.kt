package com.ahmety.uygulama.core.designsystem

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/**
 * Küçültülmüş görsellerin durduğu klasör.
 *
 * Kaydedilen sayfaların önizlemesi ve kitap kapakları aynı işi yapıyordu;
 * iki ayrı yerde aynı elli satır duruyordu. Klasör adı ve ölçüler dışarıdan
 * geliyor, gerisi ortak.
 *
 * Kayıtlar veritabanına girmiyor: kaynak silinirse dosya da gidiyor, ikinci
 * telefona taşınacak bir şey de değil — orada yeniden üretilir.
 */
class ImageCache(
    context: Context,
    folder: String,
    private val maxEdge: Int = 800,
    private val quality: Int = 75,
) {

    private val dir: File by lazy {
        File(context.filesDir, folder).apply { mkdirs() }
    }

    fun fileFor(key: Long): File? = pathFor(key).takeIf { it.exists() }

    fun load(key: Long): Bitmap? {
        val file = fileFor(key) ?: return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /** Ham baytları küçültüp kaydeder. Başarısız olursa yarım dosya bırakmıyor. */
    fun save(key: Long, bytes: ByteArray): Boolean = runCatching {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@runCatching false
        val scaled = downscale(source)
        pathFor(key).outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, out)
        }
        if (scaled !== source) scaled.recycle()
        source.recycle()
        true
    }.getOrElse {
        runCatching { pathFor(key).delete() }
        false
    }

    fun delete(key: Long) {
        runCatching { pathFor(key).delete() }
    }

    private fun downscale(source: Bitmap): Bitmap {
        val longest = maxOf(source.width, source.height)
        if (longest <= maxEdge) return source
        val ratio = maxEdge.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * ratio).toInt().coerceAtLeast(1),
            (source.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    }

    private fun pathFor(key: Long): File = File(dir, "$key.jpg")
}
