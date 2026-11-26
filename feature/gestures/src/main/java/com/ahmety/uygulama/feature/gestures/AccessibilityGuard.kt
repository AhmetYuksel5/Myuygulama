package com.ahmety.uygulama.feature.gestures

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.app.NotificationCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Erişilebilirlik servisinin kapanmasına karşı bekçi.
 *
 * Servis bazen kendiliğinden kapanıyor: ya bir hatayla çöküyor ve Android
 * onu kapatıyor, ya da telefonun güç yönetimi uygulamayı öldürüyor. Bir
 * uygulama erişilebilirlik iznini kendine geri veremez; bu yüzden:
 *
 * - Bildirim süzgeci servisi (sürekli bağlı) ayarı izliyor; servis
 *   kapanınca dokununca ayarı açan bir bildirim çıkıyor.
 * - Bilgisayardan bir kez "güvenli ayarları yazma" izni verildiyse
 *   bekçi servisi kendisi geri açıyor, bildirim gerekmiyor.
 * - Servisin içindeki çökmeler bir dosyaya yazılıyor; sebep sonradan
 *   görülebilsin.
 */
object AccessibilityGuard {

    private const val PREFS = "merkez_erisilebilirlik_bekcisi"

    /** Servis en az bir kez bağlandı: kapanırsa uyarmaya değer. */
    private const val KEY_EXPECTED = "expected"
    private const val KEY_RESTORED = "restored_count"

    /** Kullanıcının anahtarı: kapalıyken kasıtlı kapatılan servis geri açılmıyor. */
    private const val KEY_GUARD = "guard"

    private const val CHANNEL = "erisilebilirlik_bekcisi"
    private const val NOTIFICATION_ID = 7301
    private const val CRASH_FILE = "son_cokme.txt"
    private const val WRITE_SECURE = "android.permission.WRITE_SECURE_SETTINGS"

    /** Bilgisayardan bir kez verilecek iznin komutu; ekranda kopyalanabiliyor. */
    const val ADB_COMMAND =
        "adb shell pm grant com.ahmety.uygulama android.permission.WRITE_SECURE_SETTINGS"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun component(context: Context) =
        "${context.packageName}/${QuickCursorService::class.java.name}"

    /** Servis bağlandı; bundan sonra kapanırsa bekçi devreye giriyor. */
    fun onServiceConnected(context: Context) {
        prefs(context).edit().putBoolean(KEY_EXPECTED, true).apply()
        cancelNotice(context)
        if (prefs(context).getBoolean(KEY_KEEP_ADB_TIMEOUT, false) && canRestore(context)) {
            runCatching {
                Settings.Global.putLong(context.contentResolver, KEY_GLOBAL_ADB_TIMEOUT, 0L)
            }
        }
    }

    fun isGuarded(context: Context): Boolean = prefs(context).getBoolean(KEY_GUARD, true)

