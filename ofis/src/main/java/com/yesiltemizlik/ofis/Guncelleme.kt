package com.yesiltemizlik.ofis

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Uygulama içi güncelleme: GitHub'daki "ofis-v0.N" etiketli son sürüm
 * indirilir ve Android'in kurulum ekranı açılır. Sürüm kodu CI çalışma
 * numarası + 1 olduğu için N, kurulu sürümün koduyla karşılaştırılır.
 */
object Guncelleme {
    /** [numara] gizli sayaç (etiketteki derleme no), [ad] görünen sürüm (1.x). */
    data class Surum(val numara: Int, val apk: String, val boyut: Long, val ad: String)

    fun kuruluNumara(c: Context): Long = try {
        c.packageManager.getPackageInfo(c.packageName, 0).longVersionCode - 1
    } catch (e: Exception) {
        Long.MAX_VALUE
    }

    /** Sürüm geçmişi satırı: görünen ad, yayın tarihi, başlık ve maddeler (sürüm notu = commit iletisi). */
    data class Gecmis(val numara: Int, val ad: String, val tarih: String, val baslik: String, val maddeler: List<String>)

    private val ETIKET = Regex("^ofis-v0\\.(\\d+)$")

    /**
     * Yalnız My Asistan sürümleri, yeniden eskiye. Depoda Merkez sürümleri de
     * var ve çok daha sık çıkıyor; ilk sayfada My Asistan hiç kalmayabilir, bu
     * yüzden 100'erli en çok 4 sayfa okunur.
     */
    private fun ofisSurumleri(): List<org.json.JSONObject> {
        val out = ArrayList<org.json.JSONObject>()
        for (sayfa in 1..4) {
            val c = URL("https://api.github.com/repos/AhmetYuksel5/Myuygulama/releases?per_page=100&page=$sayfa")
                .openConnection() as HttpURLConnection
            c.connectTimeout = 10_000
            c.readTimeout = 15_000
            c.setRequestProperty("Accept", "application/vnd.github+json")
            c.setRequestProperty("User-Agent", "my-asistan")
            val a = JSONArray(c.inputStream.bufferedReader().use { it.readText() })
            c.disconnect()
            for (i in 0 until a.length()) {
                val r = a.getJSONObject(i)
                if (ETIKET.matches(r.optString("tag_name"))) out += r
            }
            if (a.length() < 100) break
        }
        return out.sortedByDescending { numara(it) }
    }

    private fun numara(r: org.json.JSONObject) =
        ETIKET.find(r.optString("tag_name"))?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Görünen ad sürüm başlığından ("My Asistan 1.3"); eski düzende yoksa sayaç. */
    private fun gorunenAd(r: org.json.JSONObject) =
        Regex("(\\d+\\.\\d+)\\s*$").find(r.optString("name"))?.groupValues?.get(1) ?: "0.${numara(r)}"

    fun sonSurum(): Surum? = try {
        ofisSurumleri().firstNotNullOfOrNull { r ->
            val dosyalar = r.optJSONArray("assets") ?: return@firstNotNullOfOrNull null
            var apk: org.json.JSONObject? = null
            for (j in 0 until dosyalar.length()) {
                val d = dosyalar.getJSONObject(j)
                if (d.optString("name").endsWith(".apk")) apk = d
            }
            apk?.let { Surum(numara(r), it.optString("browser_download_url"), it.optLong("size"), gorunenAd(r)) }
        }
    } catch (e: Exception) {
        null
    }

    /** Sürüm geçmişi; ağ yoksa null. Künye satırları (yardımcı yazar, oturum) atılır. */
    fun gecmis(): List<Gecmis>? = try {
        val ay = listOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık")
        ofisSurumleri().map { r ->
            val satirlar = r.optString("body").lines().map { it.trimEnd() }
                .filterNot { it.startsWith("Co-Authored-By") || it.startsWith("Claude-Session") }
            val baslik = satirlar.firstOrNull { it.isNotBlank() }?.trim() ?: ""
            // Madde "- " ile başlar; sarılıp alt satıra geçen devamı aynı maddeye eklenir.
            val maddeler = ArrayList<String>()
            for (s in satirlar.dropWhile { it.isBlank() }.drop(1)) {
                val t = s.trim()
                when {
                    t.startsWith("- ") -> maddeler += t.removePrefix("- ")
                    t.isNotEmpty() && maddeler.isNotEmpty() -> maddeler[maddeler.lastIndex] = maddeler.last() + " " + t
                }
            }
            val y = r.optString("published_at")
            val tarih = if (y.length >= 10) {
                "${y.substring(8, 10).toInt()} ${ay[y.substring(5, 7).toInt() - 1]} ${y.substring(0, 4)}"
            } else {
                ""
            }
            Gecmis(numara(r), gorunenAd(r), tarih, baslik.removePrefix("My Asistan: "), maddeler)
        }
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
     * Önbellekli denetim: kart her açıldığında GitHub'a gitmesin (giriş
     * yapılmamış istekte saatte 60 sınırı var). [azamiYas] kadar eski sonuç
     * yeniden kullanılır. Ağ işi; arka planda çağrılmalı.
     */
    fun bilinenYeni(c: Context, azamiYas: Long = 3_600_000L): Surum? {
        val p = c.getSharedPreferences("guncelleme", Context.MODE_PRIVATE)
        val simdi = System.currentTimeMillis()
        if (simdi - p.getLong("ts", 0L) > azamiYas) {
            val s = sonSurum()
            if (s != null) {
                p.edit().putLong("ts", simdi).putInt("numara", s.numara).putString("apk", s.apk)
                    .putLong("boyut", s.boyut).putString("ad", s.ad).apply()
            }
        }
        val n = p.getInt("numara", 0)
        val apk = p.getString("apk", "") ?: ""
        if (n <= kuruluNumara(c) || apk.isEmpty()) return null
        return Surum(n, apk, p.getLong("boyut", 0L), p.getString("ad", null) ?: "0.$n")
    }

