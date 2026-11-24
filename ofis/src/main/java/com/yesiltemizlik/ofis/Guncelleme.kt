package com.yesiltemizlik.ofis

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * Yeni sürüm var mı: GitHub'daki "ofis-v0.N" etiketli son sürüm. Sürüm kodu
 * CI çalışma numarası + 1 olduğu için N, kurulu sürümün koduyla karşılaştırılır.
 */
object Guncelleme {
    data class Surum(val numara: Int, val apk: String)

    fun sonSurum(): Surum? = try {
        val c = URL("https://api.github.com/repos/AhmetYuksel5/Myuygulama/releases?per_page=40")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "ofis-uygulama")
        val metin = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val a = JSONArray(metin)
        var bulunan: Surum? = null
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            val n = Regex("^ofis-v0\\.(\\d+)$").find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val dosyalar = r.optJSONArray("assets") ?: continue
            if (dosyalar.length() == 0) continue
            if (bulunan == null || n > bulunan.numara) {
                bulunan = Surum(n, dosyalar.getJSONObject(0).optString("browser_download_url"))
            }
        }
        bulunan
    } catch (e: Exception) {
        null
    }
}
