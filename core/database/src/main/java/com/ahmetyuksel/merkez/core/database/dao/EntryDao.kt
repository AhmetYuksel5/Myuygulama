package com.ahmetyuksel.merkez.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.ahmetyuksel.merkez.core.database.entity.EntryEntity
import com.ahmetyuksel.merkez.core.database.entity.EntryLinkEntity
import com.ahmetyuksel.merkez.core.database.entity.EntryTagCrossRef
import com.ahmetyuksel.merkez.core.database.entity.EntryWithTags
import com.ahmetyuksel.merkez.core.model.EntryType
import kotlinx.coroutines.flow.Flow

@Dao
interface EntryDao {

    @Insert
    suspend fun insert(entry: EntryEntity): Long

    @Update
    suspend fun update(entry: EntryEntity)

    @Upsert
    suspend fun upsert(entry: EntryEntity): Long

    @Delete
    suspend fun delete(entry: EntryEntity)

    @Query("SELECT * FROM entry WHERE id = :id")
    suspend fun getById(id: Long): EntryEntity?

    @Query("SELECT * FROM entry WHERE uuid = :uuid")
    suspend fun getByUuid(uuid: String): EntryEntity?

    /** Gerçek silme yerine mezar taşı bırakır. */
    @Query("UPDATE entry SET deletedAt = :now, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: Long, now: Long)

    @Transaction
    @Query("SELECT * FROM entry WHERE id = :id")
    fun observeWithTags(id: Long): Flow<EntryWithTags?>

    @Transaction
    @Query(
        """
        SELECT * FROM entry
        WHERE type = :type AND archived = 0 AND deletedAt IS NULL
        ORDER BY updatedAt DESC
        """,
    )
    fun observeByType(type: EntryType): Flow<List<EntryWithTags>>

    /**
     * Tüm modüllerin ortak arama girişi: not, makale, alıntı, kelime, görev — hepsi
     * aynı FTS indeksinden gelir.
     */
    @Transaction
    @Query(
        """
        SELECT entry.* FROM entry
        JOIN entry_fts ON entry.id = entry_fts.rowid
        WHERE entry_fts MATCH :query AND entry.archived = 0 AND entry.deletedAt IS NULL
        ORDER BY entry.updatedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun search(query: String, limit: Int): List<EntryWithTags>

    @Transaction
    @Query(
        """
        SELECT entry.* FROM entry
        JOIN entry_tag ON entry.id = entry_tag.entryId
        WHERE entry_tag.tagId = :tagId AND entry.archived = 0 AND entry.deletedAt IS NULL
        ORDER BY entry.updatedAt DESC
        """,
    )
    fun observeByTag(tagId: Long): Flow<List<EntryWithTags>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addTag(crossRef: EntryTagCrossRef)

    @Delete
    suspend fun removeTag(crossRef: EntryTagCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun addLink(link: EntryLinkEntity)

    @Delete
    suspend fun removeLink(link: EntryLinkEntity)

    /** Bu kayda atıf veren kayıtlar (backlink). */
    @Transaction
    @Query(
        """
        SELECT entry.* FROM entry
        JOIN entry_link ON entry.id = entry_link.fromEntryId
        WHERE entry_link.toEntryId = :entryId AND entry.deletedAt IS NULL
        ORDER BY entry.updatedAt DESC
        """,
    )
    fun observeBacklinks(entryId: Long): Flow<List<EntryWithTags>>
}
