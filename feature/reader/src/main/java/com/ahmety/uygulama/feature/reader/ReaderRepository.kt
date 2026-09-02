package com.ahmety.uygulama.feature.reader

import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.EntryType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.nio.charset.Charset
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

sealed interface SaveArticleResult {
    data class Saved(val entryId: Long, val title: String) : SaveArticleResult
    data class Failed(val reason: String) : SaveArticleResult
}

/**
 * URL'den okunabilir makale çıkarıp arşive kaydeder.
 *
 * Kaydedilen makale sıradan bir Entry (tip: ARTICLE) — yani notlarla aynı
 * arama indeksinde, aynı etiket sisteminde. Çevrimdışı okunur; kaynak URL
 * `source` alanında durur.
 */
@Singleton
class ReaderRepository @Inject constructor(
    private val entryRepository: EntryRepository,
    private val images: ArticleImageStore,
) {

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    suspend fun saveFromUrl(rawUrl: String): SaveArticleResult = withContext(Dispatchers.IO) {
        val url = normalizeUrl(rawUrl)
            ?: return@withContext SaveArticleResult.Failed("Geçerli bir adres bulunamadı.")

        val html = runCatching { fetch(url) }.getOrNull()
            ?: return@withContext SaveArticleResult.Failed(
                "Sayfa indirilemedi. Bağlantıyı ve interneti kontrol et.",
            )

        val article = ArticleExtractor.extract(url, html)
            ?: return@withContext SaveArticleResult.Failed(
                "Sayfadan okunabilir metin çıkarılamadı; içerik komut dosyasıyla " +
                    "yükleniyor ya da üye duvarının arkasında olabilir.",
            )

        val body = buildString {
            article.byline?.let { appendLine(it).appendLine() }
            append(article.body)
        }
        val id = entryRepository.createEntry(
            type = EntryType.ARTICLE,
            title = article.title,
            body = body,
            source = url,
        )
        // Önizleme resmi yazının kendisi kadar önemli değil: alınamazsa
        // makale yine kaydedilmiş sayılıyor, kart resimsiz görünüyor.
        article.image?.let { fetchPreview(it, id) }
        SaveArticleResult.Saved(entryId = id, title = article.title)
    }

    /** Kaydedilmiş sayfanın önizleme resmi; yoksa null. */
    fun previewImage(entryId: Long): File? = images.fileFor(entryId)

    /** Makale silinince resmi de gitsin; yoksa dosyalar birikip kalıyor. */
    fun deletePreview(entryId: Long) = images.delete(entryId)

    /**
     * Kapak resmini indirip küçültülmüş hâlde saklar.
     *
     * Büyük dosyanın peşine düşmüyoruz: kartta kaplayacağı yer birkaç yüz
     * piksel, bunun için megabaytlık bir dosyayı çekmenin anlamı yok.
     * Sunucu boyutu bildirmezse indirdiğimiz kadarına bakıp sınırı aşarsa
     * bırakıyoruz.
     */
    private fun fetchPreview(imageUrl: String, entryId: Long) {
        runCatching {
            val request = Request.Builder()
                .url(imageUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "image/*")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                val body = response.body ?: return
                if (body.contentLength() > MAX_IMAGE_BYTES) return
                val bytes = body.bytes()
                if (bytes.size > MAX_IMAGE_BYTES) return
                images.save(entryId, bytes)
            }
        }
    }

    /**
     * Paylaşılan metnin içinden URL'yi ayıklar; paylaşımlar çoğu zaman
     * "başlık + link" biçiminde gelir.
     */
    fun findUrl(sharedText: String): String? =
        URL_PATTERN.find(sharedText)?.value

    private fun normalizeUrl(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        val candidate = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
        return candidate.takeIf { URL_PATTERN.matches(it) || it.contains('.') }
    }

    private fun fetch(url: String): String {
        val request = Request.Builder()
            .url(url)
            // Gerçekçi mobil tarayıcı kimliği: bazı siteler tanımadıkları
            // istemcileri reddediyor. Cihazın kendi IP'sinden gittiğimiz için
            // bu genelde yeterli.
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "tr-TR,tr;q=0.9,en;q=0.8")
            .build()

        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "HTTP ${response.code}" }
            val bytes = response.body?.bytes() ?: error("Boş yanıt")

            // Charset önceliği: HTTP başlığı > HTML meta > UTF-8.
            // Eski Türkçe siteler hâlâ windows-1254 ilan edebiliyor.
            val headerCharset = response.body?.contentType()?.charset()
            val charset = headerCharset ?: sniffMetaCharset(bytes) ?: Charsets.UTF_8
            return String(bytes, charset)
        }
    }

    private fun sniffMetaCharset(bytes: ByteArray): Charset? {
        val head = String(bytes, 0, minOf(bytes.size, 4096), Charsets.ISO_8859_1)
        val name = META_CHARSET.find(head)?.groupValues?.getOrNull(1) ?: return null
        return runCatching { Charset.forName(name.trim()) }.getOrNull()
    }

    private companion object {
        /**
         * Gerçekçi mobil tarayıcı kimliği: bazı siteler tanımadıkları
         * istemcileri reddediyor. Cihazın kendi IP'sinden gittiğimiz için
         * bu genelde yeterli.
         */
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"

        /** Önizleme için bundan büyüğünü indirmenin anlamı yok. */
        const val MAX_IMAGE_BYTES = 3L * 1024 * 1024

        val URL_PATTERN = Regex("""https?://[^\s"'<>]+""")
        val META_CHARSET = Regex(
            """charset\s*=\s*["']?([\w-]+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
