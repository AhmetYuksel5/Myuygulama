package com.ahmety.uygulama.feature.gestures

import android.content.Context
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Ekran Ajanı'nın yerel ve Google Drive destekli hafıza (merdiven metotları)
 * ile çalışma günlüğü (log) deposu.
 *
 * Google Drive bağlantısı kullanıcıya ait Apps Script Web App köprüsü üzerinden
 * çalışır; böylece Google Cloud OAuth kurulumuna gerek kalmadan arka planda
 * dosya arama, okuma, günlük yükleme ve hafıza eşitleme yapılabilir.
 */
object ScreenAgentStorage {

    private const val PREFS_NAME = "merkez_yapay_zeka"
    private const val KEY_DRIVE_URL = "drive_bridge_url"
    private const val KEY_DRIVE_TOKEN = "drive_bridge_token"

    private const val MEMORY_FILE = "screen_agent_memory.md"
    private const val LOG_FILE = "screen_agent_log.md"
    private const val MAX_LOCAL_LOG_CHARS = 50_000
    private const val MEMORY_REFRESH_MS = 2 * 60 * 1_000L

    private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

    @Volatile
    private var lastMemoryPullMs = 0L

    private val http: OkHttpClient by lazy {
        // Apps Script web uygulamaları POST sonrası JSON gövdesini 302 yönlendirmesiyle
        // script.googleusercontent.com üzerinden döndürür; yönlendirme açık olmalı.
        OkHttpClient.Builder()
            .followRedirects(true)
            .followSslRedirects(true)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(35, TimeUnit.SECONDS)
            .build()
    }

    val DEFAULT_MEMORY: String = """
        # Merkez Ekran Ajanı — Hafıza ve Merdiven Metotları

        ## Genel Kurallar
        - Google Drive dosyası aranıyorsa ekranda Drive uygulamasını açmak yerine önce `drive_search` ve `drive_read` kullan; gerekirse dönen linki `open_url` ile tek adımda aç.
        - Birden fazla eşleşen kişi, hesap veya cihaz çıktığında tahmin etme; `ask` eylemiyle seçenekleri sun.

        ## Öğrenilen Metotlar
    """.trimIndent() + "\n"

    fun isBridgeConfigured(context: Context): Boolean {
        val (url, token) = bridgeCreds(context)
        return url.isNotBlank() && token.isNotBlank()
    }

    // ---- Yerel hafıza (Merkez_Ajan_Hafiza.md önbelleği) ----

    fun readLocalMemory(context: Context): String {
        val file = File(context.applicationContext.filesDir, MEMORY_FILE)
        val text = runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
        return text.ifBlank { DEFAULT_MEMORY }
    }

    fun writeLocalMemory(context: Context, content: String) {
        val file = File(context.applicationContext.filesDir, MEMORY_FILE)
        val clean = content.ifBlank { DEFAULT_MEMORY }
        runCatching { file.writeText(clean) }
    }

    /**
     * Yeni bir merdiven metodu / öğrenilen kuralı hem yerel `.md` dosyasına hem de
     * (köprü kuruluysa) Google Drive'daki `Merkez_Ajan_Hafiza.md` dosyasına ekler.
     */
    fun appendMemory(context: Context, note: String, syncDrive: Boolean = true): String {
        val clean = note.trim()
        if (clean.isEmpty()) return readLocalMemory(context)
        val line = if (clean.startsWith("- ")) clean else "- $clean"
        val current = readLocalMemory(context)
        val updated = if (current.contains(line)) {
            current
        } else {
            current.trimEnd() + "\n" + line + "\n"
        }
        writeLocalMemory(context, updated)
        if (syncDrive && isBridgeConfigured(context)) {
            Thread {
                runCatching {
                    val res = callBridge(
                        context,
                        JSONObject()
                            .put("action", "append_memory")
                            .put("note", line),
                    )
                    val remote = res.optString("content").trim()
                    if (remote.isNotEmpty()) {
                        writeLocalMemory(context, remote + "\n")
                        lastMemoryPullMs = System.currentTimeMillis()
                    }
                }
            }.start()
        }
        return updated
    }

    /** Ayarlar ekranından elle düzenlenen hafızayı kaydeder ve varsa Drive'a yazar. */
    fun saveMemory(context: Context, content: String): Result<String> {
        val clean = content.ifBlank { DEFAULT_MEMORY }
        writeLocalMemory(context, clean)
        if (!isBridgeConfigured(context)) return Result.success("Cihaza kaydedildi.")
        return runCatching {
            val res = callBridge(
                context,
                JSONObject()
                    .put("action", "write_memory")
                    .put("content", clean),
            )
            if (!res.optBoolean("ok", false)) {
                error(res.optString("error").ifBlank { "Drive hafızası güncellenemedi." })
            }
            lastMemoryPullMs = System.currentTimeMillis()
            "Cihaza ve Google Drive'a kaydedildi."
        }
    }

    /** Drive'daki `Merkez_Ajan_Hafiza.md` dosyasını çeker (sık çağrılmasın diye 2 dk önbellekli). */
    fun syncMemoryFromDrive(context: Context, force: Boolean = false): Result<String> {
        if (!isBridgeConfigured(context)) return Result.success(readLocalMemory(context))
        val now = System.currentTimeMillis()
        if (!force && now - lastMemoryPullMs < MEMORY_REFRESH_MS) {
            return Result.success(readLocalMemory(context))
        }
        return runCatching {
            val res = callBridge(context, JSONObject().put("action", "read_memory"))
            if (!res.optBoolean("ok", false)) {
                error(res.optString("error").ifBlank { "Drive hafızası okunamadı." })
            }
            val remote = res.optString("content").trim()
            if (remote.isNotEmpty()) {
                writeLocalMemory(context, remote + "\n")
                lastMemoryPullMs = now
                remote + "\n"
            } else {
                readLocalMemory(context)
            }
        }
    }

