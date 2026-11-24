package com.yesiltemizlik.ofis

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uygulama içi güncelleme: GitHub'daki "ofis-v0.N" etiketli son sürüm
 * indirilir ve Android'in kurulum ekranı açılır. Sürüm kodu CI çalışma
 * numarası + 1 olduğu için N, kurulu sürümün koduyla karşılaştırılır.
 */
object Guncelleme {
    data class Surum(val numara: Int, val apk: String, val boyut: Long)

    fun kuruluNumara(c: Context): Long = try {
        c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode - 1
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    fun sonSurum(): Surum? = try {
        val c = URL("https://api.github.com/repos/AhmetYuksel5/Myuygulama/releases?per_page=40")
            .openConnection() as HttpURLConnection
        c.connectTimeout = 10_000
        c.readTimeout = 15_000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "my-asistan")
        val metin = c.inputStream.bufferedReader().use { it.readText() }
        c.disconnect()
        val a = JSONArray(metin)
        var bulunan: Surum? = null
        for (i in 0 until a.length()) {
            val r = a.getJSONObject(i)
            val n = Regex("^ofis-v0\\.(\\d+)$").find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: continue
            val dosyalar = r.optJSONArray("assets") ?: continue
            var apk: org.json.JSONObject? = null
            for (j in 0 until dosyalar.length()) {
                val d = dosyalar.getJSONObject(j)
                if (d.optString("name").endsWith(".apk")) apk = d
            }
            if (apk == null) continue
            if (bulunan == null || n > bulunan.numara) {
                bulunan = Surum(n, apk.optString("browser_download_url"), apk.optLong("size"))
            }
        }
        bulunan
    } catch (e: Exception) {
        null
    }

    /** Kurulu sürümden yenisi varsa o, yoksa null. Ağ işi; arka planda çağrılmalı. */
    fun yeniVarMi(c: Context): Surum? = sonSurum()?.takeIf { it.numara > kuruluNumara(c) }

    /**
     * Android başka bir APK kurmaya ancak kullanıcı bu uygulamaya bir kez
     * izin verirse müsaade ediyor.
     */
    fun kurmaIzniVar(c: Context) = c.packageManager.canRequestPackageInstalls()

    fun kurmaIzniNiyeti(c: Context) =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${c.packageName}"))

    /**
     * İndirir ve kurulum ekranını açar. [ilerleme] 0..1, [bitti] hata metni
     * ya da null; ikisi de ana iş parçacığında.
     */
    fun indirVeKur(c: Context, s: Surum, ilerleme: (Float) -> Unit, bitti: (String?) -> Unit) {
        val app = c.applicationContext
        val ana = Handler(Looper.getMainLooper())
        Thread {
            val hedef = File(app.cacheDir, "my-asistan-${s.numara}.apk")
            val hata = try {
                val k = URL(s.apk).openConnection() as HttpURLConnection
                k.instanceFollowRedirects = true
                k.connectTimeout = 30_000
                k.readTimeout = 60_000
                k.inputStream.use { giris ->
                    hedef.outputStream().use { cikis ->
                        val tampon = ByteArray(64 * 1024)
                        var toplam = 0L
                        var son = 0L
                        while (true) {
                            val n = giris.read(tampon)
                            if (n <= 0) break
                            cikis.write(tampon, 0, n)
                            toplam += n
                            if (s.boyut > 0 && toplam - son > 100_000) {
                                son = toplam
                                val o = (toplam.toFloat() / s.boyut).coerceIn(0f, 1f)
                                ana.post { ilerleme(o) }
                            }
                        }
                    }
                }
                k.disconnect()
                val uri = FileProvider.getUriForFile(app, "${app.packageName}.dosyalar", hedef)
                app.startActivity(
                    Intent(Intent.ACTION_VIEW)
                        .setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
                null
            } catch (e: Exception) {
                hedef.delete()
                "İndirilemedi"
            }
            ana.post { bitti(hata) }
        }.start()
    }
}
