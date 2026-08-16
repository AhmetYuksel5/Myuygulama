package com.ahmety.uygulama.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.LinkRelation

@Serializable
@Entity(
    tableName = "entry",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index("type"),
        Index("updatedAt"),
        Index("archived"),
        Index("deletedAt"),
    ],
)
data class EntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Cihazdan bağımsız kimlik; yedek/geri yükleme ve olası senkron için. */
    val uuid: String,
    val type: EntryType,
    val title: String,
    val body: String = "",
    val source: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val archived: Boolean = false,
    /** Mezar taşı: dolu ise kayıt silinmiş sayılır ama satır yerinde durur. */
    val deletedAt: Long? = null,
)

/**
 * `entry` tablosunun tam metin arama gölgesi (external content FTS).
 * Room, `entry` üzerindeki değişiklikleri buraya taşıyan tetikleyicileri kendisi üretir;
 * bu yüzden ayrıca senkronize etmemiz gerekmiyor.
 */
@Entity(tableName = "entry_fts")
@Fts4(contentEntity = EntryEntity::class)
data class EntryFtsEntity(
    val title: String,
    val body: String,
)

@Serializable
@Entity(
    tableName = "tag",
    indices = [
        Index(value = ["name"], unique = true),
        Index(value = ["uuid"], unique = true),
    ],
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val name: String,
    val color: Int? = null,
)

@Entity(
    tableName = "entry_tag",
    primaryKeys = ["entryId", "tagId"],
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TagEntity::class,
            parentColumns = ["id"],
            childColumns = ["tagId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("tagId")],
)
data class EntryTagCrossRef(
    val entryId: Long,
    val tagId: Long,
)

@Entity(
    tableName = "entry_link",
    foreignKeys = [
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["fromEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = EntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["toEntryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["fromEntryId", "toEntryId", "relation"], unique = true),
        Index("toEntryId"),
    ],
)
data class EntryLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val fromEntryId: Long,
    val toEntryId: Long,
    @ColumnInfo(defaultValue = "REFERENCES") val relation: LinkRelation = LinkRelation.REFERENCES,
)
