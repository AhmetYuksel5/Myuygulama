package com.ahmety.uygulama.core.database.sync

import android.content.Context
import android.net.wifi.WifiManager
import android.os.Build
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URL
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Aynı ağda bulunan bir Merkez cihazı. */
data class Peer(
    val id: String,
    val name: String,
    val address: String,
    val port: Int,
) {
    internal val base: String get() = "http://$address:$port"
}

/**
 * Aynı ağdaki ikinci telefonu bulup dosyaları doğrudan ona veren katman.
 *
 * Syncthing'in yaptığı işin bu uygulamaya yeten kadarı: cihazlar birbirini
 * yerel ağda duyuruyla buluyor, sonra doğrudan konuşuyorlar. Bulut yok,
 * hesap yok, üçüncü bir uygulama yok.
 *
 * **Eşleşme kurtarma anahtarıyla.** Duyurunun içinde anahtardan türetilmiş
 * bir etiket var; aynı etiketi taşımayan cihaz görülmüyor bile. Yani ayrı
 * bir eşleştirme adımı yok — anahtarı ikinci telefona girmek zaten
 * eşleştirmek demek. Etiket anahtarın özeti olduğu için anahtarın kendisi
 * ağa hiç çıkmıyor.
 *
 * Taşınan dosyalar zaten [SyncCrypto] ile şifreli; etiket gizlilik için
 * değil, yanlış cihazla konuşmamak için.
 */
