package com.yesiltemizlik.ofis

import android.Manifest
import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/**
 * Bir izin satırı. [calisma] doluysa izin uygulama içinden istenir; boşsa
 * [ayar] ilgili ayar sayfasını açar. [durum] null dönerse sistem durumu
 * söylemiyor demektir (MIUI otomatik başlatma gibi), satır yine de gösterilir.
 */
class Izin(
    val ad: String,
    val not: String = "",
    val calisma: List<String> = emptyList(),
    val ayar: ((Context) -> Intent)? = null,
    val durum: (Context) -> Boolean?,
)

val miui: Boolean
    get() = Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true) ||
        Build.BRAND.equals("Redmi", ignoreCase = true) || Build.BRAND.equals("POCO", ignoreCase = true)

private fun Context.izinVar(ad: String) = checkSelfPermission(ad) == PackageManager.PERMISSION_GRANTED

private fun uygulamaAyari(c: Context) =
    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${c.packageName}"))

/** İlk niyet telefonda karşılanıyorsa o, yoksa uygulamanın ayar sayfası. */
private fun ilkAcilan(c: Context, vararg niyetler: Intent): Intent =
    niyetler.firstOrNull { it.resolveActivity(c.packageManager) != null } ?: uygulamaAyari(c)

/**
 * MIUI kendi izinlerini AppOps'ta tutuyor; gizli yöntemle okunuyor. Okunamazsa
 * null (bilinmiyor) döner, satır "Ayarla" düğmesiyle kalır.
 * 10008 otomatik başlatma, 10020 kilit ekranında göster, 10021 arka planda pencere aç.
 */
private fun miuiOp(c: Context, op: Int): Boolean? = try {
    val ops = c.getSystemService(AppOpsManager::class.java)
    val m = AppOpsManager::class.java.getMethod(
        "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, String::class.java
    )
    (m.invoke(ops, op, Process.myUid(), c.packageName) as Int) == AppOpsManager.MODE_ALLOWED
} catch (e: Exception) {
    null
}

private fun miuiIzinEkrani(c: Context) = ilkAcilan(
    c,
    Intent("miui.intent.action.APP_PERM_EDITOR")
        .setClassName("com.miui.securitycenter", "com.miui.permcenter.permissions.PermissionsEditorActivity")
        .putExtra("extra_pkgname", c.packageName),
    Intent("miui.intent.action.APP_PERM_EDITOR").putExtra("extra_pkgname", c.packageName),
)

fun Context.erisimAcik(): Boolean {
    val acik = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
    return acik.split(':').any { it.startsWith("$packageName/") }
}

fun izinler(): List<Izin> = buildList {
    add(Izin("Telefon durumu", "Görüşmenin bittiği an", listOf(Manifest.permission.READ_PHONE_STATE)) {
        it.izinVar(Manifest.permission.READ_PHONE_STATE)
    })
    add(Izin("Arama kaydı", "Numara, süre, yön", listOf(Manifest.permission.READ_CALL_LOG)) {
        it.izinVar(Manifest.permission.READ_CALL_LOG)
    })
    if (Build.VERSION.SDK_INT >= 33) {
        add(Izin("Bildirim", "Kart açılamazsa yedek", listOf(Manifest.permission.POST_NOTIFICATIONS)) {
            it.izinVar(Manifest.permission.POST_NOTIFICATIONS)
        })
    }
    add(Izin("Diğer uygulamaların üzerinde göster", "Kartın kendiliğinden açılması",
        ayar = { Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${it.packageName}")) }) {
        Settings.canDrawOverlays(it)
    })
    add(Izin("Pil kısıtlaması yok", "Arka planda kapanmasın",
        ayar = { Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${it.packageName}")) }) {
        it.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(it.packageName)
    })
    add(Izin("Erişilebilirlik", "İzin verilmiyorsa: Uygulama bilgisi › sağ üst menü › Kısıtlı ayarlara izin ver",
        ayar = { Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS) }) {
        it.erisimAcik()
    })
    if (miui) {
        add(Izin("Otomatik başlatma", "MIUI: kapalıyken görüşme yakalanmaz",
            ayar = {
                ilkAcilan(
                    it,
                    Intent().setComponent(
                        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity")
                    ),
                )
            }) { miuiOp(it, 10008) })
        add(Izin("Arka planda açılır pencere", "MIUI: Diğer izinler › Arka planda çalışırken açılır pencereleri göster",
            ayar = ::miuiIzinEkrani) { miuiOp(it, 10021) })
        add(Izin("Kilit ekranında göster", "MIUI: Diğer izinler › Kilit ekranında göster",
            ayar = ::miuiIzinEkrani) { miuiOp(it, 10020) })
    }
}