    /**
     * Sormadan kurulabilir mi: Android 12+ bir uygulamanın kendini onaysız
     * güncellemesine, paketin son kurucusu kendisiyse izin veriyor. İlk
     * kurulum dosya yöneticisinden yapıldığı için ilk güncelleme bir kez
     * onay ister; ondan sonraki her güncelleme sessiz gider.
     */
    fun sessizKurulabilir(c: Context): Boolean = Build.VERSION.SDK_INT >= 31 && kurmaIzniVar(c) && try {
        c.packageManager.getInstallSourceInfo(c.packageName).installingPackageName == c.packageName
    } catch (e: Exception) {
        false
    }

    private val calisiyor = AtomicBoolean(false)

    /**
     * İndirir ve kurar. [ilerleme] 0..1, [bitti] hata metni ya da null; ikisi
     * de ana iş parçacığında. [arkaplan] doğruysa onay gerekirse ekrana bir şey
     * açılmaz, bildirim düşer.
     */
    fun indirVeKur(
        c: Context,
        s: Surum,
        arkaplan: Boolean = false,
        ilerleme: (Float) -> Unit = {},
        bitti: (String?) -> Unit = {},
    ) {
        val app = c.applicationContext
        val ana = Handler(Looper.getMainLooper())
        if (!calisiyor.compareAndSet(false, true)) {
            ana.post { bitti("Güncelleme zaten iniyor") }
            return
        }
        Thread {
            val hedef = File(app.cacheDir, "my-asistan-${s.ad}-${s.numara}.apk")
            val hata = try {
                if (!(hedef.exists() && s.boyut > 0 && hedef.length() == s.boyut)) indir(s, hedef) { o -> ana.post { ilerleme(o) } }
                ana.post { ilerleme(1f) }
                kur(app, hedef, arkaplan)
                null
            } catch (e: Exception) {
                hedef.delete()
                "İndirilemedi"
            } finally {
                calisiyor.set(false)
            }
            ana.post { bitti(hata) }
        }.start()
    }

    /** Kart kapanınca ya da uygulamadan çıkılınca: yeni sürüm varsa ve sormadan kurulabiliyorsa kur. */
    fun arkaplandaGuncelle(c: Context) {
        val app = c.applicationContext
        Thread {
            if (!sessizKurulabilir(app)) return@Thread
            val s = bilinenYeni(app) ?: return@Thread
            indirVeKur(app, s, arkaplan = true)
        }.start()
    }

    private fun indir(s: Surum, hedef: File, ilerleme: (Float) -> Unit) {
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
                        ilerleme((toplam.toFloat() / s.boyut).coerceIn(0f, 1f))
                    }
                }
            }
        }
        k.disconnect()
    }

    /**
     * PackageInstaller oturumuyla kurar; böylece uygulama kendi kurucusu olur
     * ve sonraki güncellemeler sorulmadan gider. Oturum açılamazsa (bazı MIUI
     * sürümleri) eski yol: kurulum ekranı.
     */
    private fun kur(c: Context, apk: File, arkaplan: Boolean) {
        try {
            val kurucu = c.packageManager.packageInstaller
            val p = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            p.setAppPackageName(c.packageName)
            if (Build.VERSION.SDK_INT >= 31) {
                p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = kurucu.createSession(p)
            kurucu.openSession(id).use { oturum ->
                oturum.openWrite("my-asistan.apk", 0, apk.length()).use { cikis ->
                    apk.inputStream().use { it.copyTo(cikis) }
                    oturum.fsync(cikis)
                }
                val sonuc = Intent(c, KurulumAlicisi::class.java).putExtra(KurulumAlicisi.EK_ARKAPLAN, arkaplan)
                // Sistem sonuca bilgi ekliyor; değiştirilebilir olmak zorunda.
                val pi = PendingIntent.getBroadcast(
                    c, id, sonuc, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                oturum.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            if (arkaplan) throw e
            val uri = FileProvider.getUriForFile(c, "${c.packageName}.dosyalar", apk)
            c.startActivity(
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, "application/vnd.android.package-archive")
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }
}
