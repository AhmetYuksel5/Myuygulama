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
    indices = [Index("status"), Index("deletedAt"), Index("dueAt")],
)
data class VocabProgressEntity(
    @PrimaryKey val word: String,
    /** NEW | KNOWN | LEARNING | IGNORED (eski kayıtlarda UNSURE de olabilir) */
    val status: String,
    val updatedAt: Long,
    val deletedAt: Long? = null,

    /** Tekrar merdiveninin kademesi. 0 = henüz programa girmedi. */
    val box: Int = 0,

    /** Bir sonraki tekrar zamanı; null ise programda değil (öğrenildi/önemsiz). */
    val dueAt: Long? = null,

    val lastReviewedAt: Long? = null,

    /** Kuyruğa ilk giriş — kullanıcının deyimiyle "ekleme tarihi". */
    val introducedAt: Long? = null,

    val reviewCount: Int = 0,

    /** Üst üste geçildiği için kademesi düşürülme sayısı. */
    val lapseCount: Int = 0,

    /** Art arda kaç kez "şimdilik geç" denildi; çalışılınca sıfırlanıyor. */
    val postponeCount: Int = 0,

    /** Kartın anlamı kaç kez açıldı — hatırlayamama göstergesi. */
    val revealCount: Int = 0,
)
