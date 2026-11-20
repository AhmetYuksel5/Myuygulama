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

    fun get(pkg: String): Bitmap? = cache.get(pkg)

    fun forget(pkg: String) {
        cache.remove(pkg)
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

    fun capture(service: AccessibilityService) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        // Sistem erişilebilirlik görüntüsünü saniyede bir ile sınırlıyor.
        val now = SystemClock.uptimeMillis()
        if (now - lastShot < MIN_GAP_MS) return
        val pkg = runCatching { service.rootInActiveWindow?.packageName?.toString() }.getOrNull() ?: return
        // Merkez'in kendi pencereleri ve listede olmayan (klavye, ana ekran)
        // uygulamalar çekilmiyor.
        if (pkg == service.packageName) return
        if (AppHistory.snapshot(service, 1).firstOrNull() != pkg) return
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
                        if (small != null) cache.put(pkg, small)
                    }

                    override fun onFailure(errorCode: Int) = Unit
                },
            )
        }
    }

    private const val MAX_ITEMS = 12
    private const val THUMB_WIDTH = 360
    private const val SETTLE_MS = 900L
    private const val MIN_GAP_MS = 1_200L
}
