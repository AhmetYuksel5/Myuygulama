package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.os.SystemClock

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

    // Açılabilir mi sorusu paket yöneticisine gidiyor; her pencere
    // değişiminde sormamak için cevabı saklanıyor.
    private val launchable = HashMap<String, Boolean>()

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
        if (name == context.packageName && className?.endsWith(".MainActivity") != true) return
        // Yüzü olmayanlar (sistem arayüzü, klavye, bildirim perdesi)
        // listeye girmiyor: aralarında geçiş yapmanın karşılığı yok ve
        // gerçek uygulamaları listeden atarlardı.
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
        prefs(context).getString(KEY, "").orEmpty()
            .split('\n')
            .filter { it.isNotBlank() }
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
