package com.ahmety.uygulama.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bir simgeye atanan tek bir komut. Kodlanmış hâli "tür:argüman" biçiminde
 * saklanıyor (kotlinx.serialization eklentisi app modülünde yok; org.json yeter).
 *
 * Örn: "app:com.whatsapp", "wa:+905551112233", "call:+905551112233",
 * "nav:İş adresi ...", "web:https://...".
 */
sealed interface LauncherAction {
    fun encode(): String
    fun run(context: Context)

    object None : LauncherAction {
        override fun encode() = "none"
        override fun run(context: Context) = Unit
    }

    data class OpenApp(val packageName: String) : LauncherAction {
        override fun encode() = "app:$packageName"
        override fun run(context: Context) {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivityCatching(intent, "Uygulama açılamadı")
            } else {
                toast(context, "Uygulama bulunamadı")
            }
        }
    }

    /** WhatsApp sohbetini açar (wa.me). */
    data class WhatsApp(val phone: String) : LauncherAction {
        override fun encode() = "wa:$phone"
        override fun run(context: Context) {
            val digits = phone.filter { it.isDigit() || it == '+' }.removePrefix("+")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$digits"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivityCatching(intent, "WhatsApp açılamadı")
        }
    }

    /** Doğrudan arama başlatır; izin yoksa çeviriciyi açar. */
    data class Call(val phone: String) : LauncherAction {
        override fun encode() = "call:$phone"
        override fun run(context: Context) {
            val uri = Uri.parse("tel:${Uri.encode(phone)}")
            val call = Intent(Intent.ACTION_CALL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(call)
            } catch (e: SecurityException) {
                // CALL_PHONE izni verilmemiş: çeviriciye düş.
                Dial(phone).run(context)
            } catch (e: Exception) {
                toast(context, "Arama başlatılamadı")
            }
        }
    }

    data class Dial(val phone: String) : LauncherAction {
        override fun encode() = "dial:$phone"
        override fun run(context: Context) {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivityCatching(intent, "Çevirici açılamadı")
        }
    }

    data class Sms(val phone: String) : LauncherAction {
        override fun encode() = "sms:$phone"
        override fun run(context: Context) {
            val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(phone)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivityCatching(intent, "Mesaj açılamadı")
        }
    }

    /** Verilen adrese navigasyon başlatır (Google Haritalar). */
    data class Navigate(val address: String) : LauncherAction {
        override fun encode() = "nav:$address"
        override fun run(context: Context) {
            val nav = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("google.navigation:q=${Uri.encode(address)}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                context.startActivity(nav)
            } catch (e: Exception) {
                val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(address)}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivityCatching(geo, "Harita açılamadı")
            }
        }
    }

    data class Web(val url: String) : LauncherAction {
        override fun encode() = "web:$url"
        override fun run(context: Context) {
            val fixed = if (url.startsWith("http")) url else "https://$url"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(fixed))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivityCatching(intent, "Bağlantı açılamadı")
        }
    }

    companion object {
        fun decode(raw: String?): LauncherAction {
            if (raw.isNullOrBlank()) return None
            val sep = raw.indexOf(':')
            if (sep < 0) return None
            val type = raw.substring(0, sep)
            val arg = raw.substring(sep + 1)
            return when (type) {
                "app" -> OpenApp(arg)
                "wa" -> WhatsApp(arg)
                "call" -> Call(arg)
                "dial" -> Dial(arg)
                "sms" -> Sms(arg)
                "nav" -> Navigate(arg)
                "web" -> Web(arg)
                else -> None
            }
        }
    }
}

private fun Context.startActivityCatching(intent: Intent, errorMessage: String) {
    try {
        startActivity(intent)
    } catch (e: Exception) {
        toast(this, errorMessage)
    }
}

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

enum class FavoriteType { APP, SHORTCUT }

/**
 * Ana ekrandaki tek bir simge. Uygulama simgesi ya da kişi/kısayol olabilir.
 * Her simgenin dört jesti ayrı komuta bağlı: tek dokun, çift dokun, yukarı
 * sürükle, aşağı sürükle. (Kişiler için: tek dokun = WhatsApp, çift dokun =
 * ara, yukarı = iş navigasyonu, aşağı = ev navigasyonu gibi.)
 */
data class Favorite(
    val id: String,
    val type: FavoriteType,
    val label: String,
    val packageName: String? = null,
    val colorArgb: Int = 0xFF3F51B5.toInt(),
    val tap: LauncherAction = LauncherAction.None,
    val doubleTap: LauncherAction = LauncherAction.None,
    val swipeUp: LauncherAction = LauncherAction.None,
    val swipeDown: LauncherAction = LauncherAction.None,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("label", label)
        put("pkg", packageName ?: JSONObject.NULL)
        put("color", colorArgb)
        put("tap", tap.encode())
        put("double", doubleTap.encode())
        put("up", swipeUp.encode())
        put("down", swipeDown.encode())
    }

    companion object {
        fun fromJson(o: JSONObject): Favorite = Favorite(
            // Bozuk kayıtta boş id, "id eşitse sil" mantığıyla tüm boş id'li
            // simgeleri birden siler; boşsa yenisini üret.
            id = o.optString("id").ifBlank { newFavoriteId() },
            type = runCatching { FavoriteType.valueOf(o.optString("type", "APP")) }
                .getOrDefault(FavoriteType.APP),
            label = o.optString("label"),
            packageName = o.optString("pkg").takeIf { it.isNotBlank() && it != "null" },
            colorArgb = o.optInt("color", 0xFF3F51B5.toInt()),
            tap = LauncherAction.decode(o.optString("tap")),
            doubleTap = LauncherAction.decode(o.optString("double")),
            swipeUp = LauncherAction.decode(o.optString("up")),
            swipeDown = LauncherAction.decode(o.optString("down")),
        )
    }
}

