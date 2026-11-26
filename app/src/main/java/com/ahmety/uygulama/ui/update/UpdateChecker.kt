package com.ahmety.uygulama.ui.update

import android.content.Context
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

data class UpdateInfo(
    val versionName: String,
    val versionCode: Long,
    val downloadUrl: String,
    val notes: String,
    val sizeBytes: Long,
)

/**
 * Play Store'da olmadığımız için güncellemeyi kendimiz yönetiyoruz:
 * GitHub Release'lerine bakıyor, yenisi varsa APK'yı indirip kurulum
 * ekranını açıyoruz.
 *
 * Bunun çalışabilmesi için APK'ların **sabit bir anahtarla** imzalanması şart.
 * Farklı imzalı bir APK Android tarafından güncelleme sayılmaz; kullanıcı
 * uygulamayı silmek ve izinleri baştan vermek zorunda kalır.
 */
@Singleton
class UpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    fun currentVersionCode(): Long = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        info.longVersionCode
    }.getOrDefault(0L)

    fun currentVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")

    /** @return yeni sürüm varsa bilgisi, yoksa null */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) { checkLatest()?.update }

    /**
     * Son sürüme bakar. null: GitHub'a ulaşılamadı; [Latest.update] null ise
     * ulaşıldı ama yeni sürüm yok. Önbellek ikisini ayırt etsin diye sarılı:
     * bağlantı yokken "yeni sürüm yok" diye önbelleği silmesin.
     */
    private class Latest(val update: UpdateInfo?)

    private fun checkLatest(): Latest? {
        val json = fetch(RELEASES_URL) ?: return null
        val release = runCatching { JSONObject(json) }.getOrNull() ?: return null
        return Latest(latestFrom(release))
    }

    private fun latestFrom(release: JSONObject): UpdateInfo? {
        val tag = release.optString("tag_name")
        val remoteBuild = tag.substringAfterLast('.').toIntOrNull() ?: return null
        val remoteVersionCode = (1 + remoteBuild).toLong()
        if (remoteVersionCode <= currentVersionCode()) return null

        val assets = release.optJSONArray("assets") ?: return null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk")) continue
            return UpdateInfo(
                versionName = tag.removePrefix("v"),
                versionCode = remoteVersionCode,
                downloadUrl = asset.optString("browser_download_url"),
                notes = release.optString("body").trim(),
                sizeBytes = asset.optLong("size"),
            )
        }
        return null
    }

    /**
     * APK'yı indirir. Uygulamanın kendi önbelleğine yazıyoruz; hem izin
     * gerektirmiyor hem de kurulumdan sonra sistem temizleyebiliyor.
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Float) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        val target = File(context.cacheDir, "merkez-${info.versionName}.apk")
        runCatching {
            val connection = (URL(info.downloadUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 30_000
                readTimeout = 60_000
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        total += read
                        if (info.sizeBytes > 0) {
                            onProgress((total.toFloat() / info.sizeBytes).coerceIn(0f, 1f))
                        }
                    }
                }
            }
            connection.disconnect()
            target
        }.getOrElse {
            target.delete()
            null
        }
    }

    /**
     * Kurulum oturumuyla kurar. Böylece Merkez paketin kurucusu oluyor ve
     * Android 12+ sonraki güncellemeleri sormadan kuruyor (My Asistan'daki
     * düzen). İlk güncelleme, dosya yöneticisinden kurulmuş olduğu için bir
     * kez onay ister. Oturum açılamazsa eski yol: kurulum ekranı.
     *
     * [background] doğruysa onay gerektiğinde ekrana bir şey açılmıyor,
     * bildirim düşüyor; kurulum ekranına düşme yolu da kapalı.
     */
    fun install(file: File, background: Boolean = false): Boolean {
        val opened = runCatching {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            val id = installer.createSession(params)
            installer.openSession(id).use { session ->
                session.openWrite("merkez.apk", 0, file.length()).use { out ->
                    file.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                val result = Intent(context, InstallResultReceiver::class.java)
                    .putExtra(InstallResultReceiver.EXTRA_BACKGROUND, background)
                // Sistem sonuca bilgi ekliyor; değiştirilebilir olmak zorunda.
                val pending = PendingIntent.getBroadcast(
                    context,
                    id,
                    result,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        }.isSuccess
        if (opened || background) return opened
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
        return false
    }

    /**
     * Sormadan kurulabilir mi: Android 12+ bir uygulamanın kendini onaysız
     * güncellemesine, paketin son kurucusu kendisiyse izin veriyor.
     */
    fun silentInstallable(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canInstallPackages() && runCatching {
            context.packageManager.getInstallSourceInfo(context.packageName).installingPackageName ==
                context.packageName
        }.getOrDefault(false)

    /**
     * Önbellekli denetim: her açılışta GitHub'a gitmesin (girişsiz istekte
     * saatte 60 sınırı var). [maxAgeMs] kadar eski sonuç yeniden kullanılıyor.
     */
    suspend fun knownUpdate(maxAgeMs: Long = 3_600_000L): UpdateInfo? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong("ts", 0L) > maxAgeMs) {
            val fresh = runCatching { checkLatest() }.getOrNull()
            if (fresh != null) {
                val edit = prefs.edit().putLong("ts", now)
                val info = fresh.update
                if (info == null) {
                    edit.remove("code")
                } else {
                    edit.putLong("code", info.versionCode)
                        .putString("name", info.versionName)
                        .putString("url", info.downloadUrl)
                        .putString("notes", info.notes)
                        .putLong("size", info.sizeBytes)
                }
                edit.apply()
            }
        }
        val code = prefs.getLong("code", 0L)
        val url = prefs.getString("url", null).orEmpty()
        if (code <= currentVersionCode() || url.isEmpty()) return@withContext null
        UpdateInfo(
            versionName = prefs.getString("name", null).orEmpty(),
            versionCode = code,
            downloadUrl = url,
            notes = prefs.getString("notes", null).orEmpty(),
            sizeBytes = prefs.getLong("size", 0L),
        )
    }

    /**
     * Uygulamadan çıkılınca ve bir özelliği kullanılınca: yeni sürüm varsa indir ve kur.
     *
     * Sormadan kurulabiliyorsa kuruluyor. Kurulamıyorsa (ilk güncelleme ya da
     * telefon onay istiyorsa) "güncelleme hazır" bildirimi düşüyor. Önceden
     * yalnız sormadan kurulabildiğinde çalışıyordu; güncellemeler elle
     * kurulduğu için bu hiç sağlanmadı ve ne kurulum ne bildirim geldi.
     */
    fun updateInBackground() {
        if (!busy.compareAndSet(false, true)) return
        backgroundScope.launch {
            try {
                val info = knownUpdate() ?: return@launch
                val silent = silentInstallable()
                // Aynı sürüm için bildirim art arda yağmasın.
                if (!silent && recentlyNotified(info.versionCode)) return@launch
                if (!canInstallPackages()) {
                    InstallResultReceiver.notifyAvailable(context, info.versionName)
                    markNotified(info.versionCode)
                    return@launch
                }
                val file = download(info) {} ?: return@launch
                val opened = install(file, background = true)
                if (!opened) InstallResultReceiver.notifyAvailable(context, info.versionName)
                if (!silent) markNotified(info.versionCode)
            } finally {
                busy.set(false)
            }
        }
    }

    private fun recentlyNotified(code: Long): Boolean {
        val prefs = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
        return prefs.getLong("notified_code", 0L) == code &&
            System.currentTimeMillis() - prefs.getLong("notified_at", 0L) < NOTIFY_EVERY_MS
    }

    private fun markNotified(code: Long) {
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE).edit()
            .putLong("notified_code", code).putLong("notified_at", System.currentTimeMillis()).apply()
    }

    private val busy = AtomicBoolean(false)
    private val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Android, bir uygulamanın başka bir uygulama kurmasına ancak kullanıcı
     * açıkça izin verirse müsaade eder. Bir kez verilir, sonra hep çalışır.
     */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesSettingsIntent(): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        Uri.fromParts("package", context.packageName, null),
    )

    private fun fetch(url: String): String? = runCatching {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val body = if (connection.responseCode in 200..299) {
            connection.inputStream.bufferedReader().use { it.readText() }
        } else {
            null
        }
        connection.disconnect()
        body
    }.getOrNull()

    private companion object {
        const val CACHE_PREFS = "guncelleme_onbellek"
        const val NOTIFY_EVERY_MS = 6 * 3_600_000L
        const val RELEASES_URL =
            "https://api.github.com/repos/AhmetYuksel5/Myuygulama/releases/latest"
    }
}
