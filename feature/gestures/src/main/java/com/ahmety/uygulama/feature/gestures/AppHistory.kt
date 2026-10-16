package com.ahmety.uygulama.feature.gestures

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
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
    fun record(context: Context, packageName: String?, className: String?, title: String = "") {
        load(context)
        val raw = packageName?.takeIf { it.isNotBlank() } ?: return
        val window = className?.takeIf { it.isNotBlank() } ?: return
        // Chrome'dan ana ekrana eklenen uygulama (Yeşil Temizlik gibi) ayrı
        // bir paket olarak kuruluyor ama penceresi Chrome'un. Liste ona
        // "Chrome" yazıyordu; burada kurulu web uygulamasının kendisi
        // bulunup o yazılıyor, geçiş de onu açıyor.
        if (".webapps." in window) {
            val webApp = webApkFor(context, title) ?: return
            pushFront(context, webApp)
            return
        }
        // Chrome'un tarayıcı penceresi dışındaki pencereleri (web uygulaması
        // açıkken araya giren iç ekranlar, özel sekmeler) sayılmıyor: ilk
        // sürüm web uygulamasını doğru yazıyor, hemen ardından gelen bu
        // haberler listenin başına yine "Chrome" koyuyordu.
        if (window.startsWith("org.chromium.") && !window.endsWith("ChromeTabbedActivity")) return
        val name = raw
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
        // Ana ekran uygulaması (Smart Launcher vb.) sayılmıyor: sayılınca
        // geçiş, bulunduğun uygulamayla ana ekran arasında gidip geliyordu.
        if (name in launchers(context)) return
        // Merkez'in kendi pencerelerinden yalnız ana ekran sayılıyor; seçim
        // penceresi başka bir uygulamanın üstünde açılıyor, ona geçilmez.
        if (name == context.packageName && !window.endsWith(".MainActivity")) return
        if (!isLaunchable(context, name)) return
        pushFront(context, name)
    }

    private fun pushFront(context: Context, name: String) {
        if (recent.firstOrNull() == name) return
        recent.remove(name)
        recent.add(0, name)
        while (recent.size > MAX) recent.removeAt(recent.size - 1)
        save(context)
    }

    /**
     * Chrome penceresinin hangi kurulu web uygulamasına ait olduğu.
     *
     * Kurulu web uygulamaları "org.chromium.webapk." ile başlayan paketler.
     * Tek tane varsa o; birden fazlaysa pencere başlığı uygulamanın adıyla
     * eşleşen. Bulunamazsa kayıt yapılmıyor — yanlış uygulamayı yazmaktan iyi.
     */
    private fun webApkFor(context: Context, title: String): String? = runCatching {
        val packages = context.packageManager
        val apps = packages.getInstalledApplications(0)
            .filter { it.packageName.startsWith("org.chromium.webapk.") }
            .filter { isLaunchable(context, it.packageName) }
        if (apps.size == 1) return@runCatching apps.first().packageName
        val wanted = title.trim().lowercase()
        if (wanted.isEmpty()) return@runCatching null
        apps.firstOrNull {
            val label = packages.getApplicationLabel(it).toString().trim().lowercase()
            label.isNotEmpty() && (wanted.contains(label) || label.contains(wanted))
        }?.packageName
    }.getOrNull()

    /**
     * Geçilecek uygulama; yoksa null.
     *
     * Her zaman bir önceki: iki uygulama arasında gidip geliniyor.
     *
     * İlk sürüm kısa sürede tekrarlanan geçişte listede bir adım daha
     * geriye gidiyordu (alt-tab'ı basılı tutmak gibi). Pratikte hızlı
     * basınca üçüncü uygulamaya düşülüyor, geri dönmek için beklemek
     * gerekiyordu; istenen bu değildi.
     *
     * Geçilen uygulama burada hemen listenin başına alınıyor. Sistemin
     * "öne geldi" haberini beklemek, hemen ardından basılan ikinci geçişin
     * eski sırayı görmesi demekti.
     */
    @Synchronized
    fun switchTarget(context: Context): String? {
        load(context)
        val target = recent.getOrNull(1) ?: return null
        recent.remove(target)
        recent.add(0, target)
        save(context)
        return target
    }

    /** Son [count] uygulama, en yenisi başta. */
    @Synchronized
    fun snapshot(context: Context, count: Int): List<String> {
        load(context)
        return recent.take(count)
    }

    /**
     * Uygulamayı listeden çıkarır.
     *
     * Bir uygulamanın son uygulamalardan kaydırılıp kapatıldığını Android
     * başka uygulamalara söylemiyor; kapatılanı kendiliğinden bilmenin yolu
     * yok. Şeritte uzun basınca buraya geliniyor.
     */
    @Synchronized
    fun forget(context: Context, name: String) {
        load(context)
        if (recent.remove(name)) save(context)
    }

    /** Seçilen uygulamayı listenin başına alır; şeritten seçilince. */
    @Synchronized
    fun promote(context: Context, name: String) {
        load(context)
        recent.remove(name)
        recent.add(0, name)
        save(context)
    }

    /**
     * Pencere o uygulamanın tam ekran bir ekranı mı.
     *
     * Etkinlik olması yetmiyor: widget'tan açılan "hızlı ekle" kutuları
     * (Microsoft To Do gibi) de birer etkinlik. Onlar uygulamayı açmıyor,
     * ama listeye girip geçişte uygulamayı boş yere getiriyorlardı.
     * İkisinden biri onları ayırıyor: son uygulamalarda görünmemeyi
     * isteyenler ve ekranın üstünde yüzen kutu olarak açılanlar.
     */
    private fun isActivity(context: Context, name: String, window: String): Boolean =
        activities.getOrPut("$name/$window") {
            runCatching {
                val info = context.packageManager.getActivityInfo(ComponentName(name, window), 0)
                if (info.flags and ActivityInfo.FLAG_EXCLUDE_FROM_RECENTS != 0) return@runCatching false
                !isFloating(context, name, info)
            }.getOrDefault(false)
        }

    private fun isFloating(context: Context, name: String, info: ActivityInfo): Boolean = runCatching {
        val theme = info.themeResource.takeIf { it != 0 } ?: info.applicationInfo.theme
        if (theme == 0) return@runCatching false
        val other = context.createPackageContext(name, 0)
        val attrs = other.theme.apply { applyStyle(theme, true) }
            .obtainStyledAttributes(intArrayOf(android.R.attr.windowIsFloating))
        try {
            attrs.getBoolean(0, false)
        } finally {
            attrs.recycle()
        }
    }.getOrDefault(false)

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

    private fun launchers(context: Context): Set<String> {
        launcherCache?.let { return it }
        val found = runCatching {
            val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            context.packageManager.queryIntentActivities(home, 0)
                .mapNotNull { it.activityInfo?.packageName }
                .toSet()
        }.getOrDefault(emptySet())
        launcherCache = found
        return found
    }

    private var launcherCache: Set<String>? = null

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
        val skip = keyboards(context) + launchers(context)
        prefs(context).getString(KEY, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() && it !in skip }
            .forEach { if (it !in recent) recent.add(it) }
    }

    private fun save(context: Context) {
        prefs(context).edit().putString(KEY, recent.joinToString("\n")).apply()
    }

    private const val MAX = 12


    private const val PREFS = "merkez_app_history"
    private const val KEY = "recent"
}
