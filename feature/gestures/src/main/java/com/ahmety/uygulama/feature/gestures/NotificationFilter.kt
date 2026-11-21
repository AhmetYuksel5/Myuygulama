package com.ahmety.uygulama.feature.gestures

import android.app.Notification
import android.content.Context
import android.content.SharedPreferences
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Gizlenecek bildirimin tarifi.
 *
 * [pkg] boşsa her uygulama; [text] boşsa o uygulamanın bütün bildirimleri.
 * İkisi birden boş kural kaydedilmiyor: her bildirimi yutardı.
 */
data class NotificationRule(val pkg: String, val text: String)

/** Ayar ekranında "bundan kural yap" diye gösterilen, yakın zamanda gelmiş bildirim. */
data class SeenNotification(val pkg: String, val app: String, val title: String, val text: String)

/** Bildirim süzgecinin kuralları ve sayaçları. */
class NotificationFilterStore(context: Context) {
    val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun rules(): List<NotificationRule> = runCatching {
        val array = JSONArray(prefs.getString(KEY_RULES, "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            NotificationRule(item.optString("pkg"), item.optString("text"))
        }.filter { it.pkg.isNotBlank() || it.text.isNotBlank() }
    }.getOrDefault(emptyList())

    fun save(rules: List<NotificationRule>) {
        val array = JSONArray()
        rules.forEach { array.put(JSONObject().put("pkg", it.pkg).put("text", it.text)) }
        prefs.edit().putString(KEY_RULES, array.toString()).apply()
    }

    /** Bugüne kadar gizlenen bildirim sayısı; süzgecin çalıştığı buradan görülüyor. */
    var hiddenCount: Int
        get() = prefs.getInt(KEY_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_COUNT, value).apply()

    companion object {
        private const val PREFS = "merkez_bildirim_suzgeci"
        const val KEY_RULES = "rules"
        private const val KEY_COUNT = "hidden_count"

        fun isEnabled(context: Context): Boolean =
            context.packageName in NotificationManagerCompat.getEnabledListenerPackages(context)
    }
}

/**
 * Seçilen bildirimleri geldiği anda gizleyen servis.
 *
 * Telefonun "piliniz azaldı", "arka planda güç tüketiyor" gibi kendi
 * bildirimleri ayarlardan kapatılamıyor. Bunun için ayrı bir bildirim
 * yöneticisi uygulaması çalışıyordu ve tek başına yüzlerce megabayt bellek
 * tutuyordu; aynı iş burada birkaç satır.
 *
 * Kaydırılıp atılabilen bildirim siliniyor. Atılamayan (kalıcı) bildirim
 * silinemiyor; o erteleniyor — sistem onu perdeden kaldırıyor, süre dolunca
 * geri gelirse yeniden erteleniyor.
 */
class NotificationFilterService : NotificationListenerService() {

    private lateinit var store: NotificationFilterStore
    private var rules: List<NotificationRule> = emptyList()

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == NotificationFilterStore.KEY_RULES) {
            rules = store.rules()
            sweep()
        }
    }

    override fun onCreate() {
        super.onCreate()
        store = NotificationFilterStore(this)
        rules = store.rules()
        store.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    override fun onDestroy() {
        store.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        super.onDestroy()
    }

    override fun onListenerConnected() {
        sweep()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        handle(sbn ?: return)
    }

    /** Perdede hâlihazırda duranlar: yeni kural eklenince ve servis bağlanınca. */
    private fun sweep() {
        runCatching { activeNotifications }.getOrNull()?.forEach { handle(it) }
    }

    private fun handle(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)
                ?: extras.getCharSequence(Notification.EXTRA_TEXT)
            )?.toString().orEmpty()
        if (rules.none { matches(it, sbn.packageName, everything(sbn.notification, title, text)) }) {
            remember(sbn.packageName, title, text)
            return
        }
        runCatching {
            if (sbn.isClearable) cancelNotification(sbn.key) else snoozeNotification(sbn.key, SNOOZE_MS)
            store.hiddenCount = store.hiddenCount + 1
        }
    }

    /**
     * Bildirimdeki bütün yazılar. Mesaj uygulamalarının sohbet bildiriminde
     * metin olağan alanda değil, ayrı bir mesaj listesinde duruyor; önceden
     * yalnız başlığa ve olağan metne bakılıyordu, banka SMS'i kurala uyduğu
     * hâlde gizlenmiyordu.
     */
    private fun everything(notification: Notification, title: String, text: String): String {
        val extras = notification.extras
        val parts = mutableListOf(title, text)
        listOf(
            Notification.EXTRA_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_SUMMARY_TEXT,
            Notification.EXTRA_INFO_TEXT,
            Notification.EXTRA_TITLE_BIG,
            Notification.EXTRA_CONVERSATION_TITLE,
        ).forEach { key -> extras.getCharSequence(key)?.let { parts += it.toString() } }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { parts += it.toString() }
        @Suppress("DEPRECATION")
        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        messages?.forEach { item ->
            (item as? android.os.Bundle)?.getCharSequence("text")?.let { parts += it.toString() }
        }
        notification.tickerText?.let { parts += it.toString() }
        return parts.joinToString("\n")
    }

    private fun matches(rule: NotificationRule, pkg: String, content: String): Boolean {
        if (rule.pkg.isNotBlank() && rule.pkg != pkg) return false
        if (rule.text.isBlank()) return true
        return content.lowercase(TR).contains(rule.text.trim().lowercase(TR))
    }

    private fun remember(pkg: String, title: String, text: String) {
        if (title.isBlank() && text.isBlank()) return
        val app = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        synchronized(recent) {
            recent.removeAll { it.pkg == pkg && it.title == title }
            recent.add(0, SeenNotification(pkg, app, title, text))
            while (recent.size > RECENT_LIMIT) recent.removeAt(recent.size - 1)
        }
    }

    companion object {
        private val TR = Locale.forLanguageTag("tr")

        /** Kalıcı bildirimin erteleneceği süre; dolunca geri gelirse yeniden erteleniyor. */
        private const val SNOOZE_MS = 12L * 60 * 60 * 1000
        private const val RECENT_LIMIT = 40

        // Yalnız bellekte: kural yazmak için bakılıyor, saklamaya değmez;
        // bildirimlerin içeriğini diske yazmak da istenmez.
        private val recent = ArrayList<SeenNotification>()

        /** Son gelen (gizlenmemiş) bildirimler, en yenisi başta. */
        fun recent(): List<SeenNotification> = synchronized(recent) { recent.toList() }
    }
}
