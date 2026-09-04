package com.ahmety.uygulama.core.database.sync

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Senkronun taşıyıcıdan bağımsız yüzü.
 *
 * Tasarım gereği her cihaz **yalnızca kendi klasörüne** yazar ve dosyalar bir
 * kez yazıldıktan sonra değişmez. Bu yüzden taşıyıcının çakışma çözmesi
 * gerekmez — Drive, Syncthing, Nextcloud, hepsi aynı şekilde çalışır.
 */
interface SyncTransport {
    suspend fun isReady(): Boolean

    /** `sync/` altındaki cihaz klasörlerinin adları. */
    suspend fun deviceFolders(): List<String>

    /** Bir cihaz klasöründeki dosya adları. */
    suspend fun fileNames(deviceFolder: String): List<String>

    suspend fun read(deviceFolder: String, fileName: String): ByteArray?

    suspend fun write(deviceFolder: String, fileName: String, bytes: ByteArray): Boolean

    /**
     * Dışa yazma bittikten sonra çağrılır.
     *
     * Paylaşılan klasörde yapacak bir şey yok — dosyalar zaten ortak yere
     * yazıldı. Ağ üzerinden konuşan taşıyıcı burada kendi dosyalarını
     * karşı tarafa gönderiyor.
     */
    suspend fun publish() = Unit
}

/**
 * Kullanıcının Android klasör seçicisiyle (SAF) gösterdiği bir klasöre yazar.
 * Bu klasörü Syncthing, FolderSync ya da başka bir araçla eşleyebilirsin;
 * uygulamanın umurunda değil.
 */
@Singleton
class DocumentTreeTransport @Inject constructor(
    @ApplicationContext private val context: Context,
) : SyncTransport {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun folderUri(): Uri? = prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    /**
     * Seçilen klasörü kalıcı olarak hatırlar. Kalıcı izin alınmazsa uygulama
     * yeniden başladığında klasöre erişemez, bu yüzden burada alıyoruz.
     */
    fun setFolder(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(KEY_TREE_URI, uri.toString()).apply()
        true
    }.getOrDefault(false)

    fun clearFolder() {
        prefs.edit().remove(KEY_TREE_URI).apply()
    }

    override suspend fun isReady(): Boolean = withContext(Dispatchers.IO) {
        root() != null
    }

    override suspend fun deviceFolders(): List<String> = withContext(Dispatchers.IO) {
        syncRoot(create = false)?.listFiles()
            ?.filter { it.isDirectory }
            ?.mapNotNull { it.name }
            .orEmpty()
    }

    override suspend fun fileNames(deviceFolder: String): List<String> =
        withContext(Dispatchers.IO) {
            syncRoot(create = false)?.findFile(deviceFolder)?.listFiles()
                ?.filter { it.isFile }
                ?.mapNotNull { it.name }
                .orEmpty()
        }

    override suspend fun read(deviceFolder: String, fileName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            val file = syncRoot(create = false)?.findFile(deviceFolder)?.findFile(fileName)
                ?: return@withContext null
            runCatching {
                context.contentResolver.openInputStream(file.uri)?.use { it.readBytes() }
            }.getOrNull()
        }

    override suspend fun write(
        deviceFolder: String,
        fileName: String,
        bytes: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) {
        val syncDir = syncRoot(create = true) ?: return@withContext false
        val deviceDir = syncDir.findFile(deviceFolder)
            ?: syncDir.createDirectory(deviceFolder)
            ?: return@withContext false
        // Aynı adlı dosya varsa üzerine yazmıyoruz: dosyalar değişmez kabul edilir.
        if (deviceDir.findFile(fileName) != null) return@withContext true

        val file = deviceDir.createFile(MIME_TYPE, fileName) ?: return@withContext false
        runCatching {
            context.contentResolver.openOutputStream(file.uri)?.use { it.write(bytes) }
            true
        }.getOrDefault(false)
    }

    private fun root(): DocumentFile? =
        folderUri()?.let { DocumentFile.fromTreeUri(context, it) }?.takeIf { it.canRead() }

    private fun syncRoot(create: Boolean): DocumentFile? {
        val root = root() ?: return null
        root.findFile(SYNC_DIR)?.let { return it }
        return if (create) root.createDirectory(SYNC_DIR) else null
    }

    private companion object {
        const val PREFS_NAME = "merkez_senkron"
        const val KEY_TREE_URI = "tree_uri"
        const val SYNC_DIR = "sync"
        const val MIME_TYPE = "application/octet-stream"
    }
}
