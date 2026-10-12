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
 */
internal object AppHistory {

    private val recent = ArrayList<String>()

    // Açılabilir mi sorusu paket yöneticisine gidiyor; her pencere
    // değişiminde sormamak için cevabı saklanıyor.
    private val launchable = HashMap<String, Boolean>()

    private var chainAt = 0L
    private var depth = 0
    private var frozen: List<String> = emptyList()

    /** Öne gelen uygulama. */
    @Synchronized
    fun record(context: Context, packageName: String?) {
        val name = packageName?.takeIf { it.isNotBlank() } ?: return
        // Kendi katmanlarımız ve yüzü olmayanlar (sistem arayüzü, klavye,
        // bildirim perdesi) listeye girmiyor: aralarında geçiş yapmanın
        // karşılığı yok ve gerçek uygulamaları listeden atarlardı.
        if (name == context.packageName) return
        if (!isLaunchable(context, name)) return
        if (recent.firstOrNull() == name) return
        recent.remove(name)
        recent.add(0, name)
        while (recent.size > MAX) recent.removeAt(recent.size - 1)
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
    fun switchTarget(): String? {
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

    private const val MAX = 12

    /** Bu süre içinde tekrarlanan geçiş, aynı zincirin devamı sayılıyor. */
    private const val CHAIN_MS = 1_800L
}
