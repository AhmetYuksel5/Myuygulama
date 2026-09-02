package com.ahmety.uygulama.core.database.repository

import com.ahmety.uygulama.core.database.dao.VocabDao
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.VocabProgressEntity
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import com.ahmety.uygulama.core.model.startOfDay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tekrar programındaki satırların silinmesi.
 *
 * Kelimenin kendisi bir işaretleme kaydı, tekrar geçmişi ise ayrı bir satır.
 * İkisini birlikte silmek gerekiyor: yalnızca işaret silinirse kelime
 * yeniden işaretlendiğinde eski "öğrendim" damgasıyla geri geliyor ve
 * destede hiç görünmüyor.
 *
 * Burada duruyor çünkü iki yerden birden çağrılıyor — kelime listesinden ve
 * kitaplıktan bir eser silinirken. Kitaplık, kelime katmanına bağlı değil;
 * bağımlılık ters yönde.
 */
@Singleton
class VocabProgressRepository @Inject constructor(
    private val vocabDao: VocabDao,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    /**
     * Bir kelimenin tekrar satırını siler.
     *
     * Satır gerçekten silinmiyor, `deletedAt` damgalanıyor: senkronda karşı
     * cihaz silmeyi görebilsin, eski bir kayıt satırı diriltmesin diye.
     */
    suspend fun forget(word: String) {
        val existing = vocabDao.get(word) ?: return
        val timestamp = now.millis()
        val cleared = existing.copy(deletedAt = timestamp, updatedAt = timestamp)
        vocabDao.upsert(cleared)
        changeRecorder.record(
            entityType = ChangeEntityType.VOCAB,
            entityUuid = word,
            operation = ChangeOperation.DELETE,
            payload = json.encodeToString(VocabProgressEntity.serializer(), cleared),
        )
    }

    /**
     * Bugün tekrarı gelen kelime sayısı — alt çubuktaki rozet için.
     *
     * Kelime destesinin kendisine bakmıyor, yalnızca tekrar satırlarına:
     * rozet uygulamanın her ekranında canlı duruyor ve kelime listesini
     * kurmak (işaretlemeler, kaynak adları, yapay zekâ dolguları) rozet
     * için fazla pahalı. Vadesi geçmiş satırlar da sayılıyor; gün sonu
     * her toplamada yeniden hesaplanıyor ki gece yarısını geçince sayı
     * kendiliğinden düzelsin.
     */
    fun observeDueToday(): Flow<Int> = vocabDao.observeAll().map { rows ->
        val timestamp = now.millis()
        val offset = java.util.TimeZone.getDefault().getOffset(timestamp).toLong()
        val endOfToday = startOfDay(timestamp, offset) + DAY_MILLIS
        rows.count { row ->
            row.status == LEARNING &&
                row.lastReviewedAt != null &&
                row.dueAt != null &&
                row.dueAt!! <= endOfToday
        }
    }

    /** Toplu silme. Aynı kelime iki kez geçse de bir kez işleniyor. */
    suspend fun forgetAll(words: Collection<String>) {
        words.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .forEach { forget(it) }
    }

    private companion object {
        const val LEARNING = "LEARNING"
        const val DAY_MILLIS = 86_400_000L
    }
}