    fun setGuarded(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_GUARD, value).apply()
        if (!value) cancelNotice(context)
    }

    fun canRestore(context: Context): Boolean =
        context.checkSelfPermission(WRITE_SECURE) == PackageManager.PERMISSION_GRANTED

    /** Bekçinin servisi kaç kez kendisi geri açtığı. */
    fun restoredCount(context: Context): Int = prefs(context).getInt(KEY_RESTORED, 0)

    /**
     * Android'in 7 günlük ADB yetki zaman aşımı kapalı mı (`adb_allowed_connection_time = 0`).
     *
     * Sıfır yapıldığında bilgisayarla eşleştirilmiş kablosuz hata ayıklama yetkisi
     * süre dolduğu için kendiliğinden iptal edilmez.
     */
    fun isAdbTimeoutDisabled(context: Context): Boolean = runCatching {
        Settings.Global.getLong(
            context.contentResolver,
            KEY_GLOBAL_ADB_TIMEOUT,
            DEFAULT_ADB_TIMEOUT_MS,
        ) == 0L
    }.getOrDefault(false)

    fun setAdbTimeoutDisabled(context: Context, disable: Boolean): Boolean {
        if (!canRestore(context)) return false
        val ok = runCatching {
            Settings.Global.putLong(
                context.contentResolver,
                KEY_GLOBAL_ADB_TIMEOUT,
                if (disable) 0L else DEFAULT_ADB_TIMEOUT_MS,
            )
            true
        }.getOrDefault(false)
        if (ok) {
            prefs(context).edit().putBoolean(KEY_KEEP_ADB_TIMEOUT, disable).apply()
        }
        return ok
    }

    fun isWirelessAdbOn(context: Context): Boolean = runCatching {
        Settings.Global.getInt(context.contentResolver, KEY_GLOBAL_ADB_WIFI, 0) == 1
    }.getOrDefault(false)

    fun setWirelessAdb(context: Context, enabled: Boolean): Boolean {
        if (!canRestore(context)) return false
        return runCatching {
            Settings.Global.putInt(context.contentResolver, KEY_GLOBAL_ADB_WIFI, if (enabled) 1 else 0)
            true
        }.getOrDefault(false)
    }

    private const val KEY_KEEP_ADB_TIMEOUT = "keep_adb_timeout_zero"
    private const val KEY_GLOBAL_ADB_TIMEOUT = "adb_allowed_connection_time"
    private const val KEY_GLOBAL_ADB_WIFI = "adb_wifi_enabled"
    private const val DEFAULT_ADB_TIMEOUT_MS = 7 * 24 * 60 * 60 * 1_000L

    /**
     * Ayar değişince ve izleyen servis bağlanınca çağrılıyor. Servis açıksa
     * bildirimi kaldırıyor; beklendiği hâlde kapalıysa geri açıyor ya da
     * bildirim gösteriyor.
     */
    fun check(context: Context) {
        if (QuickCursorSettings.isServiceEnabled(context)) {
            cancelNotice(context)
            return
        }
        if (!prefs(context).getBoolean(KEY_EXPECTED, false) || !isGuarded(context)) return
        if (restore(context)) return
        showNotice(context)
    }

    /** Servisi ayara geri yazar; izin yoksa false. */
    private fun restore(context: Context): Boolean {
        if (!canRestore(context)) return false
        return runCatching {
            val resolver = context.contentResolver
            val current = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                .orEmpty().split(':').filter { it.isNotBlank() }
            val next = (current + component(context)).distinct().joinToString(":")
            Settings.Secure.putString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, next)
            Settings.Secure.putInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            val p = prefs(context)
            p.edit().putInt(KEY_RESTORED, p.getInt(KEY_RESTORED, 0) + 1).apply()
            true
        }.getOrDefault(false)
    }

    private fun showNotice(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Erişilebilirlik bekçisi", NotificationManager.IMPORTANCE_HIGH),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val crash = lastCrash(context)?.lineSequence()?.take(2)?.joinToString(" · ")
        val text = "İmleç, kenar hareketleri ve sesli yazma durdu. Açmak için dokun." +
            if (crash != null) "\nSon çökme: $crash" else ""
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Merkez erişilebilirlik kapandı")
            .setContentText("Açmak için dokun.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private fun cancelNotice(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(NOTIFICATION_ID)
    }

    /** Erişilebilirlik ayarını izleyen gözlemci; [stop] ile bırakılıyor. */
    fun watch(context: Context): ContentObserver {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) = check(context)
        }
        context.contentResolver.registerContentObserver(
            Settings.Secure.getUriFor(Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES),
            false,
            observer,
        )
        check(context)
        return observer
    }

    fun stop(context: Context, observer: ContentObserver?) {
        observer?.let { runCatching { context.contentResolver.unregisterContentObserver(it) } }
    }

    // ---- Çökme kaydı ----

    @Volatile
    private var recording = false

    /**
     * Yakalanmamış hataları dosyaya yazar, sonra sistemin kendi işleyicisine
     * bırakır: hata gizlenmiyor, yalnız sebebi kalıyor.
     */
    fun recordCrashes(context: Context) {
        if (recording) return
        recording = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val time = SimpleDateFormat("d MMMM HH:mm:ss", Locale.forLanguageTag("tr")).format(Date())
                File(app.filesDir, CRASH_FILE).writeText(
                    "$time\n${error.javaClass.simpleName}: ${error.message}\n\n" +
                        android.util.Log.getStackTraceString(error).take(6000),
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    fun lastCrash(context: Context): String? =
        runCatching { File(context.filesDir, CRASH_FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clearCrash(context: Context) {
        runCatching { File(context.filesDir, CRASH_FILE).delete() }
    }

    // ---- Kapanma geçmişi ----

    /**
     * Android'in Merkez'in her kapanışı için tuttuğu kayıt: ne zaman ve
     * neden (çökme, bellek, zorla durdurma, güç yönetimi…). Servisin
     * kapanma sebebi tahminle bulunamadı; bu kayıt kesin söylüyor.
     */
    fun exitHistory(context: Context, limit: Int = 12): String {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.R) return ""
        val manager = context.getSystemService(android.app.ActivityManager::class.java) ?: return ""
        val format = SimpleDateFormat("d MMM HH:mm:ss", Locale.forLanguageTag("tr"))
        return runCatching {
            manager.getHistoricalProcessExitReasons(context.packageName, 0, limit).joinToString("\n") { info ->
                buildString {
                    append(format.format(Date(info.timestamp))).append(" · ")
                    append(reasonName(info.reason))
                    append(" · ").append(importanceName(info.importance))
                    append(" · ").append(info.pss / 1024).append(" MB")
                    if (info.status != 0) append(" · kod ").append(info.status)
                    info.description?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it.take(160)) }
                }
            }
        }.getOrDefault("")
    }

    private fun reasonName(reason: Int): String = when (reason) {
        android.app.ApplicationExitInfo.REASON_CRASH -> "çökme"
        android.app.ApplicationExitInfo.REASON_CRASH_NATIVE -> "yerel çökme"
        android.app.ApplicationExitInfo.REASON_ANR -> "yanıt vermedi"
        android.app.ApplicationExitInfo.REASON_LOW_MEMORY -> "bellek az"
        android.app.ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "fazla kaynak"
        android.app.ApplicationExitInfo.REASON_USER_REQUESTED -> "zorla durduruldu"
        android.app.ApplicationExitInfo.REASON_USER_STOPPED -> "kullanıcı durdurdu"
        android.app.ApplicationExitInfo.REASON_SIGNALED -> "sistem öldürdü"
        android.app.ApplicationExitInfo.REASON_EXIT_SELF -> "kendisi çıktı"
        android.app.ApplicationExitInfo.REASON_DEPENDENCY_DIED -> "bağlı süreç öldü"
        android.app.ApplicationExitInfo.REASON_PERMISSION_CHANGE -> "izin değişti"
        android.app.ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "güncellendi"
        android.app.ApplicationExitInfo.REASON_PACKAGE_STATE_CHANGE -> "paket durumu değişti"
        android.app.ApplicationExitInfo.REASON_FREEZER -> "donduruldu"
        android.app.ApplicationExitInfo.REASON_INITIALIZATION_FAILURE -> "başlatılamadı"
        android.app.ApplicationExitInfo.REASON_OTHER -> "diğer"
        else -> "bilinmiyor ($reason)"
    }

    private fun importanceName(importance: Int): String = when {
        importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> "önde"
        importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_PERCEPTIBLE -> "görünür servis"
        importance <= android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> "servis"
        else -> "arka plan"
    }
}
