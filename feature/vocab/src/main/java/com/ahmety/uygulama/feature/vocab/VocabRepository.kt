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
import com.ahmety.uygulama.core.model.VocabSource
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.VocabWord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
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
    private val enrichment: WordEnrichmentStore,
    private val hidden: HiddenWordStore,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<VocabWord>? = null

    /** Uygulamayla gelen sabit deste. */
    suspend fun assetWords(): List<VocabWord> = withContext(Dispatchers.IO) {
        val all = cached ?: loadFromAsset().also { cached = it }
        val skip = hidden.words()
        if (skip.isEmpty()) all else all.filter { it.word.lowercase() !in skip }
    }

    /**
     * Kitapta **mavi** işaretlenmiş kelimeler. Akış olarak veriyoruz: kitapta
     * yeni bir kelime işaretleyince kelime destesinde uygulamayı yeniden
     * başlatmadan belirmesi gerekiyor.
     */
    /**
     * Kitapta/altyazıda **mavi** işaretlenmiş kelimeler. Akış olarak veriyoruz:
     * yeni bir kelime işaretleyince uygulamayı yeniden başlatmadan destede
     * belirmesi gerekiyor.
     *
     * Kaynağın adı (kitabın/filmin başlığı) da geliyor: kelime listesini
     * "şu kitaptan" ya da "şu filmden" diye süzebilmek için.
     */
    fun observeBookWords(): Flow<List<VocabWord>> = combine(
        entryRepository.observeByType(EntryType.HIGHLIGHT),
        entryRepository.observeByType(EntryType.DOCUMENT),
    ) { entries, documents ->
        val titleById = documents.associate { it.id to it.title.trim() }
        entries
            .filter { HighlightRef.color(it.source) == HighlightColor.BLUE }
            .filter { it.title.isNotBlank() }
            .filter { it.title.trim().lowercase() !in hidden.words() }
            .map { entry ->
                val word = entry.title.trim()
                // Daha önce yapay zekâyla doldurulduysa onu kullan.
                val filled = enrichment.get(word)
                val kind = HighlightRef.kind(entry.source)
                VocabWord(
                    word = word,
                    meaning = filled?.meaning.orEmpty(),
                    definition = filled?.definition.orEmpty(),
                    examples = filled?.examples.orEmpty(),
                    related = filled?.related.orEmpty(),
                    phrases = filled?.phrases.orEmpty(),
                    context = entry.body.trim(),
                    source = when (kind) {
                        HighlightRef.KIND_SUBTITLE -> VocabSource.SUBTITLE
                        else -> VocabSource.BOOK
                    },
                    sourceName = HighlightRef.sourceId(entry.source)
                        ?.let { titleById[it] }
                        .orEmpty(),
                )
            }
            .distinctBy { it.word.lowercase() }
    }

    /**
     * Kelimeyi listeden kaldırır.
     *
     * Kitaptan/filmden gelen kelime kendi işaretleme kaydından siliniyor.
     * Sabit destedeki kelimenin kaydı asset'te; onu silemeyiz, gizlenenler
     * listesine yazıyoruz.
     */
    suspend fun deleteWord(word: VocabWord) {
        if (word.fromLibrary) {
            entryRepository.listByType(EntryType.HIGHLIGHT)
                .filter { it.title.trim().equals(word.word, ignoreCase = true) }
                .forEach { entryRepository.deleteEntry(it.id) }
        }
        hidden.hide(word.word)
    }

    /** Kullanıcının elle düzenlediği ya da çoğalttığı kelime bilgisi. */
    fun saveEdit(word: VocabWord) {
        enrichment.put(
            com.ahmety.uygulama.core.ai.WordInfo(
                word = word.word,
                meaning = word.meaning,
                definition = word.definition,
                examples = word.examples,
                related = word.related,
                phrases = word.phrases,
            ),
        )
    }

    /** Yapay zekâyla üretilen bilgiyi saklar. */
    fun saveEnrichment(info: com.ahmety.uygulama.core.ai.WordInfo) {
        enrichment.put(info)
    }

    /**
     * Akıştan gelen kitap kelimelerine, varsa yapay zekâ ile doldurulmuş
     * bilgileri uygular. Akış veritabanından geldiği için zenginleştirme
     * yazıldığında kendiliğinden güncellenmiyor.
     */
    fun applyEnrichment(words: List<VocabWord>): List<VocabWord> = words.map { word ->
        val filled = enrichment.get(word.word) ?: return@map word
        word.copy(
            meaning = filled.meaning,
            definition = filled.definition,
            examples = filled.examples,
            related = filled.related,
            phrases = filled.phrases,
        )
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
                existing.copy(
                    context = book.context,
                    source = book.source,
                    sourceName = book.sourceName,
                )
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
                    phrases = raw.p,
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
        val p: List<String> = emptyList(),
    )

    private companion object {
        const val ASSET_NAME = "vocab_upper_intermediate.json"
    }
}
