package com.ahmety.uygulama.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Karşı cihazın günlüğünde nereye kadar okuduğumuz.
 *
 * Bu satır olmadan her senkronda tüm geçmişi baştan uygulamak gerekirdi;
 * sonuç yine doğru olurdu (işlemler idempotent) ama gereksiz yere yavaş olurdu.
 */
@Entity(tableName = "sync_state")
data class SyncStateEntity(
    @PrimaryKey val deviceId: String,
    val lastAppliedSeq: Long,
    val updatedAt: Long,
)
