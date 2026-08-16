package com.ahmety.uygulama.core.database.sync

import com.ahmety.uygulama.core.database.dao.ChangeLogDao
import com.ahmety.uygulama.core.database.entity.ChangeLogEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DeviceId

/** Şu anki zaman. Ayrı bir tip olması testte zamanı sabitlemeyi mümkün kılıyor. */
fun interface Now {
    fun millis(): Long
}

/**
 * Her yazma işleminin değişiklik günlüğüne satır bırakmasını sağlar.
 *
 * Sıra numarası (`seq`) cihaz başına monoton artmak zorunda; eşzamanlı iki
 * yazma aynı numarayı almasın diye tek bir kilit üzerinden geçiyor.
 */
@Singleton
class ChangeRecorder @Inject constructor(
    private val changeLogDao: ChangeLogDao,
    @DeviceId private val deviceId: String,
    private val now: Now,
) {
    private val seqLock = Mutex()

    suspend fun record(
        entityType: String,
        entityUuid: String,
        operation: String,
        payload: String,
    ) {
        seqLock.withLock {
            val seq = changeLogDao.nextSeq(deviceId)
            changeLogDao.insert(
                ChangeLogEntity(
                    opId = UUID.randomUUID().toString(),
                    deviceId = deviceId,
                    seq = seq,
                    entityType = entityType,
                    entityUuid = entityUuid,
                    operation = operation,
                    payload = payload,
                    createdAt = now.millis(),
                ),
            )
        }
    }
}
