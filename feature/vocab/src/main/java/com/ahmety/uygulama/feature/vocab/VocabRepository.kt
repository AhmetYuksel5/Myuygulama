package com.ahmety.uygulama.feature.vocab

import android.content.Context
import com.ahmety.uygulama.core.database.dao.VocabDao
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.VocabProgressEntity
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.VocabWord
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kelime listesi asset'te sabit; kullanıcı durumu (biliyorum/bilmiyorum)
 * veritabanında. İki kaynağı burada birleştiriyoruz. Durum yazmaları değişiklik
 * günlüğünden geçtiği için ikinci telefona da taşınıyor.
 */
@Singleton
class VocabRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vocabDao: VocabDao,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    @Volatile
    private var cached: List<VocabWord>? = null

    suspend fun allWords(): List<VocabWord> = withContext(Dispatchers.IO) {
        cached ?: loadFromAsset().also { cached = it }
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
            ).map { VocabWord(word = it.w, meaning = it.t, example = it.e) }
        }
    }.getOrDefault(emptyList())

    @kotlinx.serialization.Serializable
    private data class RawWord(val w: String, val t: String, val e: String)

    private companion object {
        const val ASSET_NAME = "vocab_upper_intermediate.json"
    }
}
