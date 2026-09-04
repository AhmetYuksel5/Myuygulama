package com.ahmety.uygulama.core.database.sync

import com.ahmety.uygulama.core.database.dao.ReadingProgressDao
import com.ahmety.uygulama.core.database.dao.EntryDao
import com.ahmety.uygulama.core.database.dao.HabitDao
import com.ahmety.uygulama.core.database.dao.TagDao
import com.ahmety.uygulama.core.database.dao.TaskDao
import com.ahmety.uygulama.core.database.dao.VocabDao
import com.ahmety.uygulama.core.database.entity.ReadingProgressEntity
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.EntryEntity
import com.ahmety.uygulama.core.database.entity.HabitCheckEntity
import com.ahmety.uygulama.core.database.entity.HabitEntity
import com.ahmety.uygulama.core.database.entity.TagEntity
import com.ahmety.uygulama.core.database.entity.TaskEntity
import com.ahmety.uygulama.core.database.entity.TaskListEntity
import com.ahmety.uygulama.core.database.entity.VocabProgressEntity
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Karşı cihazdan gelen değişikliği yerel veritabanına uygular.
 *
 * İki kural her tipte aynı:
 *
 * 1. **Yerel `id` korunur.** Otomatik artan `id` cihaza özeldir; gelen kaydın
 *    id'sini olduğu gibi yazmak yerel satırları birbirine karıştırırdı.
 *    Eşleştirme her zaman `uuid` üzerinden yapılır.
 * 2. **Son yazan kazanır.** `updatedAt` büyük olan uygulanır. Eşitlik hâlinde
 *    iki cihazın da aynı sonuca varması için deterministik bir tie-break
 *    kullanılır (payload metinlerinin karşılaştırılması) — yoksa cihazlar
 *    birbirinin üzerine sonsuza kadar yazabilirdi.
 */
