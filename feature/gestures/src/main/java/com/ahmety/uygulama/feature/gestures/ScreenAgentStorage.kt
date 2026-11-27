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
    private const val KEY_PERSONAL_GMAIL_URL = "personal_gmail_bridge_url"
    private const val KEY_PERSONAL_GMAIL_TOKEN = "personal_gmail_bridge_token"

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
        - Google Drive dosyası aranıyorsa ekranda Drive uygulamasını açmak yerine önce `drive_search` ve `drive_read` kullan (`account: "work" | "personal"`); gerekirse dönen linki `open_url` ile tek adımda aç.
        - E-posta aranıyorsa ekranda Gmail uygulamasını açmak yerine önce `gmail_search` ve `gmail_read` araçlarını kullan; hangi hesap olduğu belli değilse ikisinde de ara veya `ask` ile sor.
        - E-posta yazılması istendiğinde asla doğrudan gönderme; `gmail_draft` ile taslak oluşturup kullanıcıya bildir.
        - Kullanıcı "takvimde ne var", "bugün/yarın ne var" veya "toplantı ekle" dediğinde ekranda Takvim uygulamasını açmak yerine doğrudan `calendar_list`, `calendar_search` ve `calendar_create` araçlarını kullan.
        - Birden fazla eşleşen kişi, hesap veya cihaz çıktığında tahmin etme; `ask` eylemiyle seçenekleri sun.

        ## Öğrenilen Metotlar
    """.trimIndent() + "\n"

    fun isBridgeConfigured(context: Context): Boolean {
        val (url, token) = bridgeCreds(context)
        return url.isNotBlank() && token.isNotBlank()
    }

    fun isPersonalGmailConfigured(context: Context): Boolean {
        val (url, token) = personalGmailCreds(context)
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
            val gmailNote = if (res.optBoolean("gmail", false)) "Gmail" else "Gmail kapalı"
            val calNote = if (res.optBoolean("calendar", false)) "Takvim" else "Takvim kapalı"
            "Bağlantı başarılı (Drive · $gmailNote · $calNote)."
        }
    }

    fun pingPersonalGmailBridge(context: Context): Result<String> {
        if (!isPersonalGmailConfigured(context)) {
            return Result.failure(IllegalStateException("Önce kişisel köprü URL'si (ve jeton) kaydedilmeli."))
        }
        return runCatching {
            val (url, token) = personalGmailCreds(context)
            val res = callEndpoint(url, token, JSONObject().put("action", "gmail_ping"))
            if (!res.optBoolean("ok", false)) {
                error(res.optString("error").ifBlank { "Kişisel köprü yanıt vermedi." })
            }
            val parts = buildList {
                add("Gmail")
                if (res.optBoolean("drive", false)) add("Drive")
                if (res.optBoolean("calendar", false)) add("Takvim")
            }.joinToString(" · ")
            val unread = res.optInt("unread", -1)
            val extra = if (unread >= 0) ", $unread okunmamış" else ""
            "Bağlantı başarılı ($parts hazır$extra)."
        }
    }

    fun driveSearch(
        context: Context,
        query: String,
        account: String = "work",
        limit: Int = 8,
    ): Result<String> = runCatching {
        val (url, token, label) = resolveBridge(context, account)
        val q = query.trim()
        require(q.isNotEmpty()) { "Drive arama sorgusu boş." }
        val res = callEndpoint(
            url,
            token,
            JSONObject()
                .put("action", "drive_search")
                .put("query", q)
                .put("limit", limit.coerceIn(1, 15)),
        )
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error").ifBlank { "Drive araması başarısız." })
        }
        val items = res.optJSONArray("items")
        if (items == null || items.length() == 0) {
            "$label Drive'da \"$q\" için eşleşen dosya bulunamadı."
        } else {
            buildString {
                append("$label Drive arama sonuçları (\"$q\"):\n")
                for (i in 0 until items.length()) {
                    val row = items.optJSONObject(i) ?: continue
                    val id = row.optString("id")
                    val name = row.optString("name")
                    val mime = shortMime(row.optString("mimeType"))
                    val urlOut = row.optString("url")
                    append("${i + 1}. $name ($mime) | id=$id | url=$urlOut\n")
                }
            }.trim()
        }
    }

    fun driveRead(
        context: Context,
        fileId: String,
        account: String = "work",
        maxChars: Int = 3500,
    ): Result<String> = runCatching {
        val (url, token, label) = resolveBridge(context, account)
        val fid = fileId.trim()
        require(fid.isNotEmpty()) { "fileId boş." }
        val res = callEndpoint(
            url,
            token,
            JSONObject()
                .put("action", "drive_read")
                .put("fileId", fid)
                .put("maxChars", maxChars.coerceIn(500, 8000)),
        )
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error").ifBlank { "Drive dosyası okunamadı." })
        }
        val name = res.optString("name")
        val fileUrl = res.optString("url")
        val content = res.optString("content").trim()
        "$label Drive dosyası: $name ($fileUrl)\n\n$content"
    }

    // ---- İki hesaplı Gmail köprü araçları (yalnızca okuma ve taslak) ----

    fun gmailSearch(
        context: Context,
        query: String,
        account: String = "work",
        limit: Int = 10,
    ): Result<String> = runCatching {
        val (url, token, label) = resolveBridge(context, account)
        val q = query.trim()
        require(q.isNotEmpty()) { "Gmail arama sorgusu boş." }
        val res = callEndpoint(
            url,
            token,
            JSONObject()
                .put("action", "gmail_search")
                .put("query", q)
                .put("limit", limit.coerceIn(1, 20)),
        )
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error").ifBlank { "Gmail araması başarısız." })
        }
        val items = res.optJSONArray("items")
        if (items == null || items.length() == 0) {
            "$label hesabında \"$q\" için eşleşen e-posta bulunamadı."
        } else {
            buildString {
                append("$label Gmail sonuçları (\"$q\"):\n")
                for (i in 0 until items.length()) {
                    val row = items.optJSONObject(i) ?: continue
                    val unreadTag = if (row.optBoolean("unread", false)) "[OKUNMADI] " else ""
                    val subject = row.optString("subject").ifBlank { "(konusuz)" }
                    val from = row.optString("from")
                    val date = row.optString("date")
                    val tid = row.optString("threadId")
                    val count = row.optInt("messageCount", 1)
                    val snippet = row.optString("snippet").take(140)
                    append("${i + 1}. $unreadTag$subject | Kimden: $from | Tarih: $date ($count mesaj) | threadId=$tid")
                    if (snippet.isNotBlank()) append(" | Özet: $snippet")
                    append('\n')
                }
            }.trim()
        }
    }

    fun gmailRead(
        context: Context,
        threadId: String,
        account: String = "work",
    ): Result<String> = runCatching {
        val (url, token, label) = resolveBridge(context, account)
        val tid = threadId.trim()
        require(tid.isNotEmpty()) { "threadId boş." }
        val res = callEndpoint(
            url,
            token,
            JSONObject()
                .put("action", "gmail_read")
                .put("threadId", tid),
        )
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error").ifBlank { "E-posta okunamadı." })
        }
        val subject = res.optString("subject").ifBlank { "(konusuz)" }
        val total = res.optInt("messageCount", 0)
        val msgs = res.optJSONArray("messages")
        buildString {
            append("$label Gmail yazışması: $subject (toplam $total mesaj, son ${msgs?.length() ?: 0})\n")
            if (msgs != null) {
                for (i in 0 until msgs.length()) {
                    val m = msgs.optJSONObject(i) ?: continue
                    append("\n--- Mesaj ${i + 1} ---\n")
                    append("Kimden: ").append(m.optString("from")).append('\n')
                    val to = m.optString("to")
                    if (to.isNotBlank()) append("Kime: ").append(to).append('\n')
                    append("Tarih: ").append(m.optString("date")).append('\n')
                    val atts = m.optJSONArray("attachments")
                    if (atts != null && atts.length() > 0) {
                        val names = (0 until atts.length()).mapNotNull { j ->
                            val a = atts.optJSONObject(j) ?: return@mapNotNull null
                            val n = a.optString("name").ifBlank { return@mapNotNull null }
                            val sz = a.optLong("size", 0L)
                            if (sz > 0L) "$n (${sz / 1024} KB)" else n
                        }
                        if (names.isNotEmpty()) append("Ekler: ").append(names.joinToString(", ")).append('\n')
                    }
                    append(m.optString("body").trim()).append('\n')
                }
            }
        }.trim()
    }

    fun gmailDraft(
        context: Context,
        to: String,
        subject: String,
        body: String,
        account: String = "work",
    ): Result<String> = runCatching {
        val (url, token, label) = resolveBridge(context, account)
        val recipient = to.trim()
        require(recipient.isNotEmpty()) { "Alıcı (to) boş." }
        val res = callEndpoint(
            url,
            token,
            JSONObject()
                .put("action", "gmail_draft")
                .put("to", recipient)
                .put("subject", subject.trim())
                .put("body", body),
        )
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error").ifBlank { "Taslak oluşturulamadı." })
        }
        val draftId = res.optString("draftId")
        "$label hesabında taslak oluşturuldu (gönderilmedi) | draftId=$draftId | Kime: $recipient | Konu: ${subject.trim()}"
    }

    // ---- İki hesaplı Google Takvim köprü araçları (listeleme, arama, oluşturma) ----

    fun calendarList(
        context: Context,
        start: String = "",
        end: String = "",
        account: String = "",
        limit: Int = 25,
    ): Result<String> = runCatching {
        val bridges = resolveReadBridges(context, account)
        val sections = bridges.map { bridge ->
            runCatching {
                val payload = JSONObject()
                    .put("action", "calendar_list")
                    .put("limit", limit.coerceIn(1, 50))
                if (start.isNotBlank()) payload.put("start", start.trim())
                if (end.isNotBlank()) payload.put("end", end.trim())
                val res = callEndpoint(bridge.url, bridge.token, payload)
                if (!res.optBoolean("ok", false)) {
                    error(res.optString("error").ifBlank { "Takvim okunamadı." })
                }
                val rangeNote = "${res.optString("start")} → ${res.optString("end")}"
                formatCalendarItems(bridge.label, "Takvim ($rangeNote)", res.optJSONArray("items"))
            }.getOrElse { "${bridge.label} Takvim hatası: ${it.message}" }
        }
        sections.joinToString("\n\n").trim()
    }

    fun calendarSearch(
        context: Context,
        query: String,
        start: String = "",
        end: String = "",
        account: String = "",
        limit: Int = 25,
    ): Result<String> = runCatching {
        val q = query.trim()
        require(q.isNotEmpty()) { "Takvim arama sorgusu boş." }
        val bridges = resolveReadBridges(context, account)
        val sections = bridges.map { bridge ->
            runCatching {
                val payload = JSONObject()
                    .put("action", "calendar_search")
                    .put("query", q)
                    .put("limit", limit.coerceIn(1, 50))
                if (start.isNotBlank()) payload.put("start", start.trim())
                if (end.isNotBlank()) payload.put("end", end.trim())
                val res = callEndpoint(bridge.url, bridge.token, payload)
                if (!res.optBoolean("ok", false)) {
                    error(res.optString("error").ifBlank { "Takvim araması başarısız." })
                }
                formatCalendarItems(bridge.label, "Takvim araması (\"$q\")", res.optJSONArray("items"))
            }.getOrElse { "${bridge.label} Takvim hatası: ${it.message}" }
        }
        sections.joinToString("\n\n").trim()
    }

    fun calendarCreate(
        context: Context,
        title: String,
        start: String,
        end: String = "",
        allDay: Boolean = false,
        description: String = "",
        location: String = "",
        account: String = "work",
    ): Result<String> = runCatching {
        val (url, token, label) = resolveBridge(context, account)
        val cleanTitle = title.trim()
        val cleanStart = start.trim()
        require(cleanTitle.isNotEmpty()) { "Etkinlik başlığı (title) boş." }
        require(cleanStart.isNotEmpty()) { "Etkinlik başlangıcı (start) boş." }
        val payload = JSONObject()
            .put("action", "calendar_create")
            .put("title", cleanTitle)
            .put("start", cleanStart)
            .put("allDay", allDay)
        if (end.isNotBlank()) payload.put("end", end.trim())
        if (description.isNotBlank()) payload.put("description", description.trim())
        if (location.isNotBlank()) payload.put("location", location.trim())
        val res = callEndpoint(url, token, payload)
        if (!res.optBoolean("ok", false)) {
            error(res.optString("error").ifBlank { "Etkinlik oluşturulamadı." })
        }
        val ev = res.optJSONObject("event")
        val evStart = ev?.optString("start").orEmpty().ifBlank { cleanStart }
        val evEnd = ev?.optString("end").orEmpty()
        val loc = ev?.optString("location").orEmpty()
        buildString {
            append("$label takvimine etkinlik eklendi: $cleanTitle | $evStart")
            if (evEnd.isNotBlank()) append(" → $evEnd")
            if (loc.isNotBlank()) append(" | Yer: $loc")
        }
    }

    private fun formatCalendarItems(label: String, heading: String, items: org.json.JSONArray?): String {
        if (items == null || items.length() == 0) {
            return "$label $heading: etkinlik bulunamadı."
        }
        return buildString {
            append("$label $heading:\n")
            for (i in 0 until items.length()) {
                val ev = items.optJSONObject(i) ?: continue
                val title = ev.optString("title").ifBlank { "(başlıksız)" }
                val s = ev.optString("start")
                val e = ev.optString("end")
                val allDay = ev.optBoolean("allDay", false)
                val loc = ev.optString("location")
                val desc = ev.optString("description")
                append("${i + 1}. $title | ")
                if (allDay) append("Tüm gün: $s") else append("$s → $e")
                if (loc.isNotBlank()) append(" | Yer: $loc")
                if (desc.isNotBlank()) append(" | Not: ${desc.take(120)}")
                append('\n')
            }
        }.trim()
    }

    private data class ResolvedBridge(val url: String, val token: String, val label: String)

    internal fun isPersonalAccount(account: String): Boolean {
        val norm = account.trim().lowercase(java.util.Locale.ROOT).replace("i\u0307", "i")
        return norm == "kisisel" || norm == "kişisel" || norm == "personal" || norm.contains("ahmetyuksel5")
    }

    internal fun isBothAccounts(account: String): Boolean {
        val norm = account.trim().lowercase(java.util.Locale.ROOT).replace("i\u0307", "i")
        return norm.isEmpty() || norm == "both" || norm == "ikisi" || norm == "hepsi" || norm == "all"
    }

    /** Takvim okuma/arama için hesap belirtilmezse kurulu tüm köprüleri (İş + Kişisel) döndürür. */
    private fun resolveReadBridges(context: Context, account: String): List<ResolvedBridge> {
        if (!isBothAccounts(account)) {
            return listOf(resolveBridge(context, account))
        }
        val list = mutableListOf<ResolvedBridge>()
        if (isBridgeConfigured(context)) {
            val (u, t) = bridgeCreds(context)
            list += ResolvedBridge(u, t, "İş (yesiltemizlik)")
        }
        if (isPersonalGmailConfigured(context)) {
            val (u, t) = personalGmailCreds(context)
            list += ResolvedBridge(u, t, "Kişisel (ahmetyuksel5)")
        }
        require(list.isNotEmpty()) { "Hiçbir Google Apps Script köprüsü kayıtlı değil: Merkez → Yapay zekâ." }
        return list
    }

    private fun resolveBridge(context: Context, account: String): ResolvedBridge {
        return if (isPersonalAccount(account)) {
            val (url, token) = personalGmailCreds(context)
            require(url.isNotBlank()) { "Kişisel köprü kayıtlı değil: Merkez → Yapay zekâ." }
            require(token.isNotBlank()) { "Kişisel köprü için jeton kayıtlı değil." }
            ResolvedBridge(url, token, "Kişisel (ahmetyuksel5)")
        } else {
            val (url, token) = bridgeCreds(context)
            require(url.isNotBlank() && token.isNotBlank()) {
                "İş köprüsü (yesiltemizlik) kayıtlı değil: Merkez → Yapay zekâ."
            }
            ResolvedBridge(url, token, "İş (yesiltemizlik)")
        }
    }

    private fun callBridge(context: Context, payload: JSONObject): JSONObject {
        val (url, token) = bridgeCreds(context)
        return callEndpoint(url, token, payload)
    }

    private fun callEndpoint(url: String, token: String, payload: JSONObject): JSONObject {
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

    private fun personalGmailCreds(context: Context): Pair<String, String> {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val url = prefs.getString(KEY_PERSONAL_GMAIL_URL, null).orEmpty().trim()
        // Kişisel jeton boş bırakıldıysa ana MERKEZ_TOKEN devralınır.
        val ownToken = prefs.getString(KEY_PERSONAL_GMAIL_TOKEN, null).orEmpty().trim()
        val token = ownToken.ifBlank { prefs.getString(KEY_DRIVE_TOKEN, null).orEmpty().trim() }
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
