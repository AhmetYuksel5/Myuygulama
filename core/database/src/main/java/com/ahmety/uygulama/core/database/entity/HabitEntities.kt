package com.ahmety.uygulama.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "habit",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index("position"),
        Index("deletedAt"),
    ],
)
data class HabitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val name: String,
    val description: String = "",
    /** DAILY | SPECIFIC_DAYS | TIMES_PER_WEEK */
    val scheduleType: String,
    /** SPECIFIC_DAYS için bit maskesi: bit 0 = Pazartesi … bit 6 = Pazar. */
    val scheduleDaysMask: Int = 0,
    /** TIMES_PER_WEEK için haftalık hedef. */
    val scheduleTimesPerWeek: Int = 0,
    val targetPerDay: Int = 1,
    val colorArgb: Int? = null,
    val reminderMinuteOfDay: Int? = null,
    val position: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

/**
 * Bir alışkanlığın belirli bir gündeki sayacı.
 *
 * Birincil anahtar `(habitUuid, date)` — yani aynı gün için ikinci bir satır
 * oluşamaz. Bu, iki cihaz senkronunda çakışmayı kendiliğinden çözer:
 * hangi telefondan işaretlersen işaretle aynı satıra denk gelir.
 */
@Serializable
@Entity(
    tableName = "habit_check",
    primaryKeys = ["habitUuid", "date"],
    indices = [Index("date"), Index("habitUuid")],
)
data class HabitCheckEntity(
    val habitUuid: String,
    /** Epoch gün sayısı (1970-01-01 = 0). Saat dilimi taşımaz. */
    val date: Int,
    val count: Int,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
