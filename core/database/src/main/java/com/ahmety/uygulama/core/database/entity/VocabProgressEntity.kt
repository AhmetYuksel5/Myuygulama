package com.ahmety.uygulama.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

/**
 * Bir kelime hakkındaki kullanıcı kararı. Kelime listesinin kendisi asset'te
 * sabit; burada sadece "biliyorum / bilmiyorum" durumu tutuluyor, o yüzden
 * senkronda taşınan veri küçük.
 *
 * Birincil anahtar kelimenin kendisi (benzersiz ve kalıcı).
 */
@Serializable
@Entity(
    tableName = "vocab_progress",
    indices = [Index("status"), Index("deletedAt")],
)
data class VocabProgressEntity(
    @PrimaryKey val word: String,
    /** NEW | KNOWN | LEARNING */
    val status: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
