package com.ahmety.uygulama.feature.ebook

import android.content.Context
import android.net.Uri
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

sealed interface ImportResult {
    data class Imported(val entryId: Long, val title: String) : ImportResult
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
) {

    private val readerPrefs = context.getSharedPreferences("merkez_kitap", Context.MODE_PRIVATE)

    private val booksDir: File
        get() = File(context.filesDir, "kitaplar").apply { mkdirs() }

    fun observeBooks(): Flow<List<Entry>> = entryRepository.observeByType(EntryType.DOCUMENT)

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
        ImportResult.Imported(id, book.title)
    }

    /** Ayrıştırılmış metni okur. Kayıt yoksa veya dosya silinmişse null. */
    suspend fun loadBook(entryId: Long): EpubBook? = withContext(Dispatchers.IO) {
        val entry = entryRepository.getById(entryId) ?: return@withContext null
        val path = entry.source ?: return@withContext null
        val file = File(path)
        if (!file.exists()) return@withContext null
        runCatching { decodeBook(file.readText(), entry.title, entry.body) }.getOrNull()
    }

    suspend fun highlightsFor(bookId: Long): List<Entry> =
        entryRepository.listByType(EntryType.HIGHLIGHT)
            .filter { HighlightRef.sourceId(it.source) == bookId && isBook(it.source) }

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
        val source = HighlightRef.encode(HighlightRef.KIND_BOOK, bookId, color)
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

    /** Kitapta en son okunan bölüm — kaldığın yerden devam edebilmek için. */
    fun lastChapter(bookId: Long): Int = readerPrefs.getInt("chapter_$bookId", 0)

    fun saveLastChapter(bookId: Long, index: Int) {
        readerPrefs.edit().putInt("chapter_$bookId", index).apply()
    }

    suspend fun deleteBook(entry: Entry) {
        withContext(Dispatchers.IO) {
            entry.source?.let { path ->
                val json = File(path)
                runCatching { json.delete() }
                // Yanındaki .epub dosyasını da temizle.
                runCatching { File(path.removeSuffix(".json") + ".epub").delete() }
            }
        }
        entryRepository.deleteEntry(entry.id)
    }

    private fun isBook(source: String?): Boolean =
        HighlightRef.kind(source) == HighlightRef.KIND_BOOK

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
