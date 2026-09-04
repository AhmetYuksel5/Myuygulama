package com.ahmety.uygulama.core.database.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Senkronun hangi yoldan gideceği. */
enum class SyncMode {
    /** Bir klasör; onu iki telefon arasında taşımak başka bir aracın işi. */
    FOLDER,

    /** Aynı ağdaki ikinci telefonla doğrudan. */
    LAN,

    /** GitHub'daki özel bir depo üzerinden; mobil veriyle de çalışıyor. */
    GITHUB,
}

/**
 * Seçili taşıyıcıya yönlendiren ince katman.
 *
 * Senkron motoru hangi yolun kullanıldığını bilmiyor; veri biçimi ikisinde
 * de aynı olduğu için yolu değiştirmek veriyi bozmuyor.
 */
@Singleton
class ActiveTransport @Inject constructor(
    @ApplicationContext context: Context,
    private val folder: DocumentTreeTransport,
    private val lan: LanTransport,
    private val github: GitHubTransport,
) : SyncTransport {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var mode: SyncMode
        get() = runCatching { SyncMode.valueOf(prefs.getString(KEY_MODE, null) ?: "") }
            .getOrDefault(SyncMode.LAN)
        set(value) = prefs.edit().putString(KEY_MODE, value.name).apply()

    private val current: SyncTransport
        get() = when (mode) {
            SyncMode.LAN -> lan
            SyncMode.GITHUB -> github
            SyncMode.FOLDER -> folder
        }

    override suspend fun isReady(): Boolean = current.isReady()

    override suspend fun deviceFolders(): List<String> = current.deviceFolders()

    override suspend fun fileNames(deviceFolder: String): List<String> =
        current.fileNames(deviceFolder)

    override suspend fun read(deviceFolder: String, fileName: String): ByteArray? =
        current.read(deviceFolder, fileName)

    override suspend fun write(
        deviceFolder: String,
        fileName: String,
        bytes: ByteArray,
    ): Boolean = current.write(deviceFolder, fileName, bytes)

    override suspend fun publish() = current.publish()

    private companion object {
        const val PREFS_NAME = "merkez_senkron_yol"
        const val KEY_MODE = "mode"
    }
}