@Singleton
class ChangeApplier @Inject constructor(
    private val habitDao: HabitDao,
    private val taskDao: TaskDao,
    private val entryDao: EntryDao,
    private val tagDao: TagDao,
    private val vocabDao: VocabDao,
    private val readingProgressDao: ReadingProgressDao,
    private val json: Json,
) {

    suspend fun apply(entityType: String, payload: String): Boolean = runCatching {
        when (entityType) {
            ChangeEntityType.HABIT -> applyHabit(payload)
            ChangeEntityType.HABIT_CHECK -> applyHabitCheck(payload)
            TASK_LIST -> applyTaskList(payload)
            TASK -> applyTask(payload)
            ChangeEntityType.ENTRY -> applyEntry(payload)
            ChangeEntityType.TAG -> applyTag(payload)
            ChangeEntityType.VOCAB -> applyVocab(payload)
            ChangeEntityType.READING -> applyReading(payload)
            else -> false
        }
    }.getOrDefault(false)

    private suspend fun applyHabit(payload: String): Boolean {
        val incoming = json.decodeFromString(HabitEntity.serializer(), payload)
        val local = habitDao.getByUuid(incoming.uuid)
        if (local != null && !incoming.wins(local.updatedAt, payload, local.serialized())) return false
        habitDao.upsert(incoming.copy(id = local?.id ?: 0L))
        return true
    }

    private suspend fun applyHabitCheck(payload: String): Boolean {
        val incoming = json.decodeFromString(HabitCheckEntity.serializer(), payload)
        val local = habitDao.getCheck(incoming.habitUuid, incoming.date)
        // Birincil anahtar (alışkanlık, gün) olduğu için id sorunu yok.
        if (local != null && incoming.updatedAt < local.updatedAt) return false
        habitDao.upsertCheck(incoming)
        return true
    }

    private suspend fun applyTaskList(payload: String): Boolean {
        val incoming = json.decodeFromString(TaskListEntity.serializer(), payload)
        val local = taskDao.getListByUuid(incoming.uuid)
        if (local != null && !incoming.wins(local.updatedAt, payload, local.serialized())) return false
        taskDao.upsertList(incoming.copy(id = local?.id ?: 0L))
        return true
    }

    private suspend fun applyTask(payload: String): Boolean {
        val incoming = json.decodeFromString(TaskEntity.serializer(), payload)
        val local = taskDao.getByUuid(incoming.uuid)
        if (local != null && !incoming.wins(local.updatedAt, payload, local.serialized())) return false
        taskDao.upsert(incoming.copy(id = local?.id ?: 0L))
        return true
    }

    private suspend fun applyEntry(payload: String): Boolean {
        val incoming = json.decodeFromString(EntryEntity.serializer(), payload)
        val local = entryDao.getByUuid(incoming.uuid)
        if (local != null && !incoming.wins(local.updatedAt, payload, local.serialized())) return false
        entryDao.upsert(incoming.copy(id = local?.id ?: 0L))
        return true
    }

    private suspend fun applyTag(payload: String): Boolean {
        val incoming = json.decodeFromString(TagEntity.serializer(), payload)
        // Etiketin adı benzersiz; aynı adlı etiket zaten varsa yenisini yazmıyoruz.
        if (tagDao.findByUuid(incoming.uuid) != null) return false
        if (tagDao.findByName(incoming.name) != null) return false
        tagDao.insert(incoming.copy(id = 0L))
        return true
    }

    /**
     * Kelime ilerlemesi tekrar programını da taşıyor; "son yazan kazanır"
     * tek başına yetmiyor.
     *
     * Kazananı yine [VocabProgressEntity.updatedAt] belirliyor, ama sayaçlar
     * geri gitmemeli ve sıradaki tekrar tarihi ileri alınmalı: aynı kelime
     * iki telefonda arka arkaya sorulursa çalışma boşa gider. En kötü ihtimalle
     * bir kademe ilerleme kaybediyoruz, o zararsız.
     */
    /**
     * Kaldığın yer: geç olan kazanıyor.
     *
     * Birleştirme yok, çünkü "daha ileride olan kazansın" yanlış olurdu —
     * kitabı baştan okumaya başlamak da geçerli bir hareket.
     */
    private suspend fun applyReading(payload: String): Boolean {
        val incoming = json.decodeFromString(ReadingProgressEntity.serializer(), payload)
        val local = readingProgressDao.get(incoming.entryUuid)
        if (local != null && incoming.updatedAt <= local.updatedAt) return false
        readingProgressDao.upsert(incoming)
        return true
    }

    private suspend fun applyVocab(payload: String): Boolean {
        val incoming = json.decodeFromString(VocabProgressEntity.serializer(), payload)
        val local = vocabDao.getIncludingDeleted(incoming.word)
        // word birincil anahtar; id sorunu yok, sadece zaman karşılaştırması.
        if (local != null && incoming.updatedAt < local.updatedAt) return false
        val merged = if (local == null) {
            incoming
        } else {
            incoming.copy(
                reviewCount = maxOf(incoming.reviewCount, local.reviewCount),
                lapseCount = maxOf(incoming.lapseCount, local.lapseCount),
                revealCount = maxOf(incoming.revealCount, local.revealCount),
                dueAt = maxOfNullable(incoming.dueAt, local.dueAt),
                lastReviewedAt = maxOfNullable(incoming.lastReviewedAt, local.lastReviewedAt),
                introducedAt = minOfNullable(incoming.introducedAt, local.introducedAt),
            )
        }
        vocabDao.upsert(merged)
        return true
    }

    /** İkisi de doluysa büyüğü; biri boşsa dolu olan. */
    private fun maxOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> maxOf(a, b)
    }

    private fun minOfNullable(a: Long?, b: Long?): Long? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }

    private fun EntryEntity.serialized(): String = json.encodeToString(EntryEntity.serializer(), this)

    private fun HabitEntity.serialized(): String = json.encodeToString(HabitEntity.serializer(), this)
    private fun TaskEntity.serialized(): String = json.encodeToString(TaskEntity.serializer(), this)
    private fun TaskListEntity.serialized(): String =
        json.encodeToString(TaskListEntity.serializer(), this)

    private companion object {
        const val TASK = "task"
        const val TASK_LIST = "task_list"
    }
}

/**
 * Gelen kayıt yerelin yerine geçmeli mi?
 *
 * Zaman damgaları eşitse metinleri karşılaştırıyoruz; keyfi ama **deterministik**
 * bir kural, ve iki cihazda da aynı cevabı verdiği için ikisi de aynı sonuca varır.
 */
private fun Any.wins(
    localUpdatedAt: Long,
    incomingPayload: String,
    localPayload: String,
): Boolean {
    val incomingUpdatedAt = when (this) {
        is EntryEntity -> updatedAt
        is HabitEntity -> updatedAt
        is TaskEntity -> updatedAt
        is TaskListEntity -> updatedAt
        else -> return false
    }
    return when {
        incomingUpdatedAt > localUpdatedAt -> true
        incomingUpdatedAt < localUpdatedAt -> false
        else -> incomingPayload > localPayload
    }
}
