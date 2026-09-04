package com.ahmety.uygulama.feature.ebook

import android.graphics.Bitmap
import androidx.annotation.RequiresApi
import com.ahmety.uygulama.core.designsystem.readingContext
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
 * Taranmış sayfada seçimin sonucu.
 *
 * Üç ayrı durum var ve kullanıcıya söylenecekleri farklı: kelime bulundu,
 * sayfada yazı tanınmadı, tanıma hiç çalıştırılamadı.
 */
/**
 * Sayfadaki bir metin satırının çerçevesi; oranlar (0..1).
 *
 * Seçimi metin gibi göstermek için gerekiyor: parmakla çizilen dikdörtgen
 * değil, satır satır şeritler. Kitapta seçim böyle görünüyor.
 */
data class PdfBand(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * İki nokta arasındaki seçimin şeritleri.
 *
 * Metin seçimi dikdörtgen değil: ilk satır tutulan yerden satır sonuna,
 * aradaki satırlar baştan sona, son satır satır başından bırakılan yere
 * kadar. Kitaptaki seçimin biçimi bu; PDF'te de aynısı çizilsin diye
 * burada hesaplanıyor.
 */
fun selectionBands(
    lines: List<PdfBand>,
    startX: Float,
    startY: Float,
    endX: Float,
    endY: Float,
): List<PdfBand> {
    if (lines.isEmpty()) return emptyList()
    val ordered = lines.sortedWith(compareBy({ it.top }, { it.left }))
    val first = lineIndexAt(ordered, startX, startY)
    val last = lineIndexAt(ordered, endX, endY)

    // Parmağın hangi sırayla gittiği önemli değil; seçim yukarıdan aşağı.
    val forward = first <= last
    val from = if (forward) first else last
    val to = if (forward) last else first
    val fromX = if (forward) startX else endX
    val toX = if (forward) endX else startX

    if (from == to) {
        val line = ordered[from]
        return listOf(
            line.copy(
                left = minOf(fromX, toX).coerceIn(line.left, line.right),
                right = maxOf(fromX, toX).coerceIn(line.left, line.right),
            ),
        )
    }
    return buildList {
        add(ordered[from].let { it.copy(left = fromX.coerceIn(it.left, it.right)) })
        for (i in from + 1 until to) add(ordered[i])
        add(ordered[to].let { it.copy(right = toX.coerceIn(it.left, it.right)) })
    }
}

/** Noktanın düştüğü satır; hiçbirinin içinde değilse en yakını. */
private fun lineIndexAt(ordered: List<PdfBand>, x: Float, y: Float): Int {
    val inside = ordered.indexOfFirst { y >= it.top && y <= it.bottom }
    if (inside >= 0) {
        // Aynı hizada birden çok şerit olabilir (iki sütun); yataya da bak.
        val sameRow = ordered.withIndex().filter { y >= it.value.top && y <= it.value.bottom }
        return sameRow.minByOrNull { (_, band) ->
            maxOf(band.left - x, 0f, x - band.right)
        }?.index ?: inside
    }
    return ordered.indices.minByOrNull { i ->
        val band = ordered[i]
        val dy = maxOf(band.top - y, 0f, y - band.bottom)
        val dx = maxOf(band.left - x, 0f, x - band.right)
        dy * 4f + dx
    } ?: 0
}

sealed interface OcrOutcome {
    data class Word(val word: PdfWord) : OcrOutcome

    /** Tanıma çalıştı ama seçilen yerde kelime yok. */
    data object Empty : OcrOutcome

    /** Tanıma çalıştırılamadı; çoğunlukla model henüz inmemiş olur. */
    data class Failed(val message: String) : OcrOutcome
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

    /**
     * OCR'ın kendi kilidi. Sayfa çizmenin kilidiyle aynı olamaz: tanıma
     * önce sayfayı çizdiriyor, tek kilit olsaydı kendini bekleyip
     * kilitlenirdi.
     */
    private val ocrMutex = Mutex()

    private val ocr = PdfOcr()

    /** Tanınan sayfalar. Aynı sayfada ikinci seçim beklemesin diye. */
    private val ocrCache = LinkedHashMap<Int, List<OcrWord>>()

    private val bandMutex = Mutex()

    /** Sayfaların satır çerçeveleri; seçimi çizerken kullanılıyor. */
    private val bandCache = LinkedHashMap<Int, List<PdfBand>>()

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
    suspend fun wordAt(index: Int, xFraction: Float, yFraction: Float): PdfWord? =
        selection(index, xFraction, yFraction, xFraction, yFraction)

    /**
     * İki nokta arasındaki metin.
     *
     * Aynı noktayı iki kez verirsen o noktadaki kelime seçiliyor; farklı
     * noktalar verirsen aradaki her şey. Parmağı basılı tutup sürüklemek
     * bunu kullanıyor.
     */
    suspend fun selection(
        index: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): PdfWord? {
        if (!textSupported) return null
        return mutex.withLock {
            withContext(Dispatchers.IO) {
                runCatching { readSelection(index, startX, startY, endX, endY) }.getOrNull()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private fun readSelection(
        index: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): PdfWord? =
        renderer.openPage(index).use { page ->
            fun boundaryAt(x: Float, y: Float) = SelectionBoundary(
                Point(
                    (x * page.width).toInt().coerceIn(0, page.width - 1),
                    (y * page.height).toInt().coerceIn(0, page.height - 1),
                ),
            )
            val selection = page.selectContent(
                boundaryAt(startX, startY),
                boundaryAt(endX, endY),
            ) ?: return null

            val contents = selection.selectedTextContents
            val text = contents.joinToString(" ") { it.text }.trim()
            if (text.isBlank()) return null

            val rects = contents.flatMap { it.bounds }
            if (rects.isEmpty()) return null
            val left = rects.minOf { it.left } / page.width
            val top = rects.minOf { it.top } / page.height
            val right = rects.maxOf { it.right } / page.width
            val bottom = rects.maxOf { it.bottom } / page.height

            // Bağlam: kelimenin geçtiği cümle. Bloğun tamamı değil —
            // PDF'te bir blok bazen bütün sayfa oluyor ve kart okunmaz
            // hâle geliyordu. Kural kitaptakiyle aynı.
            val context = runCatching {
                page.textContents
                    .map { it.text }
                    .firstOrNull { it.contains(text, ignoreCase = true) }
                    ?.let { readingContext(it, text) }
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

    /**
     * Sayfadaki metin satırlarının çerçeveleri.
     *
     * Seçimi metin gibi çizebilmek için gerekiyor. Önce PDF'in kendi metin
     * yerleşimi deneniyor — anında geliyor; yoksa tanınan kelimeler
     * satırlara toplanıyor.
     */
    suspend fun lines(index: Int): List<PdfBand> = bandMutex.withLock {
        bandCache[index]?.let { return@withLock it }

        val fromText = if (textSupported) textBands(index) else emptyList()
        val bands = fromText.ifEmpty { ocrBands(index) }

        bandCache[index] = bands
        while (bandCache.size > BAND_CACHE) {
            bandCache.remove(bandCache.keys.first())
        }
        bands
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private suspend fun textBands(index: Int): List<PdfBand> = mutex.withLock {
        withContext(Dispatchers.IO) {
            runCatching {
                renderer.openPage(index).use { page ->
                    val width = page.width.toFloat().coerceAtLeast(1f)
                    val height = page.height.toFloat().coerceAtLeast(1f)
                    page.textContents
                        .flatMap { it.bounds }
                        .map {
                            PdfBand(
                                left = it.left / width,
                                top = it.top / height,
                                right = it.right / width,
                                bottom = it.bottom / height,
                            )
                        }
                        .filter { it.right > it.left && it.bottom > it.top }
                }
            }.getOrDefault(emptyList())
        }
    }

    private suspend fun ocrBands(index: Int): List<PdfBand> =
        groupLines(ocrWords(index).getOrDefault(emptyList())).map { line ->
            PdfBand(
                left = line.minOf { it.left },
                top = line.minOf { it.top },
                right = line.maxOf { it.right },
                bottom = line.maxOf { it.bottom },
            )
        }

    /**
     * Tanınan kelimeleri satırlara toplar; her satır soldan sağa sıralı.
     *
     * Aynı satır sayılmak için dikey olarak örtüşmek yetiyor: tanıma
     * kutuları harflere tam oturmuyor ve tepe noktalarını karşılaştırmak
     * aynı satırdaki kelimeleri ayırıyordu.
     */
    private fun groupLines(words: List<OcrWord>): List<List<OcrWord>> {
        if (words.isEmpty()) return emptyList()
        val rows = mutableListOf<MutableList<OcrWord>>()
        words.sortedBy { it.top }.forEach { word ->
            val middle = (word.top + word.bottom) / 2
            val row = rows.lastOrNull { existing ->
                val top = existing.minOf { it.top }
                val bottom = existing.maxOf { it.bottom }
                middle in top..bottom
            }
            if (row == null) rows.add(mutableListOf(word)) else row.add(word)
        }
        return rows
            .map { row -> row.sortedBy { it.left } }
            .sortedBy { row -> row.minOf { it.top } }
    }

    /**
     * Taranmış sayfada iki nokta arasındaki metin.
     *
     * PDF'in kendi metnine bakan yol boş dönerse buraya düşülüyor: sayfa
     * yüksekçe bir çözünürlükte çizilip görüntüden yazı tanınıyor. Sayfa
     * bir kez tanınıyor, sonucu saklanıyor — tanıma bir saniyeye yakın
     * sürüyor ve aynı sayfada ikinci kelimeyi beklemenin anlamı yok.
     */
    suspend fun ocrSelection(
        index: Int,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): OcrOutcome {
        val words = ocrWords(index).getOrElse { error ->
            return OcrOutcome.Failed(
                error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName,
            )
        }
        if (words.isEmpty()) return OcrOutcome.Empty
        return pick(words, startX, startY, endX, endY)
            ?.let { OcrOutcome.Word(it) }
            ?: OcrOutcome.Empty
    }

    /** Sayfanın tanınmış kelimeleri; ilk seferde çizip tanıyor. */
    private suspend fun ocrWords(index: Int): Result<List<OcrWord>> = ocrMutex.withLock {
        ocrCache[index]?.let { return@withLock Result.success(it) }

        val bitmap = render(index, OCR_WIDTH, PdfCrop.FULL)
            ?: return@withLock Result.failure(IllegalStateException("Sayfa çizilemedi."))
        // Bitmap elle geri verilmiyor: tanıma iptal edilirse ML Kit onu
        // hâlâ okuyor olabiliyor ve geri verilmiş bir bitmap'e dokunmak
        // uygulamayı düşürüyor. Çöp toplayıcı zaten alıyor.
        val found = runCatching { ocr.words(bitmap) }

        found.onSuccess { list ->
            ocrCache[index] = list
            // Bellekte birkaç sayfa yetiyor; okuyucu ileri gidiyor,
            // gerideki sayfaya dönme ihtimali düşük.
            while (ocrCache.size > OCR_CACHE) {
                ocrCache.remove(ocrCache.keys.first())
            }
        }
        found
    }

    /**
     * Seçilen kelimeler; **okuma sırasına göre**.
     *
     * Aradaki her şey değil de iki noktanın çevrelediği dikdörtgene düşen
     * kelimeler alınsaydı seçim metin gibi değil, kutu gibi davranırdı:
     * satır ortasından başlayıp aşağı sürüklerken satırların baş tarafı
     * dışarıda kalırdı. Kitapta seçim nasılsa burada da öyle — tutulan
     * kelimeden bırakılan kelimeye kadar aradaki her kelime.
     *
     * Parmak sürüklenmeden kaldırılmışsa tek kelime seçiliyor; noktanın
     * içine düştüğü kelime, yoksa en yakını — tanıma kutuları harflere tam
     * oturmuyor ve tam isabet beklemek seçimi çoğu zaman boş bırakırdı.
     */
    private fun pick(
        words: List<OcrWord>,
        startX: Float,
        startY: Float,
        endX: Float,
        endY: Float,
    ): PdfWord? {
        val ordered = groupLines(words).flatten()
        if (ordered.isEmpty()) return null

        val tap = kotlin.math.abs(endX - startX) < POINT_SIZE &&
            kotlin.math.abs(endY - startY) < POINT_SIZE

        val chosen = if (tap) {
            val nearest = ordered.minByOrNull { gap(it, startX, startY) } ?: return null
            if (gap(nearest, startX, startY) > NEAR) return null
            listOf(nearest)
        } else {
            val a = indexNear(ordered, startX, startY) ?: return null
            val b = indexNear(ordered, endX, endY) ?: return null
            ordered.subList(minOf(a, b), maxOf(a, b) + 1)
        }
        if (chosen.isEmpty()) return null

        val text = chosen.joinToString(" ") { it.text }.trim()
        if (text.isBlank()) return null

        return PdfWord(
            text = text,
            left = chosen.minOf { it.left },
            top = chosen.minOf { it.top },
            right = chosen.maxOf { it.right },
            bottom = chosen.maxOf { it.bottom },
            context = readingContext(
                chosen.map { it.line }.distinct().joinToString(" "),
                text,
            ).take(MAX_CONTEXT),
        )
    }

    /**
     * Noktaya karşılık gelen kelimenin sırası.
     *
     * Satır önce geliyor: parmak satırın sağ ucunu geçtiğinde bir alttaki
     * satırın başındaki kelime değil, o satırın son kelimesi seçilmeli.
     */
    private fun indexNear(ordered: List<OcrWord>, x: Float, y: Float): Int? {
        val onRow = ordered.withIndex().filter { y >= it.value.top && y <= it.value.bottom }
        if (onRow.isNotEmpty()) {
            return onRow.minByOrNull { maxOf(it.value.left - x, 0f, x - it.value.right) }?.index
        }
        return ordered.indices.minByOrNull { i ->
            val word = ordered[i]
            val dy = maxOf(word.top - y, 0f, y - word.bottom)
            val dx = maxOf(word.left - x, 0f, x - word.right)
            dy * 4f + dx
        }
    }

    /** Noktanın kelime kutusuna uzaklığı; kutunun içindeyse sıfır. */
    private fun gap(word: OcrWord, x: Float, y: Float): Float {
        val dx = maxOf(word.left - x, 0f, x - word.right)
        val dy = maxOf(word.top - y, 0f, y - word.bottom)
        return kotlin.math.hypot(dx, dy)
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
     * yolu yok, ama çizerken atlanabilir.
     *
     * İki karar burada:
     *
     * 1. Örneklenen sayfaların kutuları **birleştiriliyor**, ortalaması
     *    alınmıyor. Ortanca, ilk satırı biraz yukarıda başlayan sayfaların
     *    tepesini kesiyordu — bir sayfayı okunmaz etmektense biraz fazla
     *    boşluk bırakmak yeğ.
     * 2. Her sayfanın kendi üst bilgisi ve sayfa numarası kutunun dışında
     *    bırakılıyor: gövdeden hep beyaz bir şeritle ayrıldıkları için
     *    bulunabiliyorlar. Her sayfada aynı satırı okumanın anlamı yok.
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
                    // Neredeyse tam sayfa kaplayan örnekler (tam sayfa
                    // görsel, kapak) birleşimi anlamsız kılıyor.
                    .filter { it.width < 0.95f || it.height < 0.95f }
                if (boxes.isEmpty()) return@runCatching PdfCrop.FULL

                val box = PdfCrop(
                    left = (boxes.minOf { it.left } - PADDING).coerceAtLeast(0f),
                    top = (boxes.minOf { it.top } - PADDING).coerceAtLeast(0f),
                    right = (boxes.maxOf { it.right } + PADDING).coerceAtMost(1f),
                    bottom = (boxes.maxOf { it.bottom } + PADDING).coerceAtMost(1f),
                )
                // Kazanç yoksa hiç uğraşma.
                if (box.width > 0.96f && box.height > 0.96f) PdfCrop.FULL else box
            }.getOrDefault(PdfCrop.FULL)
        }
    }

    /**
     * Tek bir sayfada gövde metninin sınırları; oran olarak.
     *
     * Önce satır satır mürekkep sayılıyor. Üstte ve altta, gövdeden beyaz
     * bir şeritle ayrılmış kısa bloklar varsa (üst bilgi, sayfa numarası)
     * dışarıda bırakılıyor. Sol ve sağ sınır yalnız kalan satırlara
     * bakılarak bulunuyor — yoksa sayfanın iki ucuna yayılan üst bilgi
     * kutuyu gereksiz genişletiyor.
     */
    private fun inkBox(page: PdfRenderer.Page): PdfCrop? = runCatching {
        val width = SAMPLE_WIDTH
        val height = (width.toFloat() * page.height / page.width).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        bitmap.recycle()

        // Satır başına mürekkepli piksel sayısı.
        val rowInk = IntArray(height)
        for (y in 0 until height) {
            val row = y * width
            var count = 0
            for (x in 0 until width) {
                if (isInk(pixels[row + x])) count++
            }
            rowInk[y] = count
        }

        var first = rowInk.indexOfFirst { it > 0 }
        var last = rowInk.indexOfLast { it > 0 }
        if (first < 0 || last < 0) return@runCatching null

        val gap = (height * GAP_RATIO).toInt().coerceAtLeast(2)
        val headerZone = (height * EDGE_ZONE).toInt()

        // Üst bilgi: ilk bloğun ardından yeterince geniş bir beyaz şerit
        // varsa ve blok sayfanın tepesine yakınsa, gövde şeritten sonra
        // başlıyor demektir.
        var y = first
        while (y <= last && rowInk[y] > 0) y++
        var blank = y
        while (blank <= last && rowInk[blank] == 0) blank++
        if (y - first < headerZone && blank - y >= gap && blank <= last) first = blank

        // Sayfa numarası: aynısı alttan.
        y = last
        while (y >= first && rowInk[y] > 0) y--
        blank = y
        while (blank >= first && rowInk[blank] == 0) blank--
        if (last - y < headerZone && y - blank >= gap && blank >= first) last = blank

        // Sol ve sağ, yalnız gövde satırlarına bakarak.
        var minX = width
        var maxX = -1
        for (row in first..last) {
            val base = row * width
            for (x in 0 until width) {
                if (isInk(pixels[base + x])) {
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                }
            }
        }
        if (maxX < 0) return@runCatching null

        PdfCrop(
            left = minX.toFloat() / width,
            top = first.toFloat() / height,
            right = (maxX + 1).toFloat() / width,
            bottom = (last + 1).toFloat() / height,
        )
    }.getOrNull()

    /** Kaba parlaklık: tarama kâğıdı bembeyaz olmuyor, eşik biraz gevşek. */
    private fun isInk(pixel: Int): Boolean {
        val luminance = ((pixel shr 16 and 0xFF) * 3 +
            (pixel shr 8 and 0xFF) * 6 +
            (pixel and 0xFF)) / 10
        return luminance < INK_THRESHOLD
    }

    override fun close() {
        runCatching { ocr.close() }
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

        /**
         * Çerçeve için örneklenecek sayfa sayısı. Birleşim alındığı için
         * ne kadar çok sayfaya bakılırsa o kadar az sayfa kesiliyor.
         */
        private const val SAMPLE_PAGES = 16

        /** Örnek sayfanın çizileceği genişlik; sınır bulmaya bu yetiyor. */
        private const val SAMPLE_WIDTH = 140

        /** Bu parlaklığın altındaki piksel yazı sayılıyor. */
        private const val INK_THRESHOLD = 232

        /** Çerçevenin etrafında bırakılan pay; harfler kenara yapışmasın. */
        private const val PADDING = 0.012f

        /** Üst bilgiyi gövdeden ayıran beyaz şeridin en az yüksekliği. */
        private const val GAP_RATIO = 0.018f

        /** Üst bilgi aranan bölge: sayfanın tepesinden bu kadarı. */
        private const val EDGE_ZONE = 0.10f

        /** Karta yazılacak bağlamın üst sınırı. */
        private const val MAX_CONTEXT = 400

        /**
         * Tanıma için sayfanın çizileceği genişlik.
         *
         * Tipik bir kitap sayfasında iki yüz nokta/inç civarına denk
         * geliyor; tanıma için önerilen alt sınırın üstünde, belleği de
         * zorlamıyor.
         */
        private const val OCR_WIDTH = 1600

        /** Bellekte tutulacak tanınmış sayfa sayısı. */
        private const val OCR_CACHE = 8

        /** Bellekte tutulacak satır çerçevesi sayfası. */
        private const val BAND_CACHE = 8

        /** Bundan küçük bir aralık sürükleme değil, tek dokunuş sayılıyor. */
        private const val POINT_SIZE = 0.01f

        /** Tek dokunuşta kelimenin bu kadar yakınına düşmek yetiyor. */
        private const val NEAR = 0.05f

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
