package com.ahmety.uygulama.feature.ebook

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Taranmış sayfada tanınan bir kelime.
 *
 * Yerler sayfanın oranı olarak (0..1) tutuluyor, piksel olarak değil:
 * tanıma hangi çözünürlükte yapıldıysa yapılsın işaretin sayfadaki yeri
 * değişmesin diye.
 */
data class OcrWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Kelimenin geçtiği satırın tamamı; kelime kartındaki bağlam için. */
    val line: String,
)

/**
 * Sayfa görüntüsünden yazı tanıma.
 *
 * Taranmış PDF'te sayfa baştan sona resim; içinde harf yok, dolayısıyla
 * PDF'in kendi metnine bakan yol boş dönüyor. Tek çare piksele bakmak.
 *
 * ML Kit'in Play Hizmetleri'nden çalışan sürümü kullanılıyor: tanıma
 * modeli APK'nın içinde taşınmıyor, telefonun kendi Play Hizmetleri'nden
 * geliyor. Uygulamaya eklediği birkaç yüz kilobayt yalnızca çağrı
 * katmanı. Karşılığında model ilk kullanımdan önce inmiş olmalı; inmemişse
 * çağrı hata veriyor ve bunu kullanıcıya söylüyoruz.
 *
 * Latin tanıyıcısı Türkçe için yeterli: ı, ş, ğ, ç, ö, ü Latin alfabesinin
 * parçası olarak tanınıyor.
 */
class PdfOcr : Closeable {

    private val recognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    /** Verilen sayfa görüntüsündeki bütün kelimeler. */
    suspend fun words(bitmap: Bitmap): List<OcrWord> {
        val recognized = recognize(bitmap)
        val width = bitmap.width.toFloat().coerceAtLeast(1f)
        val height = bitmap.height.toFloat().coerceAtLeast(1f)
        return buildList {
            recognized.textBlocks.forEach { block ->
                block.lines.forEach { line ->
                    line.elements.forEach { element ->
                        val box = element.boundingBox ?: return@forEach
                        add(
                            OcrWord(
                                text = element.text,
                                left = box.left / width,
                                top = box.top / height,
                                right = box.right / width,
                                bottom = box.bottom / height,
                                line = line.text,
                            ),
                        )
                    }
                }
            }
        }
    }

    /** ML Kit geri çağrıyla çalışıyor; askıya alınmış çağrıya çeviriyoruz. */
    private suspend fun recognize(bitmap: Bitmap): Text =
        suspendCancellableCoroutine { continuation ->
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { continuation.resume(it) }
                .addOnFailureListener { continuation.resumeWithException(it) }
        }

    override fun close() {
        runCatching { recognizer.close() }
    }
}
