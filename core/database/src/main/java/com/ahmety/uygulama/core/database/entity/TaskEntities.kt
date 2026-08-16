package com.ahmety.uygulama.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity(
    tableName = "task_list",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index("position"),
        Index("deletedAt"),
    ],
)
data class TaskListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val name: String,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)

@Serializable
@Entity(
    tableName = "task",
    indices = [
        Index(value = ["uuid"], unique = true),
        Index("listUuid"),
        Index("parentUuid"),
        Index("dueDate"),
        Index("completedAt"),
        Index("deletedAt"),
    ],
)
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uuid: String,
    val listUuid: String,
    val title: String,
    val notes: String = "",
    /** Epoch gün sayısı; null ise tarihsiz. */
    val dueDate: Int? = null,
    val dueMinuteOfDay: Int? = null,
    val completedAt: Long? = null,
    /** NONE | LOW | NORMAL | HIGH */
    val priority: String = "NONE",
    val parentUuid: String? = null,
    /** DAY | WEEK | MONTH | YEAR; null ise tekrar yok. */
    val recurrenceUnit: String? = null,
    val recurrenceInterval: Int = 1,
    val recurrenceDaysMask: Int = 0,
    val recurrenceFromCompletion: Boolean = false,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
)
