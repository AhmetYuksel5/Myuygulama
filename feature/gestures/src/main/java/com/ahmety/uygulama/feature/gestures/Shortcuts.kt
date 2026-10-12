package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

/** Kısayolun ne yaptığı. */
enum class ShortcutKind { APP, CALL, WHATSAPP, SYSTEM }

/**
 * Çubuktaki tek bir kısayol.
 *
 * [value] türüne göre değişiyor: uygulamada paket adı, aramada ve
 * WhatsApp'ta telefon numarası, sistemde eylemin adı. Tek alanda
 * tutuluyor çünkü üçü de bir metin ve ayrı alanlar boş kalırdı.
 */
data class Shortcut(val kind: ShortcutKind, val value: String, val label: String)

/**
 * Kısayolların kaydı.
 *
 * İmlecin ayarlarıyla aynı dosyada duruyor: ikisi de aynı servisin işi ve
 * servis zaten o dosyayı dinliyor, kısayol değişince listeyi tazelemek
 * için ayrı bir yol açmaya gerek kalmıyor.
 */
class ShortcutStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(QuickCursorSettings.PREFS_NAME, Context.MODE_PRIVATE)

    var items: List<Shortcut>
        get() = decode(prefs.getString(KEY, "").orEmpty())
        set(value) {
            prefs.edit().putString(KEY, encode(value)).apply()
        }

    fun add(item: Shortcut) {
        items = items + item
    }

    fun removeAt(index: Int) {
        items = items.filterIndexed { i, _ -> i != index }
    }

    /** Sırayı değiştirir; çubukta soldan sağa bu sırayla diziliyorlar. */
    fun move(from: Int, to: Int) {
        val list = items.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        items = list
    }

    private fun encode(list: List<Shortcut>): String {
        val array = JSONArray()
        list.forEach { item ->
            array.put(
                JSONObject()
                    .put("k", item.kind.name)
                    .put("v", item.value)
                    .put("l", item.label),
            )
        }
        return array.toString()
    }

    private fun decode(raw: String): List<Shortcut> {
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val row = array.optJSONObject(i) ?: return@mapNotNull null
                val kind = runCatching { ShortcutKind.valueOf(row.optString("k")) }.getOrNull()
                    ?: return@mapNotNull null
                Shortcut(kind, row.optString("v"), row.optString("l"))
            }
        }.getOrDefault(emptyList())
    }

    companion object {
        const val KEY = "shortcuts"

        /** Sistem kısayolları: saklanan ad → ekranda görünen ad. */
        val SYSTEM: Map<String, String> = linkedMapOf(
            "last_app" to "Önceki uygulama",
            "notifications" to "Bildirimler",
            "quick_settings" to "Hızlı ayarlar",
            "recents" to "Son uygulamalar",
            "home" to "Ana ekran",
            "back" to "Geri",
            "screenshot" to "Ekran görüntüsü",
            "lock" to "Kilitle",
            "power" to "Güç menüsü",
        )
    }
}

/**
 * Kısayolu çalıştırır.
 *
 * Uygulama ve WhatsApp yeni görevde açılıyor: çağıran bir servis, görevi
 * olmayan bir yerden etkinlik başlatmanın tek yolu bu.
 *
 * Arama tuş takımını numarayla açıyor, aramayı kendisi başlatmıyor:
 * doğrudan arama ayrı bir izin istiyor ve yanlışlıkla açılan bir panelin
 * telefon etmesi istenecek bir şey değil.
 */
internal fun runShortcut(service: AccessibilityService, item: Shortcut): Boolean = when (item.kind) {
    ShortcutKind.APP -> {
        val intent = service.packageManager.getLaunchIntentForPackage(item.value)
        if (intent == null) {
            false
        } else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { service.startActivity(intent) }.isSuccess
        }
    }

    ShortcutKind.CALL -> start(
        service,
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:${item.value}")),
    )

    ShortcutKind.WHATSAPP -> start(
        service,
        // wa.me WhatsApp'ın kendi adresi; numara başındaki artı ve
        // boşluklar olmadan isteniyor.
        Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://wa.me/${item.value.filter { it.isDigit() }}"),
        ),
    )

    // Önceki uygulama bir sistem eylemi değil, bizim tuttuğumuz sıradan
    // geliyor; o yüzden ötekilerden ayrı duruyor.
    ShortcutKind.SYSTEM -> if (item.value == "last_app") {
        switchToLastApp(service)
    } else {
        val action = when (item.value) {
            "notifications" -> AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            "quick_settings" -> AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS
            "recents" -> AccessibilityService.GLOBAL_ACTION_RECENTS
            "home" -> AccessibilityService.GLOBAL_ACTION_HOME
            "back" -> AccessibilityService.GLOBAL_ACTION_BACK
            "screenshot" -> AccessibilityService.GLOBAL_ACTION_TAKE_SCREENSHOT
            "lock" -> AccessibilityService.GLOBAL_ACTION_LOCK_SCREEN
            "power" -> AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            else -> null
        }
        action != null && runCatching { service.performGlobalAction(action) }.getOrDefault(false)
    }
}

/**
 * Bir önceki uygulamaya geçer.
 *
 * Açılış niyeti uygulamanın kendi simgesine dokunmakla aynı: varsa duran
 * görevi öne getiriyor, kaldığın yerden devam ediyorsun.
 */
internal fun switchToLastApp(service: AccessibilityService): Boolean {
    val target = AppHistory.switchTarget(service)
    if (target == null) {
        // Sessizce hiçbir şey olmaması, bozuk sanılıyordu. Liste ancak
        // uygulamalar arasında gezindikçe doluyor; bunu söylemek yetiyor.
        Toast.makeText(
            service,
            "Henüz geçilecek bir önceki uygulama yok; iki uygulama arasında geçince dolacak.",
            Toast.LENGTH_SHORT,
        ).show()
        return false
    }
    val intent = service.packageManager.getLaunchIntentForPackage(target) ?: return false
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { service.startActivity(intent) }.isSuccess
}

private fun start(service: AccessibilityService, intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { service.startActivity(intent) }.isSuccess
}
