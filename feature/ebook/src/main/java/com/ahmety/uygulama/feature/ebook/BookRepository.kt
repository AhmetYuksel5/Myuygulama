package com.ahmety.uygulama.feature.ebook

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.database.repository.VocabProgressRepository
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.zip.ZipFile
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Imported(
        val entryId: Long,
        val title: String,
        /** PDF'in okuyucusu ayrı; yüklemeden sonra doğru ekrana gidilsin. */
        val pdf: Boolean = false,
    ) : ImportResult
    data class Failed(val reason: String) : ImportResult
}

/**
 * Kitaplar ortak kayıt çekirdeğinde `DOCUMENT` olarak yaşıyor:
 * başlık kitabın adı, gövde yazarı, `source` ise ayrıştırılmış metnin yolu.
 *
 * EPUB'ı her açılışta yeniden ayrıştırmak büyük kitaplarda saniyeler sürüyor;
 * bu yüzden içe aktarırken bir kez ayrıştırıp düz metni yanına yazıyoruz.
 */
@Singleton
class BookRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryRepository: EntryRepository,
    private val progressStore: VocabProgressRepository,
    private val covers: BookCoverStore,
) {

    private val readerPrefs = context.getSharedPreferences("merkez_kitap", Context.MODE_PRIVATE)

    private val booksDir: File
        get() = File(context.filesDir, "kitaplar").apply { mkdirs() }

    /** Son okunan bölüm resimleri; anahtar arşiv içindeki yol. */
    private val cachedImages = LinkedHashMap<String, Bitmap>()

    /**
     * Kitaplık. Filmler de burada: altyazı da okunacak bir metin, kitaptan
     * farkı yalnızca nereden geldiği. Süzgeç ekranda.
     */
    /**
     * Kitaplıktakiler. Yüklenen kelime listeleri de aynı kayıt türünü
     * kullanıyor ama okunacak bir metinleri yok; rafta görünmüyorlar.
     */
    fun observeBooks(): Flow<List<Entry>> = entryRepository.observeByType(EntryType.DOCUMENT)
        .map { documents -> documents.filterNot { HighlightRef.isListDocument(it.source) } }

    fun observeHighlights(): Flow<List<Entry>> = entryRepository.observeByType(EntryType.HIGHLIGHT)

    suspend fun import(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val epubFile = File(booksDir, "kitap_$stamp.epub")

        val copied = runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                epubFile.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching false
            true
        }.getOrDefault(false)
        if (!copied) return@withContext ImportResult.Failed("Dosya okunamadı.")

        // Uzantıya değil içeriğe bakıyoruz: seçicide gelen ad her zaman
        // doğru olmuyor, hele paylaşımdan gelen dosyalarda.
        if (PdfPages.isPdf(epubFile)) {
            val pdfFile = File(booksDir, "kitap_$stamp.pdf")
            if (!epubFile.renameTo(pdfFile)) {
                epubFile.delete()
                return@withContext ImportResult.Failed("PDF kaydedilemedi.")
            }
            return@withContext importPdf(pdfFile, displayNameOf(uri))
        }

        val book = EpubParser.parse(epubFile)
        if (book == null) {
            epubFile.delete()
            return@withContext ImportResult.Failed(
                "Bu EPUB okunamadı. Dosya bozuk veya kopya koruması olabilir.",
            )
        }

        val textFile = File(booksDir, "kitap_$stamp.json")
        runCatching { textFile.writeText(encodeBook(book)) }

        val id = entryRepository.createEntry(
            type = EntryType.DOCUMENT,
            title = book.title,
            body = book.author,
            source = textFile.absolutePath,
        )
        // Kapak bir kez çıkarılıp saklanıyor. Bulunamazsa kitaplık kitabın
        // adından türeyen renkli sırtı gösteriyor; kitap yine yüklenmiş
        // sayılıyor.
        EpubParser.coverBytes(epubFile)?.let { covers.save(id, it) }
        ImportResult.Imported(id, book.title)
    }

    /**
     * PDF'i kitaplığa koyar.
     *
     * Ayrıştırılacak bir şey yok: dosya olduğu gibi duruyor, okuyucu
     * sayfaları açtıkça çiziyor. Kapak olarak ilk sayfa saklanıyor —
     * rafta PDF'ler de kitaplar gibi görünsün.
     */
    private suspend fun importPdf(file: File, displayName: String?): ImportResult {
        val pages = PdfPages.open(file)
        if (pages == null || pages.pageCount == 0) {
            pages?.close()
            file.delete()
            return ImportResult.Failed("Bu PDF açılamadı. Dosya bozuk ya da parolalı olabilir.")
        }

        val title = displayName
            ?.substringBeforeLast('.')
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension

        val id = entryRepository.createEntry(
            type = EntryType.DOCUMENT,
            title = title,
            body = "${pages.pageCount} sayfa",
            source = "${HighlightRef.PDF_SOURCE_MARKER}:${file.absolutePath}",
        )
        runCatching {
            pages.render(0, COVER_WIDTH)?.let { bitmap ->
                val stream = java.io.ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                covers.save(id, stream.toByteArray())
                bitmap.recycle()
            }
        }
        pages.close()
        return ImportResult.Imported(id, title, pdf = true)
    }

    /** Seçicinin verdiği dosya adı; PDF'in başlığı için. */
    private fun displayNameOf(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }.getOrNull()

    /** Kayıt bir PDF mi. */
    fun isPdf(entry: Entry): Boolean = HighlightRef.isPdfDocument(entry.source)

    /** PDF dosyasını açar; okuyucu sayfaları buradan alıyor. */
    suspend fun openPdf(entryId: Long): PdfPages? = withContext(Dispatchers.IO) {
        val entry = entryRepository.getById(entryId) ?: return@withContext null
        val path = entry.source
            ?.takeIf { HighlightRef.isPdfDocument(it) }
            ?.removePrefix("${HighlightRef.PDF_SOURCE_MARKER}:")
            ?: return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        PdfPages.open(file)
    }

    /** Kaydın başlığı; PDF okuyucusu üst çubuğa yazıyor. */
    suspend fun titleOf(entryId: Long): String =
        entryRepository.getById(entryId)?.title.orEmpty()

    /** PDF'te kaldığın sayfa. */
    fun lastPage(bookId: Long): Int = readerPrefs.getInt("sayfa_$bookId", 0)

    fun saveLastPage(bookId: Long, page: Int) {
        readerPrefs.edit().putInt("sayfa_$bookId", page.coerceAtLeast(0)).apply()
    }

    /**
     * Film altyazısını kitaplığa okunabilir bir belge olarak koyar.
     *
     * Altyazı da bir metin: kitapta yaptığın gibi okuyup mavi/kırmızı
     * işaretleyebilmen için aynı biçime çeviriyoruz. Bölümler on dakikalık
     * bloklar değil, sabit sayıda replik — altyazıda zaman damgasını
     * atıyoruz, elimizde yalnızca sıra kalıyor.
     */
    suspend fun importFilm(
        title: String,
        release: String,
        sentences: List<String>,
    ): Long = withContext(Dispatchers.IO) {
        val stamp = System.currentTimeMillis()
        val chapters = sentences
            .chunked(FILM_CHAPTER_SENTENCES)
            .mapIndexed { index, block ->
                EpubChapter(title = "${index + 1}. bölüm", paragraphs = block)
            }
        val book = EpubBook(title = title, author = release, chapters = chapters)
        val textFile = File(booksDir, "film_$stamp.json")
        runCatching { textFile.writeText(encodeBook(book)) }

        entryRepository.createEntry(
            type = EntryType.DOCUMENT,
            title = title,
            body = release,
            // "film:" öneki hem kitaplıkta filmi ayırt ettiriyor hem de
            // metnin yolunu taşıyor; eski kayıtlarda yalnız "film" yazıyor.
            source = "${HighlightRef.SUBTITLE_SOURCE_MARKER}:${textFile.absolutePath}",
        )
    }

    /**
     * Eser künyesi için metinden örnek.
     *
     * Altyazıda genelde metnin tamamı sığıyor; kitapta baştan başlayıp
     * sınıra kadar alıyoruz — bir romanın ilk bölümleri dönemi, mekânı ve
     * dilin düzeyini zaten ele veriyor.
     */
    suspend fun sampleFor(title: String, maxChars: Int = SAMPLE_CHARS): String =
        withContext(Dispatchers.IO) {
            val entry = entryRepository.listByType(EntryType.DOCUMENT)
                .firstOrNull { it.title.trim().equals(title.trim(), ignoreCase = true) }
                ?: return@withContext ""
            val book = loadBook(entry.id) ?: return@withContext ""
            val out = StringBuilder()
            book.chapters.forEach { chapter ->
                chapter.paragraphs.forEach { paragraph ->
                    if (out.length >= maxChars) return@withContext out.toString()
                    out.append(paragraph).append('\n')
                }
            }
            out.toString()
        }

    /** Kayıt bir film altyazısı mı, gerçek bir kitap mı. */
    fun isFilm(entry: Entry): Boolean = HighlightRef.isFilmDocument(entry.source)

    /** Ayrıştırılmış metni okur. Kayıt yoksa veya dosya silinmişse null. */
    suspend fun loadBook(entryId: Long): EpubBook? = withContext(Dispatchers.IO) {
        val entry = entryRepository.getById(entryId) ?: return@withContext null
        val raw = entry.source ?: return@withContext null
        val path = raw.removePrefix("${HighlightRef.SUBTITLE_SOURCE_MARKER}:")
        if (path.isBlank() || path == HighlightRef.SUBTITLE_SOURCE_MARKER) return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        runCatching { decodeBook(file.readText(), entry.title, entry.body) }.getOrNull()
    }

    /**
     * Kitabı EPUB'ından yeniden ayrıştırır.
     *
     * Ayrıştırıcı geliştikçe (resimler, kapak) eski kitaplar geride
     * kalıyor: metin bir kez çıkarılıp saklanıyor ve bir daha
     * bakılmıyor. Arşiv kitabın yanında durduğu için yeniden okumak
     * mümkün; işaretlemeler kelimenin kendisine bağlı olduğundan
     * kayboluyor değil.
     */
    suspend fun refresh(entry: Entry): Boolean = withContext(Dispatchers.IO) {
        val jsonPath = entry.source
            ?.removePrefix("${HighlightRef.SUBTITLE_SOURCE_MARKER}:")
            ?: return@withContext false
        val epub = File(jsonPath.removeSuffix(".json") + ".epub")
        if (!epub.exists()) return@withContext false

        val book = EpubParser.parse(epub) ?: return@withContext false
        runCatching { File(jsonPath).writeText(encodeBook(book)) }
            .getOrElse { return@withContext false }
        EpubParser.coverBytes(epub)?.let { covers.save(entry.id, it) }
        cachedImages.clear()
        true
    }

    /** Yeniden taranabilir mi: yanında EPUB dosyası duruyor mu. */
    fun canRefresh(entry: Entry): Boolean {
        val jsonPath = entry.source
            ?.removePrefix("${HighlightRef.SUBTITLE_SOURCE_MARKER}:")
            ?: return false
        return File(jsonPath.removeSuffix(".json") + ".epub").exists()
    }

    /**
     * Bölüm içindeki bir resmi arşivden okur.
     *
     * Resimler yükleme sırasında ayrıca çıkarılmıyor: EPUB dosyası kitabın
     * yanında zaten duruyor ve arşivden tek girdi okumak milisaniyeler
     * sürüyor. Ayrıca çıkarmak aynı baytları ikinci kez saklamak olurdu.
     *
     * Ekrandan büyük resimler okunurken küçültülüyor; bir kitapta üç bin
     * piksellik sayfalar olabiliyor ve tam boy açmak belleği bir anda
     * dolduruyor.
     */
    suspend fun chapterImage(bookId: Long, entryPath: String, maxWidth: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            cachedImages[entryPath]?.let { return@withContext it }

            val entry = entryRepository.getById(bookId) ?: return@withContext null
            val jsonPath = entry.source
                ?.removePrefix("${HighlightRef.SUBTITLE_SOURCE_MARKER}:")
                ?: return@withContext null
            val epub = File(jsonPath.removeSuffix(".json") + ".epub")
            if (!epub.exists()) return@withContext null

            val bitmap = runCatching {
                ZipFile(epub).use { zip ->
                    val item = zip.getEntry(entryPath) ?: return@use null
                    val bytes = zip.getInputStream(item).use { it.readBytes() }
                    decodeScaled(bytes, maxWidth)
                }
            }.getOrNull()

            if (bitmap != null) {
                // Küçük bir tampon: aynı resmin önünden birkaç kez geçiliyor
                // (aşağı in, yukarı dön) ve her seferinde çözmek gereksiz.
                if (cachedImages.size >= IMAGE_CACHE_SIZE) {
                    cachedImages.keys.firstOrNull()?.let { cachedImages.remove(it) }
                }
                cachedImages[entryPath] = bitmap
            }
            bitmap
        }

    /** Ekrana sığacak kadar küçülterek çözer. */
    private fun decodeScaled(bytes: ByteArray, maxWidth: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        var sample = 1
        while (width > 0 && maxWidth > 0 && width / sample > maxWidth * 2) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /**
     * Bu belgenin işaretleri.
     *
     * Kitap ve film işaretleri farklı türde ("book" / "subtitle") çünkü
     * kelime listesi kaynağı oradan okuyor; ama okuyucu için ikisi de aynı
     * şey, bu yüzden ikisini birden alıyoruz.
     */
    suspend fun highlightsFor(bookId: Long): List<Entry> =
        entryRepository.listByType(EntryType.HIGHLIGHT)
            .filter { HighlightRef.sourceId(it.source) == bookId && isReadable(it.source) }

    /**
     * Kelimeyi işaretler ya da rengini değiştirir.
     *
     * Aynı kelimeyi ikinci bir cümlede işaretlersen o cümle de saklanıyor:
     * bir kelimenin birden çok anlamı olabiliyor ve hangi bağlamda
     * görüldüğü önemli. İşareti kaldırmak ayrı bir eylem ([removeHighlight]);
     * eskiden aynı renge tekrar dokunmak sessizce siliyordu.
     */
    suspend fun setHighlight(
        bookId: Long,
        word: String,
        contextSentence: String,
        color: HighlightColor,
    ) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        val existing = highlightsFor(bookId).firstOrNull {
            it.title.equals(trimmed, ignoreCase = true)
        }
        val source = HighlightRef.encode(kindOf(bookId), bookId, color)
        val sentence = contextSentence.trim()

        if (existing == null) {
            entryRepository.createEntry(
                type = EntryType.HIGHLIGHT,
                title = trimmed,
                body = sentence,
                source = source,
            )
            return
        }

        if (HighlightRef.color(existing.source) != color) {
            entryRepository.updateSource(existing.id, source)
        }
        // Yeni bir bağlam cümlesiyse alt alta ekle.
        val known = existing.body.lineSequence().map { it.trim() }.toSet()
        if (sentence.isNotBlank() && sentence !in known) {
            val merged = listOf(existing.body, sentence)
                .filter { it.isNotBlank() }
                .joinToString("\n")
            entryRepository.updateEntry(existing.id, existing.title, merged)
        }
    }

    /** İşareti tamamen kaldırır. */
    suspend fun removeHighlight(bookId: Long, word: String) {
        val trimmed = word.trim()
        highlightsFor(bookId)
            .firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
            ?.let { entryRepository.deleteEntry(it.id) }
    }

    /** Kitabın kapak dosyası; yoksa null. */
    fun coverFile(bookId: Long) = covers.fileFor(bookId)

    /** Kitapta en son okunan bölüm — kaldığın yerden devam edebilmek için. */
    fun lastChapter(bookId: Long): Int = readerPrefs.getInt("chapter_$bookId", 0)

    fun saveLastChapter(bookId: Long, index: Int) {
        readerPrefs.edit().putInt("chapter_$bookId", index).apply()
    }

    /**
     * Okuma yüzdesi.
     *
     * Kitaplıkta her satırın altındaki çizgi için. Kitabı açıp hesaplamak
     * pahalı (bütün bölümleri ayrıştırmak gerekiyor); okurken zaten
     * hesaplanan sayı buraya yazılıyor.
     */
    fun readingPercent(bookId: Long): Int = readerPrefs.getInt("percent_$bookId", 0)

    fun saveReadingPercent(bookId: Long, percent: Int) {
        readerPrefs.edit().putInt("percent_$bookId", percent.coerceIn(0, 100)).apply()
    }

    /** Bölüm içinde kaldığın paragraf — bölüm başına atmamak için. */
    fun lastParagraph(bookId: Long): Int = readerPrefs.getInt("paragraph_$bookId", 0)

    fun saveLastParagraph(bookId: Long, index: Int) {
        readerPrefs.edit().putInt("paragraph_$bookId", index).apply()
    }

    /**
     * Esere ait işaretleme sayısı: silme kutusunda "kaç kelime gidecek"
     * yazabilmek için. Sayı olmadan seçim körlemesine yapılıyor.
     */
    suspend fun highlightCount(bookId: Long): Int =
        entryRepository.listByType(EntryType.HIGHLIGHT)
            .count { HighlightRef.sourceId(it.source) == bookId }

    /**
     * Kitabı ya da filmi siler.
     *
     * [withHighlights] işaretlemeleri de kaldırıyor. Eskiden hep kalıyorlardı
     * ve kaynağı silinmiş kelimeler listede adsız olarak birikiyordu; tek tek
     * silmekten başka çareleri yoktu. Tekrar programındaki satırları da
     * temizliyoruz, yoksa kelime bir daha işaretlendiğinde eski damgasıyla
     * geri geliyor.
     *
     * Renge bakmıyoruz: kitap gidiyorsa üzerindeki sarı ya da yeşil işaretin
     * de dayanağı kalmıyor.
     */
    suspend fun deleteBook(entry: Entry, withHighlights: Boolean = false) {
        if (withHighlights) {
            val marks = entryRepository.listByType(EntryType.HIGHLIGHT)
                .filter { HighlightRef.sourceId(it.source) == entry.id }
            marks.forEach { entryRepository.deleteEntry(it.id) }
            progressStore.forgetAll(marks.map { it.title })
        }
        withContext(Dispatchers.IO) {
            entry.source?.let { raw ->
                if (HighlightRef.isPdfDocument(raw)) {
                    runCatching {
                        File(raw.removePrefix("${HighlightRef.PDF_SOURCE_MARKER}:")).delete()
                    }
                    return@let
                }
                val path = raw
                val json = File(path)
                runCatching { json.delete() }
                // Yanındaki .epub dosyasını da temizle.
                runCatching { File(path.removeSuffix(".json") + ".epub").delete() }
            }
        }
        covers.delete(entry.id)
        entryRepository.deleteEntry(entry.id)
    }

    private fun isReadable(source: String?): Boolean =
        HighlightRef.kind(source) in READABLE_KINDS

    /**
     * İşaret hangi türle kaydedilecek. Filmde işaretlediğin kelime kelime
     * listesinde "Filmden" görünmeli, "Kitaptan" değil.
     */
    private suspend fun kindOf(bookId: Long): String {
        val entry = entryRepository.getById(bookId) ?: return HighlightRef.KIND_BOOK
        return if (isFilm(entry)) HighlightRef.KIND_SUBTITLE else HighlightRef.KIND_BOOK
    }

    private companion object {
        /** Bellekte tutulan resim sayısı; fazlası belleği şişiriyor. */
        const val IMAGE_CACHE_SIZE = 6

        /** PDF kapağı için ilk sayfanın çizileceği genişlik. */
        const val COVER_WIDTH = 640

        /** Bir "bölüm"e kaç replik giriyor. Okuyucunun ilerleme çubuğu için. */
        const val FILM_CHAPTER_SENTENCES = 120

        /** Künye için gönderilecek en fazla karakter (~10 bin token). */
        const val SAMPLE_CHARS = 40_000

        val READABLE_KINDS = setOf(HighlightRef.KIND_BOOK, HighlightRef.KIND_SUBTITLE)
    }

    private fun encodeBook(book: EpubBook): String {
        val chapters = JSONArray()
        book.chapters.forEach { chapter ->
            val paragraphs = JSONArray()
            chapter.paragraphs.forEach { paragraphs.put(it) }
            chapters.put(
                JSONObject().apply {
                    put("t", chapter.title)
                    put("p", paragraphs)
                },
            )
        }
        return JSONObject().apply {
            put("title", book.title)
            put("author", book.author)
            put("chapters", chapters)
        }.toString()
    }

    private fun decodeBook(raw: String, fallbackTitle: String, fallbackAuthor: String): EpubBook {
        val root = JSONObject(raw)
        val chaptersJson = root.optJSONArray("chapters") ?: JSONArray()
        val chapters = (0 until chaptersJson.length()).map { index ->
            val chapter = chaptersJson.getJSONObject(index)
            val paragraphsJson = chapter.optJSONArray("p") ?: JSONArray()
            EpubChapter(
                title = chapter.optString("t"),
                paragraphs = (0 until paragraphsJson.length()).map { paragraphsJson.getString(it) },
            )
        }
        return EpubBook(
            title = root.optString("title").ifBlank { fallbackTitle },
            author = root.optString("author").ifBlank { fallbackAuthor },
            chapters = chapters,
        )
    }
}