    // ---- Çalışma günlüğü (yerel + otomatik Drive yükleme) ----

    fun readLocalLog(context: Context): String {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        return runCatching { if (file.exists()) file.readText() else "" }.getOrDefault("")
    }

    fun clearLocalLog(context: Context) {
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        runCatching { if (file.exists()) file.delete() }
    }

    /** En yeni görev günlüğünü en üste yazar ve köprü açıksa Drive'a da yollar. */
    fun recordRunLog(context: Context, entry: String) {
        val clean = entry.trim()
        if (clean.isEmpty()) return
        val file = File(context.applicationContext.filesDir, LOG_FILE)
        runCatching {
            val existing = if (file.exists()) file.readText() else ""
            val combined = (clean + "\n\n---\n\n" + existing).take(MAX_LOCAL_LOG_CHARS)
            file.writeText(combined)
        }
        uploadLogChunk(context, clean)
    }

    /** Yeni eklenen günlük satırlarını arka planda Drive'daki Merkez_Ajan_Log.md dosyasına yollar. */
    fun uploadLogChunk(context: Context, chunk: String) {
        val clean = chunk.trim()
        if (clean.isEmpty() || !isBridgeConfigured(context)) return
        Thread {
            runCatching {
                callBridge(
                    context,
                    JSONObject()
                        .put("action", "append_log")
                        .put("entry", clean),
                )
            }
        }.start()
    }

    // ---- Google Drive köprü araçları ----

    fun pingBridge(context: Context): Result<String> {
        if (!isBridgeConfigured(context)) {
            return Result.failure(IllegalStateException("Önce köprü URL'si ve jetonunu kaydet."))
        }
        return runCatching {
            val res = callBridge(context, JSONObject().put("action", "ping"))
            if (!res.optBoolean("ok", false)) {
                error(res.optString("error").ifBlank { "Köprü yanıt vermedi." })
            }
            "Bağlantı başarılı (MerkezAjan klasörü ve dosyaları hazır)."
        }
    }

    fun driveSearch(context: Context, query: String, limit: Int = 8): Result<String> {
        if (!isBridgeConfigured(context)) {
            return Result.failure(IllegalStateException("Google Drive köprüsü kurulu değil."))
        }
        return runCatching {
            val res = callBridge(
                context,
                JSONObject()
                    .put("action", "drive_search")
                    .put("query", query)
                    .put("limit", limit.coerceIn(1, 15)),
            )
            if (!res.optBoolean("ok", false)) {
                error(res.optString("error").ifBlank { "Drive araması başarısız." })
            }
            val items = res.optJSONArray("items")
            if (items == null || items.length() == 0) {
                "Drive'da \"$query\" için eşleşen dosya bulunamadı."
            } else {
                buildString {
                    append("Drive arama sonuçları (\"$query\"):\n")
                    for (i in 0 until items.length()) {
                        val row = items.optJSONObject(i) ?: continue
                        val id = row.optString("id")
                        val name = row.optString("name")
                        val mime = shortMime(row.optString("mimeType"))
                        val url = row.optString("url")
                        append("${i + 1}. $name ($mime) | id=$id | url=$url\n")
                    }
                }.trim()
            }
        }
    }

    fun driveRead(context: Context, fileId: String, maxChars: Int = 3500): Result<String> {
        if (!isBridgeConfigured(context)) {
            return Result.failure(IllegalStateException("Google Drive köprüsü kurulu değil."))
        }
        return runCatching {
            val res = callBridge(
                context,
                JSONObject()
                    .put("action", "drive_read")
                    .put("fileId", fileId)
                    .put("maxChars", maxChars.coerceIn(500, 8000)),
            )
            if (!res.optBoolean("ok", false)) {
                error(res.optString("error").ifBlank { "Drive dosyası okunamadı." })
            }
            val name = res.optString("name")
            val url = res.optString("url")
            val content = res.optString("content").trim()
            "Dosya: $name ($url)\n\n$content"
        }
    }

    private fun callBridge(context: Context, payload: JSONObject): JSONObject {
        val (url, token) = bridgeCreds(context)
        require(url.startsWith("https://")) { "Köprü URL'si https:// ile başlamalı." }
        payload.put("token", token)
        val request = Request.Builder()
            .url(url)
            .post(payload.toString().toRequestBody(JSON_MEDIA))
            .build()
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty().trim()
            check(response.isSuccessful) { "HTTP ${response.code}" }
            // Apps Script yetki vermemişse HTML giriş sayfası döndürüyor; bunu anlaşılır bildir.
            if (body.startsWith("<")) {
                error("Apps Script HTML döndürdü: dağıtım erişimini 'Herkes' yapıp yetkiyi onayla.")
            }
            return JSONObject(body)
        }
    }

    private fun bridgeCreds(context: Context): Pair<String, String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_DRIVE_URL, null).orEmpty().trim()
        val token = prefs.getString(KEY_DRIVE_TOKEN, null).orEmpty().trim()
        return url to token
    }

    private fun shortMime(mime: String): String = when {
        mime.contains("folder") -> "klasör"
        mime.contains("document") -> "Doküman"
        mime.contains("spreadsheet") -> "E-Tablo"
        mime.contains("presentation") -> "Slayt"
        mime.contains("pdf") -> "PDF"
        mime.startsWith("image/") -> "resim"
        mime.startsWith("text/") -> "metin"
        else -> mime.substringAfterLast('/').ifBlank { "dosya" }
    }
}
