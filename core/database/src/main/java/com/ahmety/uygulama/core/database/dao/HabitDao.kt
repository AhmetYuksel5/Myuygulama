package com.ahmety.uygulama.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ahmety.uygulama.core.database.entity.HabitCheckEntity
import com.ahmety.uygulama.core.database.entity.HabitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HabitDao {

    @Upsert
    suspend fun upsert(habit: HabitEntity): Long

    @Query("SELECT * FROM habit WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): HabitEntity?

    @Query(
        """
        SELECT * FROM habit
        WHERE deletedAt IS NULL AND archived = 0
        ORDER BY position ASC, createdAt ASC
        """,
    )
    fun observeActive(): Flow<List<HabitEntity>>

    @Query(
        """
        SELECT * FROM habit
        WHERE deletedAt IS NULL AND archived = 1
        ORDER BY position ASC, createdAt ASC
        """,
    )
    fun observeArchived(): Flow<List<HabitEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM habit WHERE deletedAt IS NULL")
    suspend fun nextPosition(): Int

    @Query("UPDATE habit SET deletedAt = :now, updatedAt = :now WHERE uuid = :uuid")
    suspend fun softDelete(uuid: String, now: Long)

    @Upsert
    suspend fun upsertCheck(check: HabitCheckEntity)

    @Query(
        "SELECT * FROM habit_check WHERE habitUuid = :habitUuid AND date = :date LIMIT 1",
    )
    suspend fun getCheck(habitUuid: String, date: Int): HabitCheckEntity?

    @Query(
        """
        SELECT * FROM habit_check
        WHERE deletedAt IS NULL AND date BETWEEN :from AND :to
        """,
    )
    fun observeChecksBetween(from: Int, to: Int): Flow<List<HabitCheckEntity>>

    @Query(
        """
        SELECT * FROM habit_check
        WHERE habitUuid = :habitUuid AND deletedAt IS NULL AND count > 0
        ORDER BY date ASC
        """,
    )
    fun observeChecksForHabit(habitUuid: String): Flow<List<HabitCheckEntity>>
}
