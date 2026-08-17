package com.ahmety.uygulama.core.database.repository

import com.ahmety.uygulama.core.database.dao.EntryDao
import com.ahmety.uygulama.core.database.dao.TagDao
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.EntryEntity
import com.ahmety.uygulama.core.database.entity.EntryTagCrossRef
import com.ahmety.uygulama.core.database.entity.EntryWithTags
import com.ahmety.uygulama.core.database.entity.TagEntity
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.SearchQuery
import com.ahmety.uygulama.core.model.Tag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val SEARCH_LIMIT = 200

/**
 * Not, makale, doküman, alıntı, kelime — hepsi aynı `entry` tablosunda yaşadığı
 * için hepsinin yazma yolu da burası. Tek arama kutusu, tek etiket sistemi ve
 * kayıtlar arası bağlantılar bu ortaklıktan geliyor.
 */
@Singleton
class EntryRepository @Inject constructor(
    private val entryDao: EntryDao,
    private val tagDao: TagDao,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    fun observeByType(type: EntryType): Flow<List<Entry>> =
        entryDao.observeByType(type).map { rows -> rows.map(EntryWithTags::toDomain) }

    fun observeByTag(tagId: Long): Flow<List<Entry>> =
        entryDao.observeByTag(tagId).map { rows -> rows.map(EntryWithTags::toDomain) }

    fun observeTags(): Flow<List<Tag>> =
        tagDao.observeAll().map { rows -> rows.map(TagEntity::toDomain) }

    fun observeBacklinks(entryId: Long): Flow<List<Entry>> =
        entryDao.observeBacklinks(entryId).map { rows -> rows.map(EntryWithTags::toDomain) }

    suspend fun getById(id: Long): Entry? = entryDao.getById(id)?.toDomain(emptyList())

    /**
     * Tüm modüllerde tek seferde arama. Boş/anlamsız sorguda sonuç döndürmüyoruz;
     * FTS'e ham metin verilseydi sözdizimi hatası alırdık.
     */
    suspend fun search(raw: String): List<Entry> {
        val query = SearchQuery.toFtsQuery(raw) ?: return emptyList()
        return runCatching { entryDao.search(query, SEARCH_LIMIT) }
            .getOrDefault(emptyList())
            .map(EntryWithTags::toDomain)
    }

    suspend fun createEntry(
        type: EntryType,
        title: String,
        body: String = "",
        source: String? = null,
    ): Long {
        val timestamp = now.millis()
        val entity = EntryEntity(
            uuid = UUID.randomUUID().toString(),
            type = type,
            title = title.trim(),
            body = body,
            source = source,
            createdAt = timestamp,
            updatedAt = timestamp,
        )
        val id = entryDao.insert(entity)
        record(entity.copy(id = id), ChangeOperation.UPSERT)
        return id
    }

    suspend fun updateEntry(id: Long, title: String, body: String) {
        val existing = entryDao.getById(id) ?: return
        val updated = existing.copy(
            title = title.trim(),
            body = body,
            updatedAt = now.millis(),
        )
        entryDao.update(updated)
        record(updated, ChangeOperation.UPSERT)
    }

    /**
     * Kaydın serbest metin alanını günceller. Notlarda renk/sabitleme gibi
     * görsel bilgiler burada duruyor; ayrı sütun açmadığımız için veri göçü
     * gerekmiyor ve değişiklik günlüğü bunu kendiliğinden taşıyor.
     */
    suspend fun updateSource(id: Long, source: String?) {
        val existing = entryDao.getById(id) ?: return
        val updated = existing.copy(source = source, updatedAt = now.millis())
        entryDao.update(updated)
        record(updated, ChangeOperation.UPSERT)
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        val existing = entryDao.getById(id) ?: return
        val updated = existing.copy(archived = archived, updatedAt = now.millis())
        entryDao.update(updated)
        record(updated, ChangeOperation.UPSERT)
    }

    suspend fun deleteEntry(id: Long) {
        val existing = entryDao.getById(id) ?: return
        val timestamp = now.millis()
        entryDao.softDelete(id, timestamp)
        record(
            existing.copy(deletedAt = timestamp, updatedAt = timestamp),
            ChangeOperation.DELETE,
        )
    }

    /** Etiketi adıyla bulur, yoksa oluşturur. */
    suspend fun ensureTag(name: String): Tag {
        val trimmed = name.trim()
        tagDao.findByName(trimmed)?.let { return it.toDomain() }

        val entity = TagEntity(uuid = UUID.randomUUID().toString(), name = trimmed)
        val id = tagDao.insert(entity)
        val stored = entity.copy(id = if (id > 0) id else 0L)
        changeRecorder.record(
            entityType = ChangeEntityType.TAG,
            entityUuid = stored.uuid,
            operation = ChangeOperation.UPSERT,
            payload = json.encodeToString(TagEntity.serializer(), stored),
        )
        // insert IGNORE ile çakışma olduysa mevcut satırı geri okuyoruz.
        return tagDao.findByName(trimmed)?.toDomain() ?: stored.toDomain()
    }

    suspend fun addTag(entryId: Long, tagName: String) {
        val tag = ensureTag(tagName)
        entryDao.addTag(EntryTagCrossRef(entryId = entryId, tagId = tag.id))
        touch(entryId)
    }

    suspend fun removeTag(entryId: Long, tagId: Long) {
        entryDao.removeTag(EntryTagCrossRef(entryId = entryId, tagId = tagId))
        touch(entryId)
    }

    /**
     * Etiket bağı ayrı bir tabloda tuttuğu için kaydın kendisi değişmiyor;
     * yine de `updatedAt` güncelliyoruz ki liste sıralaması ve senkron
     * değişikliği fark etsin.
     */
    private suspend fun touch(entryId: Long) {
        val existing = entryDao.getById(entryId) ?: return
        val updated = existing.copy(updatedAt = now.millis())
        entryDao.update(updated)
        record(updated, ChangeOperation.UPSERT)
    }

    private suspend fun record(entity: EntryEntity, operation: String) {
        changeRecorder.record(
            entityType = ChangeEntityType.ENTRY,
            entityUuid = entity.uuid,
            operation = operation,
            payload = json.encodeToString(EntryEntity.serializer(), entity),
        )
    }
}

internal fun TagEntity.toDomain(): Tag = Tag(id = id, uuid = uuid, name = name, color = color)

internal fun EntryEntity.toDomain(tags: List<Tag>): Entry = Entry(
    id = id,
    uuid = uuid,
    type = type,
    title = title,
    body = body,
    source = source,
    createdAt = createdAt,
    updatedAt = updatedAt,
    archived = archived,
    deletedAt = deletedAt,
    tags = tags,
)

internal fun EntryWithTags.toDomain(): Entry = entry.toDomain(tags.map(TagEntity::toDomain))
