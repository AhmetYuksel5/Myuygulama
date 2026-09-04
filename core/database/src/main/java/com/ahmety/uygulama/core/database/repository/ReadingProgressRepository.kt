package com.ahmety.uygulama.core.database.repository

import com.ahmety.uygulama.core.database.dao.ReadingProgressDao
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.ReadingProgressEntity
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kaldığın yer.
 *
 * Her yazma değişiklik günlüğüne satır bırakıyor; böylece diğer telefon da
 * aynı yerden devam ediyor. Anahtar eserin kalıcı kimliği, yerel kimliği
 * değil.
 */
@Singleton
class ReadingProgressRepository @Inject constructor(
    private val dao: ReadingProgressDao,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    suspend fun get(entryUuid: String): ReadingProgressEntity? = dao.get(entryUuid)

    /**
     * Verilen alanları günceller; verilmeyenler olduğu gibi kalıyor.
     *
     * PDF okuyucusu sayfayı, kitap okuyucusu bölüm ve paragrafı yazıyor;
     * ikisi de yüzdeyi yazıyor. Tek bir yazma yolu olması, iki okuyucunun
     * birbirinin kaydını ezmesini önlüyor.
     */
    suspend fun save(
        entryUuid: String,
        chapter: Int? = null,
        paragraph: Int? = null,
        page: Int? = null,
        percent: Int? = null,
    ) {
        if (entryUuid.isBlank()) return
        val existing = dao.get(entryUuid)
        val updated = ReadingProgressEntity(
            entryUuid = entryUuid,
            chapter = chapter ?: existing?.chapter ?: 0,
            paragraph = paragraph ?: existing?.paragraph ?: 0,
            page = page ?: existing?.page ?: 0,
            percent = (percent ?: existing?.percent ?: 0).coerceIn(0, 100),
            updatedAt = now.millis(),
        )
        // Değişen bir şey yoksa günlüğe satır bırakmıyoruz: okurken her
        // sayfa değişiminde yazılıyor ve aynı değeri tekrar tekrar
        // taşımanın anlamı yok.
        if (existing != null &&
            existing.chapter == updated.chapter &&
            existing.paragraph == updated.paragraph &&
            existing.page == updated.page &&
            existing.percent == updated.percent
        ) {
            return
        }

        dao.upsert(updated)
        changeRecorder.record(
            entityType = ChangeEntityType.READING,
            entityUuid = entryUuid,
            operation = ChangeOperation.UPSERT,
            payload = json.encodeToString(ReadingProgressEntity.serializer(), updated),
        )
    }
}