@Singleton
class LanPeer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val crypto: SyncCrypto,
    @DeviceId private val deviceId: String,
) {

    /** Sunucunun sunacağı dosyalar; [LanTransport] dolduruyor. */
    internal var store: LocalStore? = null

    private val _peers = MutableStateFlow<List<Peer>>(emptyList())
    val peers: StateFlow<List<Peer>> = _peers.asStateFlow()

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    private var scope: CoroutineScope? = null
    private var server: ServerSocket? = null
    private var beacon: DatagramSocket? = null
    private var multicastLock: WifiManager.MulticastLock? = null

    /** Cihazın ağda görünen adı. */
    val deviceName: String = "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    /**
     * Sunucuyu ve duyuruyu başlatır.
     *
     * Ekran açıkken çalışıyor: arka planda sürekli açık bir sunucu tutmak
     * pili yiyor ve iki telefon da senkron ekranındayken zaten yetiyor.
     */
    fun start() {
        if (scope != null) return
        val tag = tag() ?: return
        val jobs = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope = jobs

        jobs.launch {
            runCatching {
                val socket = ServerSocket(0)
                server = socket
                _running.value = true
                while (isActive && !socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    launch { runCatching { serve(client) } }
                }
            }
        }

        jobs.launch { announce(tag) }
        jobs.launch { listen(tag) }
    }

    fun stop() {
        scope?.cancel()
        scope = null
        runCatching { server?.close() }
        server = null
        runCatching { beacon?.close() }
        beacon = null
        runCatching { multicastLock?.release() }
        multicastLock = null
        _running.value = false
        _peers.value = emptyList()
    }

    // --- Duyuru ---

    /**
     * Kendini tanıtan paketi saniyede bir yayınlar.
     *
     * Yayın adresine gönderiliyor; ağdaki herkes duyuyor ama etiketi
     * tutmayan için anlamsız bir paket.
     */
    private suspend fun announce(tag: String) {
        val socket = beaconSocket() ?: return
        val payload = JSONObject()
            .put("id", deviceId)
            .put("name", deviceName)
            .put("tag", tag)
            .toString()
        while (currentCoroutineContext().isActive) {
            val port = server?.localPort ?: 0
            if (port > 0) {
                val message = JSONObject(payload).put("port", port).toString().toByteArray()
                runCatching {
                    socket.send(
                        DatagramPacket(
                            message,
                            message.size,
                            InetAddress.getByName(BROADCAST),
                            DISCOVERY_PORT,
                        ),
                    )
                }
            }
            delay(ANNOUNCE_INTERVAL)
        }
    }

    /** Gelen duyuruları dinler; etiketi tutanları listeye koyar. */
    private suspend fun listen(tag: String) = withContext(Dispatchers.IO) {
        val socket = beaconSocket() ?: return@withContext
        val buffer = ByteArray(2048)
        while (currentCoroutineContext().isActive) {
            val packet = DatagramPacket(buffer, buffer.size)
            val received = runCatching { socket.receive(packet); true }.getOrDefault(false)
            if (!received) continue

            val json = runCatching {
                JSONObject(String(packet.data, 0, packet.length))
            }.getOrNull() ?: continue

            if (json.optString("tag") != tag) continue
            val id = json.optString("id")
            if (id.isBlank() || id == deviceId) continue

            val peer = Peer(
                id = id,
                name = json.optString("name").ifBlank { id },
                address = packet.address.hostAddress.orEmpty(),
                port = json.optInt("port"),
            )
            if (peer.address.isBlank() || peer.port <= 0) continue
            _peers.value = (_peers.value.filter { it.id != peer.id } + peer)
                .sortedBy { it.name }
        }
    }

    private fun beaconSocket(): DatagramSocket? {
        beacon?.let { return it }
        // Yayın paketlerini alabilmek için kilit gerekiyor; bazı telefonlar
        // ekran kapalıyken bunları sessizce düşürüyor.
        runCatching {
            val wifi = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifi?.createMulticastLock("merkez-senkron")?.apply {
                setReferenceCounted(false)
                acquire()
            }
        }
        return runCatching {
            DatagramSocket(null).apply {
                reuseAddress = true
                broadcast = true
                bind(InetSocketAddress(DISCOVERY_PORT))
            }
        }.getOrNull()?.also { beacon = it }
    }

    // --- Sunucu ---

    private fun serve(client: Socket) = client.use { socket ->
        val input = socket.getInputStream()
        val requestLine = readLine(input) ?: return
        val headers = mutableMapOf<String, String>()
        while (true) {
            val line = readLine(input) ?: break
            if (line.isBlank()) break
            val colon = line.indexOf(':')
            if (colon > 0) {
                headers[line.take(colon).lowercase()] = line.substring(colon + 1).trim()
            }
        }

        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0).orEmpty()
        val target = parts.getOrNull(1).orEmpty()
        val body = headers["content-length"]?.toIntOrNull()?.let { read(input, it) }.orEmpty()

        val expected = tag()
        if (expected == null || headers[AUTH_HEADER] != expected) {
            respond(socket, "403 Forbidden", JSONObject().put("error", "auth").toString())
            return
        }

        val local = store
        if (local == null) {
            respond(socket, "503 Service Unavailable", JSONObject().put("error", "store").toString())
            return
        }

        val path = target.substringBefore('?')
        val query = target.substringAfter('?', "").split('&')
            .mapNotNull { pair ->
                val index = pair.indexOf('=')
                if (index <= 0) null else decode(pair.take(index)) to decode(pair.substring(index + 1))
            }
            .toMap()

        val answer = when {
            method == "GET" && path == "/folders" ->
                JSONObject().put("folders", JSONArray(local.folders()))

            method == "GET" && path == "/files" ->
                JSONObject().put("files", JSONArray(local.files(query["folder"].orEmpty())))

            method == "GET" && path == "/file" -> {
                val bytes = local.read(query["folder"].orEmpty(), query["name"].orEmpty())
                if (bytes == null) {
                    JSONObject().put("error", "yok")
                } else {
                    JSONObject().put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
                }
            }

            method == "POST" && path == "/file" -> {
                val json = runCatching { JSONObject(body) }.getOrNull()
                val data = json?.optString("data").orEmpty()
                val written = json != null && local.write(
                    json.optString("folder"),
                    json.optString("name"),
                    Base64.decode(data, Base64.NO_WRAP),
                )
                JSONObject().put("ok", written)
            }

            else -> JSONObject().put("error", "bilinmeyen")
        }
        respond(socket, "200 OK", answer.toString())
    }

    private fun respond(socket: Socket, status: String, body: String) {
        val bytes = body.toByteArray()
        val output = socket.getOutputStream()
        output.write(
            buildString {
                append("HTTP/1.1 ").append(status).append("\r\n")
                append("Content-Type: application/json; charset=utf-8\r\n")
                append("Content-Length: ").append(bytes.size).append("\r\n")
                append("Connection: close\r\n\r\n")
            }.toByteArray(),
        )
        output.write(bytes)
        output.flush()
    }

    private fun readLine(input: InputStream): String? {
        val builder = StringBuilder()
        while (true) {
            val value = input.read()
            if (value < 0) return builder.takeIf { it.isNotEmpty() }?.toString()
            if (value == '\n'.code) return builder.toString().trimEnd('\r')
            builder.append(value.toChar())
        }
    }

    private fun read(input: InputStream, length: Int): String {
        val bytes = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = input.read(bytes, read, length - read)
            if (count < 0) break
            read += count
        }
        return String(bytes, 0, read)
    }

    // --- İstemci ---

    suspend fun folders(peer: Peer): List<String> =
        get(peer, "/folders")?.optJSONArray("folders").toList()

    suspend fun files(peer: Peer, folder: String): List<String> =
        get(peer, "/files?folder=${encode(folder)}")?.optJSONArray("files").toList()

    suspend fun file(peer: Peer, folder: String, name: String): ByteArray? {
        val data = get(peer, "/file?folder=${encode(folder)}&name=${encode(name)}")
            ?.optString("data").orEmpty()
        if (data.isBlank()) return null
        return runCatching { Base64.decode(data, Base64.NO_WRAP) }.getOrNull()
    }

    suspend fun send(peer: Peer, folder: String, name: String, bytes: ByteArray): Boolean {
        val payload = JSONObject()
            .put("folder", folder)
            .put("name", name)
            .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .toString()
        return post(peer, "/file", payload)?.optBoolean("ok") ?: false
    }

    private suspend fun get(peer: Peer, path: String): JSONObject? =
        request(peer, path, null)

    private suspend fun post(peer: Peer, path: String, body: String): JSONObject? =
        request(peer, path, body)

    private suspend fun request(peer: Peer, path: String, body: String?): JSONObject? =
        withContext(Dispatchers.IO) {
            val token = tag() ?: return@withContext null
            runCatching {
                val connection = URL(peer.base + path).openConnection() as HttpURLConnection
                connection.connectTimeout = TIMEOUT
                connection.readTimeout = TIMEOUT
                connection.setRequestProperty(AUTH_HEADER, token)
                if (body != null) {
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                connection.inputStream.use { JSONObject(it.readBytes().decodeToString()) }
            }.getOrNull()
        }

    /**
     * Kurtarma anahtarından türeyen etiket.
     *
     * Anahtarın özeti; anahtarın kendisi ağa çıkmıyor ama aynı anahtarı
     * taşıyan iki cihaz aynı etiketi üretiyor.
     */
    private fun tag(): String? {
        val key = crypto.currentKey() ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest("merkez:$key".toByteArray())
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    private fun JSONArray?.toList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { name -> name.isNotBlank() } }
    }

    private fun encode(value: String) = java.net.URLEncoder.encode(value, "UTF-8")

    private fun decode(value: String) =
        runCatching { java.net.URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

    private companion object {
        /** Duyuruların gidip geldiği kapı. */
        const val DISCOVERY_PORT = 47291
        const val BROADCAST = "255.255.255.255"
        const val ANNOUNCE_INTERVAL = 1500L
        const val AUTH_HEADER = "x-merkez-etiket"
        const val TIMEOUT = 8000
    }
}
