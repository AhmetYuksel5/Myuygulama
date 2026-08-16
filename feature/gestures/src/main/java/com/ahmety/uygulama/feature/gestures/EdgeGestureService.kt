package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs

/**
 * Ekranın kenarındaki ince şeritten yapılan kaydırmaları sistem komutlarına çevirir.
 * Fluid NG'nin yaptığı işin aynısı — ama kapalı kaynak bir APK'ya değil, kendi
 * uygulamana yetki vermiş oluyorsun.
 *
 * **Güvenlik notu:** Erişilebilirlik servislerinin asıl riski ekrandaki metni
 * okuyabilmeleridir (banka bakiyesi, şifre, SMS). Bu servis
 * `accessibility_service_config.xml` içinde `canRetrieveWindowContent="false"`
 * ile tanımlı; yani ekran içeriğini **okuyamaz**. Yaptığı tek şey jesti alıp
 * global bir komut tetiklemek.
 *
 * Katman `TYPE_ACCESSIBILITY_OVERLAY` ile çiziliyor: "diğer uygulamaların
 * üstünde göster" iznine gerek kalmıyor ve tam ekran uygulamalarda da çalışıyor.
 */
class EdgeGestureService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var edgeView: View? = null
    private var downY = 0f
    private var downX = 0f

    override fun onServiceConnected() {
        super.onServiceConnected()
        showEdge()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pencere içeriğini okumuyoruz; bu geri çağrı bilerek boş.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        hideEdge()
        super.onDestroy()
    }

    private fun showEdge() {
        if (edgeView != null) return
        val settings = GestureSettings(this)
        if (!settings.enabled) return

        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = manager

        val density = resources.displayMetrics.density
        val widthPx = (settings.widthDp * density).toInt().coerceAtLeast(4)
        val heightPx = (settings.heightDp * density).toInt().coerceAtLeast(48)

        val view = View(this).apply {
            background = GradientDrawable().apply {
                cornerRadius = widthPx / 2f
                setColor(settings.colorArgb)
            }
            setOnTouchListener { _, motionEvent -> handleTouch(motionEvent, density) }
        }

        val params = WindowManager.LayoutParams(
            widthPx,
            heightPx,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = (if (settings.onRightEdge) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            y = (settings.verticalOffsetDp * density).toInt()
        }

        runCatching {
            manager.addView(view, params)
            edgeView = view
        }
    }

    private fun hideEdge() {
        val view = edgeView ?: return
        runCatching { windowManager?.removeView(view) }
        edgeView = null
    }

    private fun handleTouch(event: MotionEvent, density: Float): Boolean {
        val threshold = SWIPE_THRESHOLD_DP * density
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downY = event.rawY
                downX = event.rawX
                true
            }

            MotionEvent.ACTION_UP -> {
                val dy = event.rawY - downY
                val dx = event.rawX - downX
                val handled = when {
                    // Dikey hareket yataydan baskınsa yukarı/aşağı komutları.
                    abs(dy) > abs(dx) && dy < -threshold ->
                        performGlobalAction(GLOBAL_ACTION_RECENTS)

                    abs(dy) > abs(dx) && dy > threshold ->
                        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)

                    // Şeritten içeri doğru kaydırma: geri.
                    abs(dx) > threshold -> performGlobalAction(GLOBAL_ACTION_BACK)

                    else -> false
                }
                // Jest gerçekten bir komuta dönüştüyse dokunsal onay ver.
                if (handled) GestureFeedback.vibrate(this, GestureSettings(this))
                true
            }

            else -> true
        }
    }

    companion object {
        private const val SWIPE_THRESHOLD_DP = 24f

        /** Ayar değiştiğinde servisin katmanı yeniden çizmesi için. */
        fun isRunning(context: Context): Boolean =
            GestureSettings(context).enabled
    }
}
