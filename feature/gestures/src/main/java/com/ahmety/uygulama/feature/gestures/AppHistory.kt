package com.ahmety.uygulama.feature.gestures

import android.content.ComponentName
import android.content.Context
import android.os.SystemClock
import android.view.inputmethod.InputMethodManager

/**
 * Hangi uygulamanın ne zaman önde olduğunun kaydı.
 *
 * "Önceki uygulamaya geç" için var. Sistemin son uygulamalar listesi
 * dışarıdan okunamıyor; o listeyi ekrana açıp ikinci kartına dokunmaya
 * çalışmak ise tahminden ibaret — kartın yeri, sayısı, animasyonun bitip
 * bitmediği bilinmiyor. Rastgele uygulama açılması, bazen hiç açılmaması
 * bundan.
 *
 * Burada tahmin yok: erişilebilirlik servisi zaten her pencere değişiminde
 * hangi uygulamanın öne geldiğini söylüyor, sırayı ondan tutuyoruz.
 *
 * Sıra diske de yazılıyor. İlk sürümde yalnız bellekteydi: yeni sürüm
 * kurulunca ya da telefon servisi yeniden başlatınca liste sıfırlanıyor
 * ve iki uygulama arasında gidip gelmeden geçiş hiçbir şey yapmıyordu.
 */
internal object AppHistory {

    private val recent = ArrayList<String>()
    private var loaded = false

    // Paket yöneticisine giden sorular; her pencere değişiminde yeniden
    // sormamak için cevapları saklanıyor.
    private val launchable = HashMap<String, Boolean>()
    private val activities = HashMap<String, Boolean>()

    private var chainAt = 0L
    private var depth = 0
    private var frozen: List<String> = emptyList()

    /**
     * Öne gelen pencere.
     *
     * [className] kendi uygulamamızı ayırt etmek için: Merkez'in ana ekranı
     * gerçek bir uygulama ve listede yeri var, ama imleç, kısayol kartı ve
     * seçim penceresi de Merkez'in pencereleri. İlk sürüm Merkez'i toptan
     * dışarıda bırakıyordu; kısayolu Merkez'de ekleyip başka uygulamaya
     * geçince listede tek uygulama kalıyor, "önceki" bulunamıyordu.
     */
    @Synchronized
    fun record(context: Context, packageName: String?, className: String?) {
        load(context)
        val name = packageName?.takeIf { it.isNotBlank() } ?: return
        val window = className?.takeIf { it.isNotBlank() } ?: return
        // Yalnız gerçek bir ekran öne geldiğinde sayılıyor.
        //
        // Pencere değişimi klavyede, açılır menüde, diyalogda da geliyor.
        // İlk sürüm "açılabilir mi" diye bakıyordu ve Gboard'un ayarlar
        // simgesi olduğu için klavye bu testi geçiyordu: yazı yazılan her
        // uygulamadan sonra listeye Gboard giriyor, geçiş onun ayarlarını
        // açıyordu. Pencerenin sınıfı o paketin bir etkinliği mi diye
        // sormak bunların hepsini bir seferde eliyor.
        if (!isActivity(context, name, window)) return
        if (name in keyboards(context)) return
        // Merkez'in kendi pencerelerinden yalnız ana ekran sayılıyor; seçim
        // penceresi başka bir uygulamanın üstünde açılıyor, ona geçilmez.
        if (name == context.packageName && !window.endsWith(".MainActivity")) return
        if (!isLaunchable(context, name)) return
        if (recent.firstOrNull() == name) return
        recent.remove(name)
        recent.add(0, name)
        while (recent.size > MAX) recent.removeAt(recent.size - 1)
        save(context)
    }

    /**
     * Geçilecek uygulama; yoksa null.
     *
     * Tek seferde bir öncekine geçiyor. Kısa sürede tekrarlanırsa listede
     * bir adım daha geriye gidiyor — alt-tab'ı basılı tutmak gibi. Sıra o
     * sırada donduruluyor: geçiş yapılan uygulama listenin başına geçtiği
     * için, dondurulmasa insan iki uygulama arasında gidip gelirdi.
     */
    @Synchronized
    fun switchTarget(context: Context): String? {
        load(context)
        val now = SystemClock.uptimeMillis()
        if (now - chainAt > CHAIN_MS || frozen.size < 2) {
            frozen = recent.toList()
            depth = 0
        }
        chainAt = now
        if (frozen.size < 2) return null
        depth = (depth + 1).coerceAtMost(frozen.size - 1)
        return frozen.getOrNull(depth)
    }

    private fun isActivity(context: Context, name: String, window: String): Boolean =
        activities.getOrPut("$name/$window") {
            runCatching {
                context.packageManager.getActivityInfo(ComponentName(name, window), 0)
                true
            }.getOrDefault(false)
        }

    /**
     * Telefondaki klavyeler.
     *
     * Etkinlik testi klavye penceresini zaten eliyor; bu ikinci kat, eski
     * sürümün diske yazdığı listeden Gboard'u temizlemek için de lazım.
     */
    private fun keyboards(context: Context): Set<String> {
        keyboardCache?.let { return it }
        val found = runCatching {
            val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            manager.inputMethodList.map { it.packageName }.toSet()
        }.getOrDefault(emptySet())
        keyboardCache = found
        return found
    }

    // Klavye listesi süreç boyunca bir kez soruluyor; her pencere
    // değişiminde sisteme gitmeye değmez, yeni klavye nadiren kurulur.
    private var keyboardCache: Set<String>? = null

    private fun isLaunchable(context: Context, name: String): Boolean =
        launchable.getOrPut(name) {
            runCatching {
                context.packageManager.getLaunchIntentForPackage(name) != null
            }.getOrDefault(false)
        }

    // Ayrı dosya: imlecin ayar dosyasına yazılsaydı, servis o dosyayı
    // dinlediği için her uygulama geçişinde katmanını baştan kurardı.
    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun load(context: Context) {
        if (loaded) return
        loaded = true
        val skip = keyboards(context)
        prefs(context).getString(KEY, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() && it !in skip }
            .forEach { if (it !in recent) recent.add(it) }
    }

    private fun save(context: Context) {
        prefs(context).edit().putString(KEY, recent.joinToString("\n")).apply()
    }

    private const val MAX = 12

    /** Bu süre içinde tekrarlanan geçiş, aynı zincirin devamı sayılıyor. */
    private const val CHAIN_MS = 1_800L

    private const val PREFS = "merkez_app_history"
    private const val KEY = "recent"
}