/** Basit bir kimlik üreteci — Math.random/UUID yerine artan sayaç yeter. */
private var favoriteCounter = 0
fun newFavoriteId(): String = "fav_${System.currentTimeMillis()}_${favoriteCounter++}"

/**
 * Ana ekran simgelerini SharedPreferences içinde JSON olarak saklar.
 * (Ayrı bir launcher olduğu için burada uygulamanın ortak veritabanına
 * bağlanmıyoruz; hafif ve bağımsız kalsın.)
 */
class LauncherStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun load(): List<Favorite> {
        val raw = prefs.getString(KEY_FAVORITES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { Favorite.fromJson(array.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    fun save(favorites: List<Favorite>) {
        val array = JSONArray()
        favorites.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_FAVORITES, array.toString()).apply()
    }

    var seeded: Boolean
        get() = prefs.getBoolean(KEY_SEEDED, false)
        set(value) = prefs.edit().putBoolean(KEY_SEEDED, value).apply()

    var onRight: Boolean
        get() = prefs.getBoolean(KEY_RIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_RIGHT, value).apply()

    var iconSizeDp: Int
        get() = prefs.getInt(KEY_ICON_SIZE, 60)
        set(value) = prefs.edit().putInt(KEY_ICON_SIZE, value.coerceIn(44, 88)).apply()

    companion object {
        private const val PREFS_NAME = "merkez_launcher"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_SEEDED = "seeded"
        private const val KEY_RIGHT = "on_right"
        private const val KEY_ICON_SIZE = "icon_size"
    }
}

data class AppInfo(val packageName: String, val label: String)

/** Cihazdaki başlatılabilir uygulamaları alfabetik döndürür. */
fun loadLaunchableApps(context: Context): List<AppInfo> {
    val pm = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        pm.queryIntentActivities(intent, 0)
            .mapNotNull { resolve ->
                val pkg = resolve.activityInfo?.packageName ?: return@mapNotNull null
                AppInfo(pkg, resolve.loadLabel(pm).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}

/**
 * İlk açılışta ana ekran boş kalmasın diye yaygın uygulamalardan yüklü
 * olanları simge yapar. Kullanıcı sonra düzenler. Üretici farkları için
 * her rol birden çok paket adıyla aranıyor (Samsung/Xiaomi kendi
 * çevirici/kamera/rehber uygulamalarını kullanıyor).
 */
fun seedFavorites(apps: List<AppInfo>): List<Favorite> {
    val installed = apps.associateBy { it.packageName }
    val roles = listOf(
        listOf("com.whatsapp"),
        listOf("com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer"),
        listOf("com.google.android.contacts", "com.samsung.android.contacts", "com.android.contacts"),
        listOf("com.google.android.apps.maps"),
        listOf("com.android.chrome"),
        listOf("com.google.android.gm"),
        listOf(
            "com.google.android.GoogleCamera",
            "com.sec.android.app.camera",
            "com.android.camera",
        ),
    )
    return roles.mapNotNull { variants ->
        val app = variants.firstNotNullOfOrNull { installed[it] } ?: return@mapNotNull null
        Favorite(
            id = newFavoriteId(),
            type = FavoriteType.APP,
            label = app.label,
            packageName = app.packageName,
            tap = LauncherAction.OpenApp(app.packageName),
        )
    }
}

/**
 * Uygulama simgelerinin süreç ömrü boyunca yaşayan önbelleği. Simgeyi her
 * hücrede yeniden çözmek çekmeceyi gözle görülür şekilde takıyordu; burada
 * bir kez çözülüp saklanıyor, çözme işi de arka plana alınıyor.
 */
object AppIconCache {

    private val cache = ConcurrentHashMap<String, ImageBitmap>()

    fun cached(packageName: String): ImageBitmap? = cache[packageName]

    suspend fun load(context: Context, packageName: String): ImageBitmap? {
        cache[packageName]?.let { return it }
        val decoded = withContext(Dispatchers.IO) {
            runCatching {
                context.packageManager.getApplicationIcon(packageName)
                    .toBitmap(ICON_PX, ICON_PX)
                    .asImageBitmap()
            }.getOrNull()
        }
        if (decoded != null) cache[packageName] = decoded
        return decoded
    }

    private const val ICON_PX = 132
}
