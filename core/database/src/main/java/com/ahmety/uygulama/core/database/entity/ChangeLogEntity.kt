package com.ahmety.uygulama.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Senkronizasyonun temeli: bu cihazda yapılan her yazma işleminin kaydı.
 *
 * Taşıyıcı (Drive / klasör) henüz yokken bile doldurulur — Faz 2'de senkron
 * açıldığında geçmiş de birlikte gider. Sonradan eklenseydi, o güne kadarki
 * veri ikinci cihaza hiç ulaşmazdı.
 *
 * `(deviceId, seq)` benzersizdir; karşı cihaz "bu cihazın kaçıncı sırasına
 * kadar okudum" bilgisini tutarak kaldığı yerden devam eder.
 */
@Entity(
    tableName = "change_log",
    indices = [
        Index(value = ["deviceId", "seq"], unique = true),
        Index("exported"),
        Index("entityUuid"),
    ],
)
data class ChangeLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val opId: String,
    val deviceId: String,
    val seq: Long,
    /** habit, habit_check, entry, task … */
    val entityType: String,
    val entityUuid: String,
    /** UPSERT | DELETE */
    val operation: String,
    /** Kaydın yeni hâli, JSON. */
    val payload: String,
    val createdAt: Long,
    /** Paylaşılan alana yazıldı mı. */
    val exported: Boolean = false,
)

object ChangeOperation {
    const val UPSERT = "UPSERT"
    const val DELETE = "DELETE"
}

object ChangeEntityType {
    const val HABIT = "habit"
    const val HABIT_CHECK = "habit_check"
    const val ENTRY = "entry"
    const val TAG = "tag"
    const val VOCAB = "vocab_progress"
}
