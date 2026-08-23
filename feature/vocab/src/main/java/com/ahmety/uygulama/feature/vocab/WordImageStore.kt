package com.ahmety.uygulama.feature.vocab

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kelime görsellerinin durduğu yer.
 *
 * Görsel bir kelimeye bir kez üretiliyor ve dosyada kalıyor: ikinci kez
 * istemek hem para hem bekleme demek. Kayıt veritabanına girmiyor çünkü
 * ikinci telefona taşınacak bir şey değil — gerekirse orada yeniden
 * üretilir.
 *
 * Gelen görsel bin yirmi dört piksellik kare; olduğu gibi saklamak yüz
 * kelimede yüz megabayt eder. Kartta kapladığı yer bunun çok altında, o
 * yüzden küçültüp JPEG olarak yazıyoruz.
 */
@Singleton
class WordImageStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val dir: File by lazy {
        File(context.filesDir, "kelime_gorsel").apply { mkdirs() }
    }

    /** Kelimenin görseli varsa dosyası, yoksa null. */
    fun fileFor(word: String): File? = pathFor(word).takeIf { it.exists() }

    fun load(word: String): Bitmap? {
        val file = fileFor(word) ?: return null
        return runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    /** Ham baytları küçültüp kaydeder. Başarısız olursa dosya bırakmıyor. */
    fun save(word: String, bytes: ByteArray): Boolean = runCatching {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            ?: return@runCatching false
        val scaled = downscale(source)
        val file = pathFor(word)
        file.outputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        }
        if (scaled !== source) scaled.recycle()
        source.recycle()
        true
    }.getOrElse {
        runCatching { pathFor(word).delete() }
        false
    }

    fun delete(word: String) {
        runCatching { pathFor(word).delete() }
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

    /**
     * Dosya adı. Kelimenin kendisi dosya adı olamıyor (boşluk, eğik çizgi,
     * Arap harfleri); okunur bir önek ve kararlı bir sayı birleştiriliyor.
     */
    private fun pathFor(word: String): File {
        val key = word.trim().lowercase()
        val slug = key.map { if (it in 'a'..'z' || it in '0'..'9') it else '_' }
            .joinToString("")
            .take(24)
        return File(dir, "${slug}_${key.hashCode()}.jpg")
    }

    private companion object {
        /** Kartta kapladığı alanın fazlasını saklamanın anlamı yok. */
        const val MAX_EDGE = 512

        const val QUALITY = 80
    }
}
