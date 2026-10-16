package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
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
        val ok = launchPackage(service, item.value)
        // Kısayoldan açılan uygulama "önceki uygulama" listesine doğrudan
        // yazılıyor. Liste sistemin pencere haberlerinden kuruluyor; bazı
        // uygulamalar (bankalar) pencerelerini olağan dışı açtığı için o
        // haber gerçek bir ekran sayılmıyor, uygulama listeye hiç girmiyordu.
        // Açan biziz, açıldığını biliyoruz.
        if (ok) AppHistory.promote(service, item.value)
        ok
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
    val target = AppCarousel.nextTarget(service)
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
    // Hemen bir önceki uygulamaya dönülüyorsa sistemin kendi hızlı geçişi
    // kullanılıyor: son uygulamalar iki kez. Sistem görevi bırakıldığı
    // hâliyle öne getiriyor — Drive'ın PDF okuyucusu gibi uygulamanın
    // içinde ayrı açılan pencereler de dahil. Paket adıyla açmak o
    // pencereyi değil uygulamanın ana ekranını getiriyordu.
    val previous = AppHistory.snapshot(service, 2).getOrNull(1)
    val ok = if (target == previous && QuickCursorSettings(service).systemQuickSwitch) {
        quickSwitch(service)
    } else {
        launchPackage(service, target)
    }
    if (ok) AppHistory.promote(service, target)
    // Geçiş anında yapılıyor, şerit arkasından geliyor: hangi uygulamaya
    // geçildiğini ve öncekileri gösteriyor, istenirse başka birine
    // dokunulabiliyor. Beklemeden geçmek ilkesi bozulmasın diye şerit
    // seçimi beklemiyor.
    if (ok) AppCarousel.show(service)
    return ok
}

/**
 * Uygulamayı ana ekrandaki simgesine dokunulmuş gibi açar.
 *
 * Paket yöneticisinin verdiği açılış niyetinde paket adı da yazılı ve bu,
 * niyeti uygulamanın açık görevini başlatan niyetten farklı kılıyor.
 * Android ikisini aynı saymayınca görevi öne getirmek yerine giriş ekranını
 * görevin üstüne yeniden açıyordu: WhatsApp'ta sohbetten ana listeye
 * düşülmesi bundandı. Simgenin gönderdiği niyetle birebir aynı olunca
 * görev olduğu gibi öne geliyor.
 */
internal fun launchPackage(service: AccessibilityService, name: String): Boolean {
    val component = service.packageManager.getLaunchIntentForPackage(name)?.component
        ?: return false
    val intent = Intent(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(component)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    return runCatching { service.startActivity(intent) }.isSuccess
}

/** Son uygulamalar tuşuna hızlıca iki kez basmak: sistemin önceki uygulamaya geçişi. */
private fun quickSwitch(service: AccessibilityService): Boolean {
    val opened = runCatching {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }.getOrDefault(false)
    if (!opened) return false
    // Ekran açılmadan ikinci basış gelirse sistem onu ilkinin parçası
    // sayıp kapatıyor; kısa bir bekleme gerekiyor.
    Handler(Looper.getMainLooper()).postDelayed({
        runCatching { service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS) }
    }, QUICK_SWITCH_DELAY_MS)
    return true
}

private const val QUICK_SWITCH_DELAY_MS = 280L

private fun start(service: AccessibilityService, intent: Intent): Boolean {
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    return runCatching { service.startActivity(intent) }.isSuccess
}
