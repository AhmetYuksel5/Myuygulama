package com.ahmety.uygulama.ui.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
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
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        val json = fetch(RELEASES_URL) ?: return@withContext null
        val release = runCatching { JSONObject(json) }.getOrNull() ?: return@withContext null

        val tag = release.optString("tag_name")
        val remoteBuild = tag.substringAfterLast('.').toIntOrNull() ?: return@withContext null
        val remoteVersionCode = (1 + remoteBuild).toLong()
        if (remoteVersionCode <= currentVersionCode()) return@withContext null

        val assets = release.optJSONArray("assets") ?: return@withContext null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            if (!name.endsWith(".apk")) continue
            return@withContext UpdateInfo(
                versionName = tag.removePrefix("v"),
                versionCode = remoteVersionCode,
                downloadUrl = asset.optString("browser_download_url"),
                notes = release.optString("body").trim(),
                sizeBytes = asset.optLong("size"),
            )
        }
        null
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

    /** Sistem kurulum ekranını açar. */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

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
        const val RELEASES_URL =
            "https://api.github.com/repos/AhmetYuksel5/Myuygulama/releases/latest"
    }
}
