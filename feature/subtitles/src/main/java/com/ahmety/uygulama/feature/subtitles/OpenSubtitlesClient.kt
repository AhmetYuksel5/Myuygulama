package com.ahmety.uygulama.feature.subtitles

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Arama sonucundaki bir altyazı. */
data class SubtitleHit(
    val fileId: Long,
    val language: String,
    /** Sürüm adı: "The.Matrix.1999.1080p.BluRay.x264-YIFY" gibi. */
    val release: String,
    val movieName: String,
    val year: Int,
    val downloads: Int,
    /** Dosya adının video dosyasıyla birebir eşleştiği durum. */
    val fromHash: Boolean,
)

sealed interface SubtitleResult<out T> {
    data class Ok<T>(val value: T) : SubtitleResult<T>
    data class Failed(val reason: String) : SubtitleResult<Nothing>
}

/**
 * OpenSubtitles REST API'si (v1).
 *
 * Arama anahtarla yapılıyor; indirme için hesapla alınan bir belirteç günlük
 * kotayı artırıyor. Belirteç süresi dolarsa bir kez yeniden giriş deneniyor.
 */
@Singleton
class OpenSubtitlesClient @Inject constructor(
    private val settings: SubtitleSettings,
) {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Filmi arar. Hem İngilizce hem Türkçe sonuçlar tek istekte geliyor;
     * eşleştirmeyi biz yapıyoruz.
     */
    suspend fun search(query: String, year: Int? = null): SubtitleResult<List<SubtitleHit>> =
        withContext(Dispatchers.IO) {
            val key = settings.apiKey
            if (key.isBlank()) {
                return@withContext SubtitleResult.Failed("OpenSubtitles anahtarı girilmemiş.")
            }
            // İlk aramadan önce giriş yapıyoruz: hesabın hangi sunucuyu
            // kullanacağını ancak giriş yanıtı söylüyor ve indirme kotası da
            // orada açılıyor.
            if (settings.token.isBlank() && settings.username.isNotBlank()) {
                login()
            }
            // Adresi elle birleştirmiyoruz: film adlarında boşluk, kesme
            // işareti, iki nokta oluyor ve yanlış kodlama sunucuyu şaşırtıyor.
            val url = "$BASE/subtitles".toHttpUrl().newBuilder()
                .addQueryParameter("languages", "en,tr")
                .addQueryParameter("order_by", "download_count")
                .addQueryParameter("order_direction", "desc")
                .addQueryParameter("query", query.trim().lowercase())
                .apply { if (year != null) addQueryParameter("year", year.toString()) }
                .build()
            request(Request.Builder().url(url).get()).map { body ->
                val items = JSONObject(body).optJSONArray("data") ?: return@map emptyList()
                (0 until items.length()).mapNotNull { index ->
                    val attributes = items.getJSONObject(index).optJSONObject("attributes")
                        ?: return@mapNotNull null
                    val files = attributes.optJSONArray("files") ?: return@mapNotNull null
                    if (files.length() == 0) return@mapNotNull null
                    val details = attributes.optJSONObject("feature_details")
                    SubtitleHit(
                        fileId = files.getJSONObject(0).optLong("file_id"),
                        language = attributes.optString("language").lowercase(),
                        release = attributes.optString("release"),
                        movieName = details?.optString("title").orEmpty(),
                        year = details?.optInt("year") ?: 0,
                        downloads = attributes.optInt("download_count"),
                        fromHash = attributes.optBoolean("moviehash_match"),
                    )
                }.filter { it.fileId > 0 }
            }
        }

    /** Altyazı metnini indirir. */
    suspend fun download(fileId: Long): SubtitleResult<String> = withContext(Dispatchers.IO) {
        when (val link = downloadLink(fileId, retry = true)) {
            is SubtitleResult.Failed -> link
            is SubtitleResult.Ok -> request(Request.Builder().url(link.value).get(), auth = false)
        }
    }

    private suspend fun downloadLink(fileId: Long, retry: Boolean): SubtitleResult<String> {
        val payload = JSONObject().put("file_id", fileId).toString()
        val response = request(
            Request.Builder()
                .url("$BASE/download")
                .post(payload.toRequestBody(JSON)),
        )
        return when (response) {
            is SubtitleResult.Ok -> {
                val link = JSONObject(response.value).optString("link")
                if (link.isBlank()) {
                    SubtitleResult.Failed("İndirme bağlantısı alınamadı.")
                } else {
                    SubtitleResult.Ok(link)
                }
            }

            is SubtitleResult.Failed -> {
                // Belirtecin süresi dolmuş olabilir: bir kez yeniden giriş dene.
                if (retry && settings.username.isNotBlank() && login() is SubtitleResult.Ok) {
                    downloadLink(fileId, retry = false)
                } else {
                    response
                }
            }
        }
    }

    /** Hesapla giriş; günlük indirme kotasını artırıyor. */
    suspend fun login(): SubtitleResult<Unit> = withContext(Dispatchers.IO) {
        if (settings.username.isBlank() || settings.password.isBlank()) {
            return@withContext SubtitleResult.Failed("Kullanıcı adı ve parola girilmemiş.")
        }
        val payload = JSONObject()
            .put("username", settings.username)
            .put("password", settings.password)
            .toString()
        request(
            Request.Builder().url("$BASE/login").post(payload.toRequestBody(JSON)),
            useToken = false,
        ).map { body ->
            val json = JSONObject(body)
            settings.token = json.optString("token")
            // API, hesabın hangi sunucuyu kullanacağını giriş yanıtında
            // söylüyor: VIP hesaplarda adres vip-api'ye dönüyor. Onu
            // kullanmazsak istekler yanlış sunucuya gidiyor.
            json.optString("base_url").takeIf { it.isNotBlank() }?.let { host ->
                settings.baseUrl = if (host.startsWith("http")) host else "https://$host"
            }
            Unit
        }
    }

    private fun request(
        builder: Request.Builder,
        auth: Boolean = true,
        useToken: Boolean = true,
    ): SubtitleResult<String> = runCatching {
        if (auth) {
            builder.header("Api-Key", settings.apiKey)
            // API kendi istemcisini tanıyabilsin diye kimliğimizi veriyoruz;
            // eksikse ya da tanınmıyorsa istekler reddediliyor.
            builder.header("User-Agent", USER_AGENT)
            builder.header("Accept", "application/json")
            val token = settings.token
            if (useToken && token.isNotBlank()) {
                builder.header("Authorization", "Bearer $token")
            }
        }
        val request = builder.build()
        // 5xx sunucu tarafı ve çoğu zaman geçici; birkaç saniye bekleyip
        // yeniden denemek kullanıcıyı boşuna uğraştırmaktan iyi.
        var lastCode = 0
        repeat(SERVER_RETRIES) { attempt ->
            if (attempt > 0) Thread.sleep(RETRY_DELAY_MS * attempt)
            http.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (response.isSuccessful) return@runCatching SubtitleResult.Ok(body)
                lastCode = response.code
                if (response.code < 500) {
                    return@runCatching SubtitleResult.Failed(readableError(response.code))
                }
            }
        }
        SubtitleResult.Failed(readableError(lastCode))
    }.getOrElse { error ->
        SubtitleResult.Failed("Bağlantı kurulamadı: ${error.message ?: "bilinmeyen hata"}")
    }

    private inline fun <T, R> SubtitleResult<T>.map(transform: (T) -> R): SubtitleResult<R> =
        when (this) {
            is SubtitleResult.Ok -> runCatching { SubtitleResult.Ok(transform(value)) }
                .getOrElse { SubtitleResult.Failed("Yanıt anlaşılamadı.") }

            is SubtitleResult.Failed -> this
        }

    private fun readableError(code: Int): String = when (code) {
        401 -> "Anahtar ya da oturum reddedildi. Ayarlardan yeniden gir."
        403 -> "Erişim reddedildi. Anahtarın doğru olduğundan emin ol."
        406 -> "Günlük indirme hakkın doldu."
        410 -> "İndirme bağlantısının süresi dolmuş, yeniden dene."
        429 -> "Çok sık istek gönderildi, biraz bekle."
        502, 503, 504 ->
            "OpenSubtitles sunucusu şu an yanıt vermiyor ($code). Birkaç dakika sonra dene."
        in 500..599 -> "OpenSubtitles sunucusunda hata ($code)."
        else -> "Sunucu $code döndü."
    }

    /** Hesabın sunucusu; giriş yanıtı farklı bir adres verirse o kullanılıyor. */
    private val BASE: String
        get() = settings.baseUrl.trimEnd('/') + "/api/v1"

    private companion object {
        const val USER_AGENT = "Uygulama v1.0"
        val JSON = "application/json".toMediaType()
        const val SERVER_RETRIES = 3
        const val RETRY_DELAY_MS = 1_500L
    }
}
