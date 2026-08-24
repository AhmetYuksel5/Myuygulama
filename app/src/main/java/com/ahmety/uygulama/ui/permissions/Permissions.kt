package com.ahmety.uygulama.ui.permissions

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.ahmety.uygulama.feature.gestures.EdgeGestureService
import com.ahmety.uygulama.feature.gestures.QuickCursorService
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat

/**
 * Uygulamanın çalışması için gereken izinler.
 *
 * Bu uygulama Play Store'a çıkmadığı için mağazanın kısıtladığı izinleri
 * (tüm dosyalara erişim, tam zamanlı alarm, pil kısıtlamasından muafiyet)
 * serbestçe isteyebiliyoruz. Yine de her izin, ne işe yaradığı yazılı olarak
 * ve tek tek isteniyor; toptan bir "hepsine izin ver" düğmesi yok.
 */
enum class PermissionKind {
    /** Sistem diyaloğuyla istenen normal çalışma zamanı izni. */
    RUNTIME,

    /** Ayarlar ekranına yönlendirerek verilen özel izin. */
    SPECIAL,
}

data class PermissionSpec(
    val id: String,
    val title: String,
    val rationale: String,
    val kind: PermissionKind,
    /** RUNTIME izinler için istenecek Android izin adları. */
    val manifestPermissions: List<String> = emptyList(),
    val isGranted: (Context) -> Boolean,
    /** SPECIAL izinler için ayarlar ekranını açan intent. */
    val settingsIntent: ((Context) -> Intent)? = null,
)

fun permissionSpecs(): List<PermissionSpec> = buildList {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(
            PermissionSpec(
                id = "notifications",
                title = "Bildirimler",
                rationale = "Alışkanlık hatırlatıcıları ve takvim uyarıları için.",
                kind = PermissionKind.RUNTIME,
                manifestPermissions = listOf(Manifest.permission.POST_NOTIFICATIONS),
                isGranted = { it.hasPermission(Manifest.permission.POST_NOTIFICATIONS) },
            ),
        )
    }

    add(
        PermissionSpec(
            id = "calendar",
            title = "Takvim",
            rationale = "Google takvimin cihazdaki takvim sağlayıcısı üzerinden okunup " +
                "yazılabilmesi için. Bu sayede ayrı bir hesap girişine gerek kalmıyor.",
            kind = PermissionKind.RUNTIME,
            manifestPermissions = listOf(
                Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR,
            ),
            isGranted = {
                it.hasPermission(Manifest.permission.READ_CALENDAR) &&
                    it.hasPermission(Manifest.permission.WRITE_CALENDAR)
            },
        ),
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        add(
            PermissionSpec(
                id = "exact_alarm",
                title = "Tam zamanlı alarm",
                rationale = "Hatırlatıcıların dakikası dakikasına çalması için. " +
                    "İzin verilmezse bildirimler gecikmeli gelebilir.",
                kind = PermissionKind.SPECIAL,
                isGranted = { context ->
                    val alarmManager = context.getSystemService(AlarmManager::class.java)
                    alarmManager?.canScheduleExactAlarms() == true
                },
                settingsIntent = { context ->
                    Intent(
                        Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                        Uri.fromParts("package", context.packageName, null),
                    )
                },
            ),
        )
    }

    add(
        PermissionSpec(
            id = "battery",
            title = "Pil kısıtlamasından muaf tut",
            rationale = "Arka plandaki yedekleme, haber çekme ve hatırlatıcıların " +
                "sistem tarafından uykuya alınmaması için.",
            kind = PermissionKind.SPECIAL,
            isGranted = { context ->
                val powerManager = context.getSystemService(PowerManager::class.java)
                powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
            },
            settingsIntent = { context ->
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.fromParts("package", context.packageName, null),
                )
            },
        ),
    )

    // Erişilebilirlik servisleri. Sistem bunları kendiliğinden kapatabiliyor
    // (üreticinin pil temizleyicisi, uzun süre kullanılmayan uygulama), o
    // yüzden durumlarını burada görüp tek dokunuşla geri açabilmek gerekiyor.
    add(
        PermissionSpec(
            id = "edge_gestures",
            title = "Kenar hareketleri",
            rationale = "Ekranın kenarından kaydırarak son uygulamalar ve bildirim " +
                "paneli. Erişilebilirlik servisi olarak çalışıyor.",
            kind = PermissionKind.SPECIAL,
            isGranted = { it.isAccessibilityServiceOn(EdgeGestureService::class.java.name) },
            settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        ),
    )

    add(
        PermissionSpec(
            id = "quick_cursor",
            title = "Tek elle imleç",
            rationale = "Ulaşılamayan köşelere basmak için sanal imleç. " +
                "Erişilebilirlik servisi olarak çalışıyor.",
            kind = PermissionKind.SPECIAL,
            isGranted = { it.isAccessibilityServiceOn(QuickCursorService::class.java.name) },
            settingsIntent = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) },
        ),
    )

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        add(
            PermissionSpec(
                id = "auto_revoke",
                title = "Kullanılmayınca izinleri kaldırma",
                rationale = "Android, uzun süre açılmayan uygulamanın izinlerini " +
                    "kendiliğinden geri alıyor. Bu telefonu seyrek kullanıyorsan " +
                    "erişilebilirlik servisleri de bu yüzden düşebilir. Kapalı " +
                    "olması gerekiyor: ayarlarda \"kullanılmıyorsa izinleri " +
                    "kaldır\" anahtarını kapat.",
                kind = PermissionKind.SPECIAL,
                isGranted = { context ->
                    runCatching { context.packageManager.isAutoRevokeWhitelisted }
                        .getOrDefault(false)
                },
                settingsIntent = { context ->
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null),
                    )
                },
            ),
        )
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        add(
            PermissionSpec(
                id = "all_files",
                title = "Tüm dosyalara erişim",
                rationale = "PDF'lerin, kasadaki dosyaların ve yedeklerin telefonda " +
                    "görünür bir klasörde (Merkez/) tutulabilmesi için. Uygulamayı " +
                    "silsen bile dosyaların yerinde kalır.",
                kind = PermissionKind.SPECIAL,
                isGranted = { Environment.isExternalStorageManager() },
                settingsIntent = { context ->
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null),
                    )
                },
            ),
        )
    }
}

/**
 * Erişilebilirlik servisi açık mı.
 *
 * Sistem açık servisleri iki nokta üst üste ile ayrılmış tek bir dizide
 * tutuyor; kendi servisimizi orada arıyoruz. Adlar karıştırılmadığı için
 * (R8 kuralında `-dontobfuscate`) sınıf adı çalışma zamanında da aynı.
 */
fun Context.isAccessibilityServiceOn(serviceClassName: String): Boolean {
    val enabled = Settings.Secure.getString(
        contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ).orEmpty()
    val target = "$packageName/$serviceClassName"
    return enabled.split(':').any { it.trim().equals(target, ignoreCase = true) }
}

fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
