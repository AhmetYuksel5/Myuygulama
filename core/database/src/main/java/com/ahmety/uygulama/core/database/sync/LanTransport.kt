package com.ahmety.uygulama.core.database.sync

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cihazın kendi değişiklik günlüğü; uygulamanın özel alanında.
 *
 * Paylaşılan klasör yolunda bu dosyalar kullanıcının seçtiği klasöre
 * yazılıyordu. Ağ yolunda ortada bir klasör yok: her cihaz kendi
 * dosyalarını kendinde tutuyor ve karşı tarafa doğrudan veriyor.
 */
@Singleton
class LocalStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val root = File(context.filesDir, "senkron")

    fun folders(): List<String> =
        root.listFiles()?.filter { it.isDirectory }?.map { it.name }.orEmpty()

    fun files(folder: String): List<String> {
        if (!safe(folder)) return emptyList()
        return File(root, folder).listFiles()?.filter { it.isFile }?.map { it.name }.orEmpty()
    }

    fun read(folder: String, name: String): ByteArray? {
        if (!safe(folder) || !safe(name)) return null
        val file = File(File(root, folder), name)
        return if (file.isFile) runCatching { file.readBytes() }.getOrNull() else null
    }

    fun write(folder: String, name: String, bytes: ByteArray): Boolean {
        if (!safe(folder) || !safe(name)) return false
        val directory = File(root, folder)
        if (!directory.isDirectory && !directory.mkdirs()) return false
        val file = File(directory, name)
        // Dosyalar bir kez yazıldıktan sonra değişmiyor; aynı ad geldiyse
        // aynı içerik demek ve üzerine yazmanın anlamı yok.
        if (file.isFile) return true
        return runCatching { file.writeBytes(bytes); true }.getOrDefault(false)
    }

    /**
     * Ad denetimi.
     *
     * Dosya adları ağdan geliyor; ".." ya da eğik çizgi taşıyan bir ad
     * uygulamanın kendi klasörünün dışına yazabilirdi.
     */
    private fun safe(name: String): Boolean =
        name.isNotBlank() && name.none { it == '/' || it == '\\' } && name != "." && name != ".."
}

/**
 * Aynı ağdaki cihazla doğrudan konuşan taşıyıcı.
 *
 * Kendi yazdıkları [LocalStore]'a gidiyor, karşı tarafınkiler ağdan
 * okunuyor. Senkron motoru arada bir fark görmüyor: onun için iki taşıyıcı
 * da "cihaz klasörleri ve içindeki değişmez dosyalar".
 */
@Singleton
class LanTransport @Inject constructor(
    private val store: LocalStore,
    private val peer: LanPeer,
) : SyncTransport {

    init {
        // Sunucu da aynı dosyaları sunuyor.
        peer.store = store
    }

    private fun partner(): Peer? = peer.peers.value.firstOrNull()

    override suspend fun isReady(): Boolean = peer.running.value

    override suspend fun deviceFolders(): List<String> = withContext(Dispatchers.IO) {
        val remote = partner()?.let { peer.folders(it) }.orEmpty()
        (store.folders() + remote).distinct()
    }

    override suspend fun fileNames(deviceFolder: String): List<String> =
        withContext(Dispatchers.IO) {
            val local = store.files(deviceFolder)
            if (local.isNotEmpty()) return@withContext local
            partner()?.let { peer.files(it, deviceFolder) }.orEmpty()
        }

    override suspend fun read(deviceFolder: String, fileName: String): ByteArray? =
        withContext(Dispatchers.IO) {
            store.read(deviceFolder, fileName)
                ?: partner()?.let { peer.file(it, deviceFolder, fileName) }
        }

    override suspend fun write(
        deviceFolder: String,
        fileName: String,
        bytes: ByteArray,
    ): Boolean = withContext(Dispatchers.IO) { store.write(deviceFolder, fileName, bytes) }

    /**
     * Kendi dosyalarını karşı tarafa gönderir.
     *
     * Bu olmadan senkron tek yönlü olurdu: karşı tarafın yazdıklarını
     * okurduk ama kendimizinkini ancak o telefon senkrona bastığında
     * verirdik. Tek dokunuşta iki yön de tamamlansın diye.
     */
    override suspend fun publish() {
        val target = partner() ?: return
        withContext(Dispatchers.IO) {
            val theirs = store.folders().associateWith { folder ->
                peer.files(target, folder).toSet()
            }
            store.folders().forEach { folder ->
                val already = theirs[folder].orEmpty()
                store.files(folder).forEach { name ->
                    if (name in already) return@forEach
                    val bytes = store.read(folder, name) ?: return@forEach
                    peer.send(target, folder, name, bytes)
                }
            }
        }
    }
}
