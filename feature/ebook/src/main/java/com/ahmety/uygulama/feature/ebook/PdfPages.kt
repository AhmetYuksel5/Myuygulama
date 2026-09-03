package com.ahmety.uygulama.feature.ebook

import android.graphics.Bitmap
import androidx.annotation.RequiresApi
import android.os.Build
import android.graphics.pdf.models.selection.SelectionBoundary
import android.graphics.Point
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Sayfanın kırpılacak çerçevesi; oranlar (0..1).
 *
 * Belgenin tamamı için bir kez hesaplanıyor: sayfa başına ayrı hesaplansa
 * bölüm başlıkları gibi seyrek sayfalarda çerçeve değişir ve metnin boyu
 * sayfadan sayfaya oynardı.
 */
data class PdfCrop(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0.05f)
    val height: Float get() = (bottom - top).coerceAtLeast(0.05f)

    companion object {
        val FULL = PdfCrop(0f, 0f, 1f, 1f)
    }
}

/**
 * Sayfada dokunulan kelime: metni, sayfadaki yeri (oran) ve içinde geçtiği
 * metin bloğu.
 */
data class PdfWord(
    val text: String,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Kelimenin geçtiği blok; kelime kartındaki bağlam cümlesi için. */
    val context: String,
)

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

    /**
     * Sayfadaki metne erişilebiliyor mu.
     *
     * PDF'in metnini okumak Android 15 ile geldi (`getTextContents`,
     * `selectContent`). Daha eski sürümlerde sayfa yalnızca resim; kelimeye
     * dokunmanın karşılığı yok.
     */
    val textSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM

    /**
     * Verilen noktadaki kelime.
     *
     * Nokta sayfanın oranı olarak geliyor (0..1), böylece yakınlaştırma ve
     * kırpma çağıranın işi olarak kalıyor. Platformun kendi seçimi
     * kullanılıyor: başlangıç ve bitiş sınırı aynı noktaysa o noktadaki
     * kelime seçiliyor — okuyucuların çift dokunuşla yaptığı şey.
     */
    suspend fun wordAt(index: Int, xFraction: Float, yFraction: Float): PdfWord? {
        if (!textSupported) return null
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { readWord(index, xFraction, yFraction) }.getOrNull()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun readWord(index: Int, xFraction: Float, yFraction: Float): PdfWord? =
        renderer.openPage(index).use { page ->
            val point = Point(
                (xFraction * page.width).toInt().coerceIn(0, page.width - 1),
                (yFraction * page.height).toInt().coerceIn(0, page.height - 1),
            )
            val boundary = SelectionBoundary(point)
            val selection = page.selectContent(boundary, boundary) ?: return null

            val contents = selection.selectedTextContents
            val text = contents.joinToString(" ") { it.text }.trim()
            if (text.isBlank()) return null

            val rects = contents.flatMap { it.bounds }
            if (rects.isEmpty()) return null
            val left = rects.minOf { it.left } / page.width
            val top = rects.minOf { it.top } / page.height
            val right = rects.maxOf { it.right } / page.width
            val bottom = rects.maxOf { it.bottom } / page.height

            // Bağlam: kelimenin bulunduğu satırın/bloğun tamamı. Sayfanın
            // bütün metnini almak kart için fazla, kelimenin kendisi ise az.
            val context = runCatching {
                page.textContents
                    .map { it.text }
                    .firstOrNull { it.contains(text, ignoreCase = true) }
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    .orEmpty()
            }.getOrDefault("")

            PdfWord(
                text = text,
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                context = context.take(MAX_CONTEXT),
            )
        }

    /** Sayfayı verilen genişlikte çizer; [crop] verilirse o çerçeveye. */
    suspend fun render(index: Int, widthPx: Int, crop: PdfCrop = PdfCrop.FULL): Bitmap? =
        mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching {
                    renderer.openPage(index).use { page ->
                        drawPage(page, widthPx, crop)
                    }
                }.getOrNull()
            }
        }

    private fun drawPage(page: PdfRenderer.Page, widthPx: Int, crop: PdfCrop): Bitmap {
        // Toplam piksel sınırı. Yakınlaştırma oranı serbest ama bir sayfa
        // dört bayt/piksel tutuyor: üç katta A4 boyu bir sayfa altmış
        // megabaytı buluyor ve uygulama bellekten düşüyor. Sınırın ötesinde
        // görüntü hafifçe yumuşuyor, o kadar.
        val cropW = page.width * crop.width
        val cropH = page.height * crop.height
        val ratio = cropH / cropW
        val capped = minOf(
            widthPx.coerceAtLeast(1),
            MAX_EDGE,
            kotlin.math.sqrt(MAX_PIXELS / ratio).toInt().coerceAtLeast(1),
        )
        val height = (capped * ratio).toInt().coerceIn(1, MAX_EDGE)

        val bitmap = Bitmap.createBitmap(capped, height, Bitmap.Config.ARGB_8888)
        // Sayfa saydam geliyor; altına beyaz koymazsak koyu temada yazı
        // zeminle aynı renge düşüp kayboluyor.
        bitmap.eraseColor(Color.WHITE)

        // Kırpma, kesip atmakla değil dönüşümle yapılıyor: sayfanın
        // istenen parçası doğrudan bitmap'in tamamına çiziliyor. Böylece
        // atılacak piksel hiç üretilmiyor ve çözünürlük metne gidiyor.
        val matrix = Matrix()
        val scaleX = capped / cropW
        val scaleY = height / cropH
        matrix.postScale(scaleX, scaleY)
        matrix.postTranslate(
            -page.width * crop.left * scaleX,
            -page.height * crop.top * scaleY,
        )
        page.render(bitmap, null, matrix, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        return bitmap
    }

    /**
     * Belgedeki yazının kapladığı çerçeve.
     *
     * Sayfanın kenar boşlukları metnin parçası — PDF'e basılı, kaldırmanın
     * yolu yok, ama çizerken atlanabilir. Birkaç sayfa küçük boyda çizilip
     * beyaz olmayan piksellerin sınırı bulunuyor, sonra kenarların ortanca
     * değeri alınıyor: tek bir tam sayfa resim ya da boş sayfa çerçeveyi
     * bozmasın diye.
     */
    suspend fun contentBox(): PdfCrop = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                val total = renderer.pageCount
                if (total == 0) return@runCatching PdfCrop.FULL
                val step = (total / SAMPLE_PAGES).coerceAtLeast(1)
                val boxes = (0 until total step step)
                    .take(SAMPLE_PAGES)
                    .mapNotNull { index ->
                        renderer.openPage(index).use { page -> inkBox(page) }
                    }
                if (boxes.isEmpty()) return@runCatching PdfCrop.FULL

                fun middle(values: List<Float>): Float =
                    values.sorted()[values.size / 2]

                val box = PdfCrop(
                    left = (middle(boxes.map { it.left }) - PADDING).coerceAtLeast(0f),
                    top = (middle(boxes.map { it.top }) - PADDING).coerceAtLeast(0f),
                    right = (middle(boxes.map { it.right }) + PADDING).coerceAtMost(1f),
                    bottom = (middle(boxes.map { it.bottom }) + PADDING).coerceAtMost(1f),
                )
                // Kazanç yoksa hiç uğraşma: kenarları zaten dar bir belgede
                // kırpmak metni büyütmüyor, yalnız hata payı getiriyor.
                if (box.width > 0.94f && box.height > 0.94f) PdfCrop.FULL else box
            }.getOrDefault(PdfCrop.FULL)
        }
    }

    /** Tek bir sayfada yazının sınırları; oran olarak. */
    private fun inkBox(page: PdfRenderer.Page): PdfCrop? = runCatching {
        val width = SAMPLE_WIDTH
        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                val pixel = pixels[row + x]
                // Kaba parlaklık: tarama kâğıdı bembeyaz olmuyor, eşik
                // biraz gevşek.
                val luminance = ((pixel shr 16 and 0xFF) * 3 +
                    (pixel shr 8 and 0xFF) * 6 +
                    (pixel and 0xFF)) / 10
                if (luminance < INK_THRESHOLD) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }
            }
        }
        if (maxX < 0 || maxY < 0) return@runCatching null
        PdfCrop(
            left = minX.toFloat() / width,
            top = minY.toFloat() / height,
            right = (maxX + 1).toFloat() / width,
            bottom = (maxY + 1).toFloat() / height,
        )
    }.getOrNull()

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

        /** Çerçeve için örneklenecek sayfa sayısı. */
        private const val SAMPLE_PAGES = 9

        /** Örnek sayfanın çizileceği genişlik; sınır bulmaya bu yetiyor. */
        private const val SAMPLE_WIDTH = 140

        /** Bu parlaklığın altındaki piksel yazı sayılıyor. */
        private const val INK_THRESHOLD = 232

        /** Çerçevenin etrafında bırakılan pay; harfler kenara yapışmasın. */
        private const val PADDING = 0.012f

        /** Karta yazılacak bağlamın üst sınırı. */
        private const val MAX_CONTEXT = 400

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
