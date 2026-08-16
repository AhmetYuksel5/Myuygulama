package com.ahmety.uygulama.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ahmety.uygulama.core.database.entity.SyncStateEntity

@Dao
interface SyncStateDao {

    @Upsert
    suspend fun upsert(state: SyncStateEntity)

    @Query("SELECT lastAppliedSeq FROM sync_state WHERE deviceId = :deviceId")
    suspend fun lastAppliedSeq(deviceId: String): Long?

    @Query("SELECT * FROM sync_state")
    suspend fun all(): List<SyncStateEntity>
}
