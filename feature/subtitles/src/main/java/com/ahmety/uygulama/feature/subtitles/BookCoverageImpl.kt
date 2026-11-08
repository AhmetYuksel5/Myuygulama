package com.ahmety.uygulama.feature.subtitles

import android.content.Context
import com.ahmety.uygulama.core.database.dao.VocabDao
import com.ahmety.uygulama.core.database.entity.VocabProgressEntity
import com.ahmety.uygulama.feature.ebook.BookCoverage
import com.ahmety.uygulama.feature.ebook.BookCoverageCalculator
import com.ahmety.uygulama.feature.ebook.BookRepository
import com.ahmety.uygulama.feature.ebook.EpubParser
import com.ahmety.uygulama.feature.vocab.WordFrequencyStore
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kitap başına "ne kadarını anlarım".
 *
 * Bir kelime bilinen sayılıyor:
 * - destedeyse ve "biliyorum", "önemsiz" ya da iyice oturmuşsa (16 gün ve üstü);
 * - destede değilse ve sıklık listesinin ilk [assumedKnown] kelimesindeyse.
 *
 * Destede olup henüz oturmamış kelime ile listenin dışında kalan nadir
 * kelime bilinmiyor. Büyük harfle başlayıp listede olmayan kelimeler özel
 * isim sayılıp hesaba katılmıyor; yoksa her roman karakter adları yüzünden
 * zor görünürdü.
 */
@Singleton
class BookCoverageImpl @Inject constructor(
    @ApplicationContext context: Context,
    private val books: BookRepository,
    private val frequency: WordFrequencyStore,
    private val vocabDao: VocabDao,
) : BookCoverageCalculator {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override var assumedKnown: Int
        get() = prefs.getInt(KEY_ASSUMED, DEFAULT_ASSUMED)
        set(value) = prefs.edit().putInt(KEY_ASSUMED, value.coerceIn(0, 15_000)).apply()

    @Volatile
    private var englishRanks: Map<String, Int>? = null

    @Volatile
    private var arabicRanks: Map<String, Int>? = null

    override suspend fun coverage(bookId: Long): BookCoverage? = withContext(Dispatchers.Default) {
        val book = books.loadBook(bookId) ?: return@withContext null
        val paragraphs = book.chapters.asSequence()
            .flatMap { it.paragraphs.asSequence() }
            .filter { EpubParser.imagePath(it) == null }
            .toList()
        val sample = paragraphs.take(40).joinToString(" ")
        val arabic = sample.count { ArabicText.isArabicLetter(it) } > sample.count { it in 'a'..'z' }
        val ranks = if (arabic) arabicRanks() else englishRanks()
        val deck = vocabDao.observeAll().first().associateBy { it.word.lowercase() }
        val limit = assumedKnown

        var known = 0
        var counted = 0
        val unknown = HashMap<String, Int>()
        for (paragraph in paragraphs) {
            for (raw in SubtitleText.tokensOf(paragraph)) {
                if (raw.length < 2 || raw.contains('\'')) continue
                val word = if (arabic) ArabicText.normalize(raw) else raw.lowercase()
                val candidates = if (arabic) ArabicText.stems(word) else SubtitleDifficulty.stems(word)
                val entry = candidates.firstNotNullOfOrNull { deck[it] }
                val rank = SubtitleDifficulty.rankOf(word, ranks)
                if (entry == null && rank == null && !arabic && raw.first().isUpperCase()) continue

                counted++
                val isKnown = when {
                    entry != null -> entry.isSettled()
                    rank != null -> rank <= limit
                    else -> false
                }
                if (isKnown) {
                    known++
                } else {
                    // Listede geçen kökü yazılıyor: "lions" değil "lion".
                    val lemma = candidates.filter { ranks.containsKey(it) }
                        .minByOrNull { ranks.getValue(it) } ?: word
                    unknown[lemma] = (unknown[lemma] ?: 0) + 1
                }
            }
        }
        if (counted == 0) return@withContext null
        BookCoverage(
            percent = (known * 100L / counted).toInt(),
            counted = counted,
            unknown = unknown.entries.sortedByDescending { it.value }.take(UNKNOWN_LIMIT)
                .map { it.key to it.value },
        )
    }

    private fun VocabProgressEntity.isSettled(): Boolean = when (status) {
        "KNOWN", "IGNORED" -> true
        "LEARNING" -> box >= SETTLED_BOX
        else -> false
    }

    private fun englishRanks(): Map<String, Int> = englishRanks
        ?: frequency.words().withIndex().associate { (i, w) -> w.lowercase() to i + 1 }
            .also { englishRanks = it }

    private fun arabicRanks(): Map<String, Int> = arabicRanks
        ?: frequency.arabicWords().withIndex().associate { (i, w) -> ArabicText.normalize(w) to i + 1 }
            .also { arabicRanks = it }

    private companion object {
        const val PREFS = "merkez_kapsam"
        const val KEY_ASSUMED = "assumed_known"
        const val DEFAULT_ASSUMED = 3000

        /** Merdivende 16 günlük basamak: kelime artık oturmuş sayılıyor. */
        const val SETTLED_BOX = 4
        const val UNKNOWN_LIMIT = 30
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class BookCoverageModule {
    @Binds
    abstract fun bindCalculator(impl: BookCoverageImpl): BookCoverageCalculator
}
