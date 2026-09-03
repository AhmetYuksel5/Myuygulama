package com.ahmety.uygulama.feature.ebook

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/** Bir sayfanın en-boy oranı; yerleşim resim gelmeden önce yerini bilsin diye. */
data class PdfPageSize(val width: Int, val height: Int) {
    val ratio: Float get() = if (height > 0) width.toFloat() / height else 0.7f
}

/**
 * PDF sayfalarını resme çevirir.
 *
 * Android'in kendi motoru (`PdfRenderer`) sayfayı çiziyor ama metnini
 * vermiyor; metin çıkaran kütüphaneler on megabaytın üzerinde ve
 * uygulamanın tamamı beş megabayt. Yani PDF'te kelime işaretleme yok,
 * okuma ve kaldığın yeri hatırlama var.
 *
 * `PdfRenderer` aynı anda tek sayfa açılmasına izin veriyor ve iş
 * parçacığı güvenli değil; bütün çizimler tek bir kilidin arkasından
 * geçiyor.
 */
class PdfPages private constructor(
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
) : Closeable {

    private val mutex = Mutex()

    val pageCount: Int get() = renderer.pageCount

    /**
     * Sayfa ölçüleri. Bir kez okunuyor: yerleşim, sayfa çizilmeden önce
     * ne kadar yer tutacağını bilmezse liste kaydırırken zıplıyor.
     */
    suspend fun sizes(): List<PdfPageSize> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                (0 until renderer.pageCount).map { index ->
                    renderer.openPage(index).use { page ->
                        PdfPageSize(page.width, page.height)
                    }
                }
            }.getOrDefault(emptyList())
        }
    }

    /** Sayfayı verilen genişlikte çizer. */
    suspend fun render(index: Int, widthPx: Int): Bitmap? = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    // Toplam piksel sınırı. Yakınlaştırma oranı serbest ama
                    // bir sayfa dört bayt/piksel tutuyor: üç katta A4 boyu
                    // bir sayfa altmış megabaytı buluyor ve uygulama
                    // bellekten düşüyor. Sınırın ötesinde görüntü hafifçe
                    // yumuşuyor, o kadar.
                    val ratio = page.height.toFloat() / page.width
                    val capped = minOf(
                        widthPx.coerceAtLeast(1),
                        MAX_EDGE,
                        kotlin.math.sqrt(MAX_PIXELS / ratio).toInt().coerceAtLeast(1),
                    )
                    val height = (capped.toLong() * page.height / page.width)
                        .toInt()
                        .coerceIn(1, MAX_EDGE)
                    val bitmap = Bitmap.createBitmap(
                        capped,
                        height,
                        Bitmap.Config.ARGB_8888,
                    )
                    // Sayfa saydam geliyor; altına beyaz koymazsak koyu
                    // temada yazı zeminle aynı renge düşüp kayboluyor.
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bitmap
                }
            }.getOrNull()
        }
    }

    override fun close() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        /** Tek bir kenarın sınırı. */
        private const val MAX_EDGE = 4096

        /**
         * Bir sayfanın toplam piksel sınırı: beş megapiksel, yani dört
         * bayttan yirmi megabayt. Kenar boşluklarını kırpmaya yetecek
         * yakınlaştırma (bir buçuk-iki kat) bu sınırın altında kalıyor.
         */
        private const val MAX_PIXELS = 5_000_000f

        fun open(file: File): PdfPages? = runCatching {
            val descriptor = ParcelFileDescriptor.open(
                file,
                ParcelFileDescriptor.MODE_READ_ONLY,
            )
            PdfPages(descriptor, PdfRenderer(descriptor))
        }.getOrNull()

        /** Dosya gerçekten PDF mi — uzantıya değil, ilk baytlara bakıyoruz. */
        fun isPdf(file: File): Boolean = runCatching {
            file.inputStream().use { input ->
                val head = ByteArray(5)
                input.read(head) == 5 && String(head, Charsets.US_ASCII) == "%PDF-"
            }
        }.getOrDefault(false)
    }
}
