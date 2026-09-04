package com.ahmety.uygulama.core.database.sync

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Değişiklik günlüklerini GitHub'daki özel bir depoda taşır.
 *
 * Mobil veriyle çalışan tek gerçekçi yol ortada bir yer olması: iki telefon
 * operatörün arkasında saklı olduğu için birbirini doğrudan arayamıyor. O
 * "orta yer" için sunucu kiralamak yerine zaten var olan bir şey
 * kullanılıyor — uygulama GitHub'la güncellemeler için nasılsa konuşuyor.
 *
 * Depo **özel** olmalı. Dosyalar [SyncCrypto] ile şifreli, yani GitHub da
 * anlamsız baytlar görüyor; ama şifreli diye veriyi herkese açık bir yere
 * koymak özensizlik olurdu.
 *
 * Dosyalar `sync/<cihaz>/<dosya>` altında duruyor ve bir kez yazıldıktan
 * sonra değişmiyor; bu yüzden çakışma diye bir şey yok ve aynı ada ikinci
 * kez yazmaya kalkışılmıyor.
 */
@Singleton
class GitHubTransport @Inject constructor(
    @ApplicationContext context: Context,
) : SyncTransport {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** "kullanıcı/depo" biçiminde. */
    var repository: String
        get() = prefs.getString(KEY_REPO, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_REPO, value.trim().trim('/')).apply()

    /**
     * Erişim anahtarı. Yalnızca istek başlığında kullanılıyor; hiçbir yere
     * yazılmıyor ve hata mesajlarına konmuyor.
     */
    var token: String
        get() = prefs.getString(KEY_TOKEN, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value.trim()).apply()

    val configured: Boolean get() = repository.contains('/') && token.isNotBlank()

    /** Ekranda anahtarın tamamı asla yazılmıyor. */
    fun maskedToken(): String {
        val value = token
        if (value.isBlank()) return ""
        return if (value.length <= 10) "•".repeat(value.length) else {
            value.take(7) + "…" + value.takeLast(4)
        }
    }

    fun clear() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_REPO).apply()
    }

    override suspend fun isReady(): Boolean = configured

    override suspend fun deviceFolders(): List<String> = list(ROOT, directories = true)

    override suspend fun fileNames(deviceFolder: String): List<String> {
        if (!safe(deviceFolder)) return emptyList()
        return list("$ROOT/$deviceFolder", directories = false)
    }

    override suspend fun read(deviceFolder: String, fileName: String): ByteArray? {
        if (!safe(deviceFolder) || !safe(fileName)) return null
        return withContext(Dispatchers.IO) {
            // Ham biçim isteniyor: içeriği JSON'un içinden ayıklamaya ve
            // satırlara bölünmüş base64'ü toparlamaya gerek kalmıyor.
            send("GET", contents("$ROOT/$deviceFolder/$fileName"), null, raw = true)
                ?.takeIf { it.first == 200 }
                ?.second
        }
    }

    override suspend fun write(
        deviceFolder: String,
        fileName: String,
        bytes: ByteArray,
    ): Boolean {
        if (!safe(deviceFolder) || !safe(fileName)) return false
        return withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("message", "senkron: $deviceFolder/$fileName")
                .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
                .toString()
            val response = send(
                "PUT",
                contents("$ROOT/$deviceFolder/$fileName"),
                body,
                raw = false,
            ) ?: return@withContext false

            when (response.first) {
                in 200..299 -> true
                // Dosya zaten var. Dosyalar değişmez olduğu için bu bir
                // hata değil, "yazılacak bir şey kalmamış" demek.
                409, 422 -> true
                else -> false
            }
        }
    }

    private suspend fun list(path: String, directories: Boolean): List<String> =
        withContext(Dispatchers.IO) {
            val response = send("GET", contents(path), null, raw = false)
                ?: return@withContext emptyList()
            // Klasör henüz yoksa 404 dönüyor; ilk senkrondan önce olağan.
            if (response.first != 200) return@withContext emptyList()

            val wanted = if (directories) "dir" else "file"
            runCatching {
                val array = JSONArray(response.second.decodeToString())
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    if (item.optString("type") != wanted) return@mapNotNull null
                    item.optString("name").takeIf { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
        }

    private fun contents(path: String): String {
        val encoded = path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        return "$API/repos/$repository/contents/$encoded"
    }

    private fun send(
        method: String,
        url: String,
        body: String?,
        raw: Boolean,
    ): Pair<Int, ByteArray>? {
        if (!configured) return null
        return runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = method
            connection.connectTimeout = TIMEOUT
            connection.readTimeout = TIMEOUT
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            connection.setRequestProperty(
                "Accept",
                if (raw) "application/vnd.github.raw" else "application/vnd.github+json",
            )
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray()) }
            }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            connection.disconnect()
            code to bytes
        }.getOrNull()
    }

    /** Ad denetimi: yol dışına çıkan bir ad depoda başka bir yeri ezerdi. */
    private fun safe(name: String): Boolean =
        name.isNotBlank() && name.none { it == '/' || it == '\\' } && name != "." && name != ".."

    private companion object {
        const val API = "https://api.github.com"
        const val API_VERSION = "2022-11-28"
        const val ROOT = "sync"
        const val TIMEOUT = 20000
        const val PREFS_NAME = "merkez_senkron_github"
        const val KEY_REPO = "repo"
        const val KEY_TOKEN = "token"
    }
}
