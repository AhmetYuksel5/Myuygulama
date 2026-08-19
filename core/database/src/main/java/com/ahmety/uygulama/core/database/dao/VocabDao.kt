package com.ahmety.uygulama.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.ahmety.uygulama.core.database.entity.VocabProgressEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VocabDao {

    @Upsert
    suspend fun upsert(progress: VocabProgressEntity)

    @Query("SELECT * FROM vocab_progress WHERE deletedAt IS NULL")
    fun observeAll(): Flow<List<VocabProgressEntity>>

    /** Silinmiş satır yok sayılıyor: silinen kelime yeniden işaretlenirse sıfırdan başlamalı. */
    @Query("SELECT * FROM vocab_progress WHERE word = :word AND deletedAt IS NULL LIMIT 1")
    suspend fun get(word: String): VocabProgressEntity?

    /**
     * Silinmişler dâhil. Yalnızca senkron için: karşı cihazdan gelen eski bir
     * kayıt, burada silinmiş satırı diriltmemeli.
     */
    @Query("SELECT * FROM vocab_progress WHERE word = :word LIMIT 1")
    suspend fun getIncludingDeleted(word: String): VocabProgressEntity?

    @Query("SELECT COUNT(*) FROM vocab_progress WHERE status = :status AND deletedAt IS NULL")
    fun observeCountByStatus(status: String): Flow<Int>
}
