package com.ahmety.uygulama.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.ahmety.uygulama.core.database.entity.ChangeLogEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ChangeLogDao {

    @Insert
    suspend fun insert(change: ChangeLogEntity): Long

    @Query("SELECT COALESCE(MAX(seq), 0) + 1 FROM change_log WHERE deviceId = :deviceId")
    suspend fun nextSeq(deviceId: String): Long

    @Query(
        """
        SELECT * FROM change_log
        WHERE deviceId = :deviceId AND exported = 0
        ORDER BY seq ASC
        LIMIT :limit
        """,
    )
    suspend fun pendingForExport(deviceId: String, limit: Int): List<ChangeLogEntity>

    @Query("UPDATE change_log SET exported = 1 WHERE id IN (:ids)")
    suspend fun markExported(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM change_log WHERE exported = 0")
    fun observePendingCount(): Flow<Int>
}
