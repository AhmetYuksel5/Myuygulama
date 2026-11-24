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
}
