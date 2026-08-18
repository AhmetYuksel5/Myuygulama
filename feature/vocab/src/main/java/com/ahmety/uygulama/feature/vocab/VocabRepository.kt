package com.ahmety.uygulama.feature.vocab

import android.content.Context
import com.ahmety.uygulama.core.database.dao.VocabDao
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.VocabProgressEntity
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.VocabWord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kelime listesi iki kaynaktan geliyor:
 *  - uygulamayla gelen sabit deste (asset),
 *  - kitapta **mavi** işaretlediğin kelimeler (bilmediğin kelimeler).
 *
 * Kullanıcı durumu (biliyorum / bilmiyorum / emin değilim) veritabanında;
 * yazmalar değişiklik günlüğünden geçtiği için ikinci telefona da taşınıyor.
 */
@Singleton
class VocabRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vocabDao: VocabDao,
    private val entryRepository: EntryRepository,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<VocabWord>? = null

    /** Uygulamayla gelen sabit deste. */
    suspend fun assetWords(): List<VocabWord> = withContext(Dispatchers.IO) {
        cached ?: loadFromAsset().also { cached = it }
    }

    /**
     * Kitapta **mavi** işaretlenmiş kelimeler. Akış olarak veriyoruz: kitapta
     * yeni bir kelime işaretleyince kelime destesinde uygulamayı yeniden
     * başlatmadan belirmesi gerekiyor.
     */
    fun observeBookWords(): Flow<List<VocabWord>> =
        entryRepository.observeByType(EntryType.HIGHLIGHT).map { entries ->
            entries
                .filter { HighlightRef.color(it.source) == HighlightColor.BLUE }
                .filter { it.title.isNotBlank() }
                .map { entry ->
                    VocabWord(
                        word = entry.title.trim(),
                        meaning = "",
                        context = entry.body.trim(),
                        fromBook = true,
                    )
                }
                .distinctBy { it.word.lowercase() }
        }

    /**
     * Deste + kitaptan gelenler. Aynı kelime iki kaynakta da varsa kitaptan
     * geleni önceliyoruz: kendi bağlam cümleni taşıyor.
     */
    fun mergeWords(asset: List<VocabWord>, fromBooks: List<VocabWord>): List<VocabWord> {
        val byWord = LinkedHashMap<String, VocabWord>()
        fromBooks.forEach { byWord[it.word.lowercase()] = it }
        asset.forEach { existing ->
            val key = existing.word.lowercase()
            val book = byWord[key]
            byWord[key] = if (book == null) {
                existing
            } else {
                // Kitaptan gelen kelimenin anlamı yok; destede varsa
                // anlamını/örneklerini kullan, bağlam cümlesini koru.
                existing.copy(context = book.context, fromBook = true)
            }
        }
        return byWord.values.toList()
    }

    fun observeProgress(): Flow<List<VocabProgressEntity>> = vocabDao.observeAll()

    fun observeCount(status: VocabStatus): Flow<Int> =
        vocabDao.observeCountByStatus(status.name)

    suspend fun setStatus(word: String, status: VocabStatus) {
        val entity = VocabProgressEntity(
            word = word,
            status = status.name,
            updatedAt = now.millis(),
        )
        vocabDao.upsert(entity)
        changeRecorder.record(
            entityType = ChangeEntityType.VOCAB,
            entityUuid = word,
            operation = ChangeOperation.UPSERT,
            payload = json.encodeToString(VocabProgressEntity.serializer(), entity),
        )
    }

    private fun loadFromAsset(): List<VocabWord> = runCatching {
        context.assets.open(ASSET_NAME).bufferedReader().use { reader ->
            jsonParser.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(RawWord.serializer()),
                reader.readText(),
            ).map { raw ->
                VocabWord(
                    word = raw.w,
                    meaning = raw.t,
                    definition = raw.d,
                    examples = raw.e,
                    related = raw.r,
                )
            }
        }
    }.getOrDefault(emptyList())

    @kotlinx.serialization.Serializable
    private data class RawWord(
        val w: String,
        val t: String = "",
        val d: String = "",
        val e: List<String> = emptyList(),
        val r: List<String> = emptyList(),
    )

    private companion object {
        const val ASSET_NAME = "vocab_upper_intermediate.json"
    }
}
