package com.ahmety.uygulama.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ahmety.uygulama.core.database.entity.TaskEntity
import com.ahmety.uygulama.core.database.entity.TaskListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {

    // --- Listeler ---

    @Upsert
    suspend fun upsertList(list: TaskListEntity): Long

    @Query("SELECT * FROM task_list WHERE deletedAt IS NULL ORDER BY position ASC, createdAt ASC")
    fun observeLists(): Flow<List<TaskListEntity>>

    @Query("SELECT * FROM task_list WHERE deletedAt IS NULL ORDER BY position ASC, createdAt ASC")
    suspend fun getLists(): List<TaskListEntity>

    @Query("SELECT * FROM task_list WHERE uuid = :uuid LIMIT 1")
    suspend fun getListByUuid(uuid: String): TaskListEntity?

    @Query("SELECT * FROM task_list WHERE name = :name AND deletedAt IS NULL LIMIT 1")
    suspend fun getListByName(name: String): TaskListEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM task_list WHERE deletedAt IS NULL")
    suspend fun nextListPosition(): Int

    // --- Görevler ---

    @Upsert
    suspend fun upsert(task: TaskEntity): Long

    @Query("SELECT * FROM task WHERE uuid = :uuid LIMIT 1")
    suspend fun getByUuid(uuid: String): TaskEntity?

    @Query(
        """
        SELECT * FROM task
        WHERE listUuid = :listUuid AND deletedAt IS NULL AND parentUuid IS NULL
        ORDER BY completedAt IS NOT NULL, position ASC, createdAt ASC
        """,
    )
    fun observeByList(listUuid: String): Flow<List<TaskEntity>>

    @Query("SELECT * FROM task WHERE deletedAt IS NULL AND parentUuid = :parentUuid ORDER BY position ASC")
    fun observeSubtasks(parentUuid: String): Flow<List<TaskEntity>>

    /**
     * "Bugün" listesi: bugüne ait veya geçmiş tarihli, tamamlanmamış görevler.
     * Geçmiş tarihliler de dahil, çünkü dün yapmadığın iş bugün de duruyor.
     */
    @Query(
        """
        SELECT * FROM task
        WHERE deletedAt IS NULL
          AND completedAt IS NULL
          AND dueDate IS NOT NULL
          AND dueDate <= :today
        ORDER BY dueDate ASC, dueMinuteOfDay IS NULL, dueMinuteOfDay ASC, position ASC
        """,
    )
    fun observeDueThrough(today: Int): Flow<List<TaskEntity>>

    /** Bugün tamamlananlar; "bugün ne yaptım" görünümü için. */
    @Query(
        """
        SELECT * FROM task
        WHERE deletedAt IS NULL AND completedAt IS NOT NULL
          AND dueDate = :today
        ORDER BY position ASC
        """,
    )
    fun observeCompletedOn(today: Int): Flow<List<TaskEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM task WHERE listUuid = :listUuid AND deletedAt IS NULL")
    suspend fun nextPosition(listUuid: String): Int

    @Query("SELECT COUNT(*) FROM task WHERE deletedAt IS NULL")
    suspend fun count(): Int
}
