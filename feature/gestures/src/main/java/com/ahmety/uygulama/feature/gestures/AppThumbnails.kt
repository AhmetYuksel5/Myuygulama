package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.LruCache
import android.view.Display

/**
 * Uygulamaların son ekranının küçük görüntüsü; son uygulamalar panelinde
 * simgenin yanında içerik de görünsün diye.
 *
 * Android başka uygulamaların görev resmini dışarı vermiyor. Bu yüzden bir
 * uygulama öne geldikten kısa süre sonra ekranın görüntüsü alınıp küçültülerek
 * saklanıyor. Yalnız bellekte: diske yazılmıyor, servis kapanınca gidiyor.
 * Banka gibi görüntü vermeyen uygulamalarda simge gösteriliyor.
 */
internal object AppThumbnails {

    private val handler = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(MAX_ITEMS) {}
    private var lastShot = 0L
    private var pending: Runnable? = null

    /**
     * Uygulamanın son görüntüsü. Bellekte yoksa diskten: önceden yalnız
     * bellekte tutuluyordu; her güncellemede ve yeniden başlatmada siliniyor,
     * kartlar simgeyle kalıyordu. Diskteki yer Merkez'in kendi önbelleği,
     * başka uygulamalar okuyamıyor.
     */
    fun get(context: android.content.Context, pkg: String): Bitmap? = cache.get(pkg)
        ?: runCatching {
            val file = fileFor(context, pkg)
            if (!file.exists()) null else android.graphics.BitmapFactory.decodeFile(file.path)
        }.getOrNull()?.also { cache.put(pkg, it) }

    fun forget(context: android.content.Context, pkg: String) {
        cache.remove(pkg)
        runCatching { fileFor(context, pkg).delete() }
    }

    private fun fileFor(context: android.content.Context, pkg: String) =
        java.io.File(java.io.File(context.cacheDir, "son_uygulamalar").apply { mkdirs() }, "$pkg.jpg")

    private fun save(context: android.content.Context, pkg: String, bitmap: Bitmap) {
        Thread {
            runCatching {
                fileFor(context, pkg).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            }
        }.start()
    }

    /**
     * Bir pencere değişiminden sonra çağrılıyor. Ekranın yerleşmesi
     * bekleniyor; art arda gelen değişimlerde yalnız sonuncusu çekiliyor.
     */
    fun schedule(service: AccessibilityService) {
        pending?.let { handler.removeCallbacks(it) }
        val run = Runnable { capture(service) }
        pending = run
        handler.postDelayed(run, SETTLE_MS)
    }

    /**
     * Ekranı şimdi çeker. [onDone] çekilen görüntüyle çağrılıyor; çekilemezse
     * (sınır, güvenli pencere, eski sürüm) elde ne varsa onunla.
     */
    fun capture(service: AccessibilityService, onDone: ((Bitmap?) -> Unit)? = null) {
        val pkg = AppHistory.appFor(
            service,
            runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull(),
        )
        val fallback: () -> Unit = { onDone?.invoke(pkg?.let { get(service, it) }) }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || pkg == null) return fallback()
        // Sistem erişilebilirlik görüntüsünü saniyede bir ile sınırlıyor.
        val now = SystemClock.uptimeMillis()
        if (now - lastShot < MIN_GAP_MS) return fallback()
        // Merkez'in kendi pencereleri ve listede olmayan (klavye, ana ekran)
        // uygulamalar çekilmiyor.
        if (pkg == service.packageName) return fallback()
        if (AppHistory.snapshot(service, 1).firstOrNull() != pkg) return fallback()
        lastShot = now
        runCatching {
            service.takeScreenshot(
                Display.DEFAULT_DISPLAY,
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                        val buffer = result.hardwareBuffer
                        val small = runCatching {
                            val full = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                                ?.copy(Bitmap.Config.ARGB_8888, false) ?: return@runCatching null
                            val width = THUMB_WIDTH.coerceAtMost(full.width)
                            val height = full.height * width / full.width
                            Bitmap.createScaledBitmap(full, width, height, true).also {
                                if (it !== full) full.recycle()
                            }
                        }.getOrNull()
                        buffer.close()
                        if (small != null) {
                            cache.put(pkg, small)
                            save(service, pkg, small)
                        }
                        onDone?.invoke(small ?: get(service, pkg))
                    }

                    override fun onFailure(errorCode: Int) = fallback()
                },
            )
        }.onFailure { fallback() }
    }

    private const val MAX_ITEMS = 12
    private const val THUMB_WIDTH = 360
    private const val SETTLE_MS = 900L
    private const val MIN_GAP_MS = 1_200L
}
