package com.ahmety.uygulama.feature.ebook

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Open Library'den kitap kapağı.
 *
 * Anahtar istemiyor, ücretsiz ve sınırsız; bu yüzden tarayıcıda arayıp
 * indirme yolunun yanına konabiliyor. Karşılığında kapsamı dar: İngilizce
 * ve bilinen kitaplarda iyi, Türkçe ve niş kitaplarda çoğu zaman boş
 * dönüyor. Boş dönmesi hata değil, o yüzden kullanıcıya "bulunamadı"
 * deniyor ve elle koyma yolu duruyor.
 *
 * Ağ katmanı olarak OkHttp kullanılıyor; uygulamada zaten var, eklenen
 * kilobayt yok.
 */
@Singleton
class OpenLibraryCovers @Inject constructor() {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /** Ada en iyi uyan kitabın kapağı; bulunamazsa null. */
    suspend fun find(title: String): ByteArray? = withContext(Dispatchers.IO) {
        val query = title.trim()
        if (query.isEmpty()) return@withContext null
        runCatching {
            val coverId = searchCoverId(query) ?: return@runCatching null
            download("https://covers.openlibrary.org/b/id/$coverId-L.jpg")
        }.getOrNull()
    }

    /**
     * Aramanın ilk kapaklı sonucunun kapak numarası.
     *
     * İlk sonuç her zaman kapaklı olmuyor; kapağı olan ilk sonuç
     * alınıyor, yoksa arama boş sayılıyor.
     */
    private fun searchCoverId(title: String): Int? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        val body = get(
            "https://openlibrary.org/search.json" +
                "?q=$encoded&limit=$SEARCH_LIMIT&fields=cover_i",
        ) ?: return null
        val docs = JSONObject(body).optJSONArray("docs") ?: return null
        for (index in 0 until docs.length()) {
            val id = docs.optJSONObject(index)?.optInt("cover_i", 0) ?: 0
            if (id > 0) return id
        }
        return null
    }

    private fun get(url: String): String? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }

    private fun download(url: String): ByteArray? =
        http.newCall(Request.Builder().url(url).build()).execute().use { response ->
            if (!response.isSuccessful) return null
            val bytes = response.body?.bytes() ?: return null
            // Kapağı olmayan numaralar için minik bir vekil resim dönüyor;
            // onu kapak diye koymanın anlamı yok.
            if (bytes.size < MIN_BYTES) null else bytes
        }

    private companion object {
        const val SEARCH_LIMIT = 5

        /** Bundan küçük bir dosya kapak değil, vekil resimdir. */
        const val MIN_BYTES = 3000
    }
}
