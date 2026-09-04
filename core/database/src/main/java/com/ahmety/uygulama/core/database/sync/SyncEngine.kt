package com.ahmety.uygulama.core.database.sync

import com.ahmety.uygulama.core.database.dao.ChangeLogDao
import com.ahmety.uygulama.core.database.dao.SyncStateDao
import com.ahmety.uygulama.core.database.entity.ChangeLogEntity
import com.ahmety.uygulama.core.database.entity.SyncStateEntity
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/** Tek bir senkron turunun sonucu; ayarlar ekranında gösteriliyor. */
data class SyncOutcome(
    val exportedChanges: Int = 0,
    val importedChanges: Int = 0,
    val skippedChanges: Int = 0,
    val error: SyncError? = null,
) {
    val isSuccess: Boolean get() = error == null
}

enum class SyncError { NO_FOLDER, NO_KEY, DECRYPT_FAILED, WRITE_FAILED }

/** Paylaşılan alana yazılan tek bir değişiklik satırı. */
@Serializable
internal data class SyncRecord(
    val opId: String,
    val seq: Long,
    val entityType: String,
    val entityUuid: String,
    val operation: String,
    val payload: String,
    val createdAt: Long,
)

/**
 * Senkron turu: önce kendi değişikliklerimizi dışa yaz, sonra diğer cihazların
 * yazdıklarını içeri al.
 *
 * Dışa yazma önce geliyor; böylece iki cihaz aynı anda senkronlanırsa en fazla
 * bir tur gecikme olur, veri kaybı olmaz.
 */
@Singleton
class SyncEngine @Inject constructor(
    private val changeLogDao: ChangeLogDao,
    private val syncStateDao: SyncStateDao,
    private val applier: ChangeApplier,
    private val crypto: SyncCrypto,
    private val transport: ActiveTransport,
    private val json: Json,
    @DeviceId private val deviceId: String,
    private val now: Now,
) {

    suspend fun sync(): SyncOutcome {
        if (!transport.isReady()) return SyncOutcome(error = SyncError.NO_FOLDER)
        if (!crypto.hasKey()) return SyncOutcome(error = SyncError.NO_KEY)

        val exported = export()
        if (exported < 0) return SyncOutcome(error = SyncError.WRITE_FAILED)

        // Ağ yolunda kendi dosyalarımızı karşı tarafa burada gönderiyoruz;
        // klasör yolunda yapacak bir şey yok.
        transport.publish()

        val imported = import()
        return SyncOutcome(
            exportedChanges = exported,
            importedChanges = imported.applied,
            skippedChanges = imported.skipped,
            error = imported.error,
        )
    }

    /** @return yazılan değişiklik sayısı, hata hâlinde -1 */
    private suspend fun export(): Int {
        var total = 0
        while (true) {
            val batch = changeLogDao.pendingForExport(deviceId, BATCH_SIZE)
            if (batch.isEmpty()) return total

            val payload = batch.joinToString("\n") { change ->
                json.encodeToString(SyncRecord.serializer(), change.toRecord())
            }.toByteArray()

            val fileName = fileNameFor(batch.first().seq, batch.last().seq)
            val written = transport.write(deviceId, fileName, crypto.encrypt(payload))
            if (!written) return -1

            changeLogDao.markExported(batch.map { it.id })
            total += batch.size
            // Toplu iş tam doluysa devam eden veri var demektir.
            if (batch.size < BATCH_SIZE) return total
        }
    }

    private data class ImportOutcome(
        val applied: Int = 0,
        val skipped: Int = 0,
        val error: SyncError? = null,
    )

    private suspend fun import(): ImportOutcome {
        var applied = 0
        var skipped = 0
        var decryptFailed = false

        transport.deviceFolders()
            .filter { it != deviceId } // Kendi yazdığımızı geri okumaya gerek yok.
            .forEach { remoteDevice ->
                val lastApplied = syncStateDao.lastAppliedSeq(remoteDevice) ?: 0L
                var highestSeq = lastApplied

                val files = transport.fileNames(remoteDevice)
                    .mapNotNull { name -> parseRange(name)?.let { it to name } }
                    // Tamamı zaten uygulanmış dosyaları hiç indirmiyoruz.
                    .filter { (range, _) -> range.second > lastApplied }
                    .sortedBy { (range, _) -> range.first }

                files.forEach { (range, fileName) ->
                    val bytes = transport.read(remoteDevice, fileName) ?: return@forEach
                    val plain = crypto.decrypt(bytes)
                    if (plain == null) {
                        decryptFailed = true
                        return@forEach
                    }

                    plain.decodeToString().lineSequence()
                        .filter { it.isNotBlank() }
                        .forEach { line ->
                            val record = runCatching {
                                json.decodeFromString(SyncRecord.serializer(), line)
                            }.getOrNull() ?: return@forEach

                            if (record.seq <= lastApplied) return@forEach
                            if (applier.apply(record.entityType, record.payload)) {
                                applied++
                            } else {
                                skipped++
                            }
                            if (record.seq > highestSeq) highestSeq = record.seq
                        }
                    if (range.second > highestSeq) highestSeq = range.second
                }

                if (highestSeq > lastApplied) {
                    syncStateDao.upsert(
                        SyncStateEntity(
                            deviceId = remoteDevice,
                            lastAppliedSeq = highestSeq,
                            updatedAt = now.millis(),
                        ),
                    )
                }
            }

        return ImportOutcome(
            applied = applied,
            skipped = skipped,
            error = if (decryptFailed && applied == 0) SyncError.DECRYPT_FAILED else null,
        )
    }

    private fun ChangeLogEntity.toRecord() = SyncRecord(
        opId = opId,
        seq = seq,
        entityType = entityType,
        entityUuid = entityUuid,
        operation = operation,
        payload = payload,
        createdAt = createdAt,
    )

    private companion object {
        const val BATCH_SIZE = 500

        /**
         * Dosya adı sıra aralığını taşır: `000000001-000000500.bin`.
         * Sabit genişlikte yazıyoruz ki isme göre sıralama sayıya göre sıralama olsun.
         */
        fun fileNameFor(startSeq: Long, endSeq: Long): String =
            "%09d-%09d.bin".format(startSeq, endSeq)

        fun parseRange(fileName: String): Pair<Long, Long>? {
            val name = fileName.removeSuffix(".bin")
            val parts = name.split("-")
            if (parts.size != 2) return null
            val start = parts[0].toLongOrNull() ?: return null
            val end = parts[1].toLongOrNull() ?: return null
            return start to end
        }
    }
}
