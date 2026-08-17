package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/**
 * Quick Cursor muadili: tek elle ulaşılamayan yerlere basmak için sanal imleç.
 *
 * Kenardaki topa parmağını basınca, imleç topun biraz **üstünde** belirir
 * (böylece başparmağın altında kalmaz, "Tek elle emret" gibi). Parmağını
 * gezdirdikçe imleç **trackpad gibi görece** hareket eder (küçük parmak
 * hareketi, hassasiyet kadar büyük imleç hareketi). Parmağını kaldırınca
 * imlecin durduğu yere gerçek bir dokunma gönderilir (`dispatchGesture`).
 *
 * Topu taşımak için uzun basıp sürükle. Kullanılmadığında top 3 saniye sonra
 * sönükleşir (fade-out); dokununca yeniden belirir. Ayarlar canlı uygulanır.
 */
class QuickCursorService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var handle: View? = null
    private var cursor: View? = null
    private var handleParams: WindowManager.LayoutParams? = null
    private var cursorParams: WindowManager.LayoutParams? = null

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var settings: QuickCursorSettings

    private var screenW = 0
    private var screenH = 0

    // Sürüş durumu
    private var lastX = 0f
    private var lastY = 0f
    private var cursorX = 0f
    private var cursorY = 0f
    private var moving = false
    private var draggingHandle = false
    private var longPressFired = false
    private val longPress = Runnable {
        longPressFired = true
        draggingHandle = true
        moving = false
        hideCursor()
        GestureFeedback.vibrateOnce(this@QuickCursorService, 25L)
    }

    // Kullanılmayınca topu sönükleştiren zamanlayıcı.
    private val fadeOut = Runnable { fadeHandle(idle = true) }

    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        handler.post { rebuild() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = QuickCursorSettings(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        rebuild()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    override fun onDestroy() {
        settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        handler.removeCallbacks(fadeOut)
        removeViews()
        super.onDestroy()
    }

    private fun rebuild() {
        removeViews()
        if (!settings.enabled) return
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = manager

        val metrics = resources.displayMetrics
        screenW = metrics.widthPixels
        screenH = metrics.heightPixels
        val density = metrics.density
        val sizePx = (settings.handleSizeDp * density).toInt()

        val handleView = HandleView(this, settings.opacityPercent)
        handleView.setOnTouchListener { _, event -> onHandleTouch(event, density) }
        val hParams = WindowManager.LayoutParams(
            sizePx,
            sizePx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (settings.onRight) Gravity.END else Gravity.START) or Gravity.BOTTOM
            y = (settings.bottomOffsetDp * density).toInt()
        }

        val cursorView = CursorView(this)
        val cParams = WindowManager.LayoutParams(
            (44 * density).toInt(),
            (44 * density).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        cursorView.visibility = View.GONE

        runCatching {
            manager.addView(cursorView, cParams)
            manager.addView(handleView, hParams)
            handle = handleView
            cursor = cursorView
            handleParams = hParams
            cursorParams = cParams
        }
        // Baştan da 3 sn sonra sönükleşsin; dokununca geri gelir.
        scheduleFade()
    }

    private fun onHandleTouch(event: MotionEvent, density: Float): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                wakeHandle()
                lastX = event.rawX
                lastY = event.rawY
                moving = false
                draggingHandle = false
                longPressFired = false
                handler.postDelayed(longPress, LONG_PRESS_MS)
                // İmleci topun biraz ÜSTÜNDE başlat; başparmağın altında kalmasın.
                // Buradan görece (trackpad) hareket edecek.
                cursorX = event.rawX.coerceIn(0f, screenW.toFloat())
                cursorY = (event.rawY - SPAWN_ABOVE_DP * density).coerceIn(0f, screenH.toFloat())
                showCursor()
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                lastX = event.rawX
                lastY = event.rawY

                if (draggingHandle) {
                    moveHandle(dx, dy, density)
                    return true
                }

                if (!moving && kotlin.math.hypot(dx.toDouble(), dy.toDouble()) > TOUCH_SLOP) {
                    moving = true
                    handler.removeCallbacks(longPress) // artık taşıma değil, imleç sürüşü
                    showCursor()
                }
                if (moving) {
                    val s = settings.sensitivity
                    cursorX = (cursorX + dx * s).coerceIn(0f, screenW.toFloat())
                    cursorY = (cursorY + dy * s).coerceIn(0f, screenH.toFloat())
                    updateCursor()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                handler.removeCallbacks(longPress)
                if (draggingHandle) {
                    persistHandlePosition(density)
                    draggingHandle = false
                    scheduleFade()
                    return true
                }
                if (moving) {
                    hideCursor()
                    tapAt(cursorX, cursorY)
                } else {
                    hideCursor()
                }
                moving = false
                scheduleFade()
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                handler.removeCallbacks(longPress)
                hideCursor()
                moving = false
                draggingHandle = false
                scheduleFade()
                return true
            }
        }
        return true
    }

    private fun showCursor() {
        cursor?.visibility = View.VISIBLE
        updateCursor()
    }

    private fun hideCursor() {
        cursor?.visibility = View.GONE
    }

    private fun updateCursor() {
        val params = cursorParams ?: return
        val view = cursor ?: return
        params.x = (cursorX - view.width / 2f).toInt()
        params.y = (cursorY - view.height / 2f).toInt()
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    /** Dokununca topu tam görünür yap ve sönükleşme sayacını durdur. */
    private fun wakeHandle() {
        handler.removeCallbacks(fadeOut)
        fadeHandle(idle = false)
    }

    /** 3 sn kullanılmazsa topu sönükleştir. */
    private fun scheduleFade() {
        handler.removeCallbacks(fadeOut)
        handler.postDelayed(fadeOut, FADE_DELAY_MS)
    }

    private fun fadeHandle(idle: Boolean) {
        val view = handle ?: return
        val target = if (idle) IDLE_ALPHA else 1f
        val duration = if (idle) FADE_OUT_MS else FADE_IN_MS
        runCatching { view.animate().alpha(target).setDuration(duration).start() }
    }

    private fun moveHandle(dx: Float, dy: Float, density: Float) {
        val params = handleParams ?: return
        val view = handle ?: return
        // Alt-hizalı olduğu için y arttıkça yukarı çıkar.
        params.y = (params.y - dy).toInt().coerceIn(0, screenH - view.height)
        runCatching { windowManager?.updateViewLayout(view, params) }
    }

    private fun persistHandlePosition(density: Float) {
        val params = handleParams ?: return
        settings.bottomOffsetDp = (params.y / density).toInt()
    }

    /**
     * İmlecin durduğu noktaya gerçek bir dokunma gönderir.
     *
     * Parmak daha yeni kalktığı için enjekte edilen dokunmayı **aynı karede**
     * göndermek girdi sisteminde düşürülüyordu (tıklama olmuyor gibi görünmesinin
     * sebebi buydu). Kısa bir gecikmeyle, parmak tamamen bırakıldıktan sonra
     * gönderiyoruz.
     */
    private fun tapAt(x: Float, y: Float) {
        val tx = x.coerceIn(0f, (screenW - 1).toFloat())
        val ty = y.coerceIn(0f, (screenH - 1).toFloat())
        handler.postDelayed({
            val path = Path().apply { moveTo(tx, ty) }
            val stroke = GestureDescription.StrokeDescription(path, 0L, TAP_DURATION_MS)
            val gesture = GestureDescription.Builder().addStroke(stroke).build()
            val dispatched = runCatching {
                dispatchGesture(
                    gesture,
                    object : GestureResultCallback() {
                        override fun onCompleted(description: GestureDescription?) {
                            GestureFeedback.vibrateOnce(this@QuickCursorService, 15L)
                        }
                    },
                    null,
                )
            }.getOrDefault(false)
            if (!dispatched) GestureFeedback.vibrateOnce(this@QuickCursorService, 8L)
        }, TAP_DELAY_MS)
    }

    private fun removeViews() {
        handle?.let { view -> runCatching { windowManager?.removeView(view) } }
        cursor?.let { view -> runCatching { windowManager?.removeView(view) } }
        handle = null
        cursor = null
    }

    companion object {
        private const val LONG_PRESS_MS = 350L
        private const val TOUCH_SLOP = 8f
        private const val TAP_DURATION_MS = 55L
        private const val TAP_DELAY_MS = 60L
        private const val SPAWN_ABOVE_DP = 130f
        private const val FADE_DELAY_MS = 3_000L
        private const val FADE_OUT_MS = 450L
        private const val FADE_IN_MS = 140L
        private const val IDLE_ALPHA = 0.12f
    }
}
