package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Ekranın kenar(lar)ındaki ince şeritten yapılan kaydırmaları sistem
 * komutlarına çevirir. Fluid NG'nin yaptığı işin aynısı — ama kapalı kaynak
 * bir APK'ya değil, kendi uygulamana yetki vermiş oluyorsun.
 *
 * **Güvenlik notu:** `accessibility_service_config.xml` içinde
 * `canRetrieveWindowContent="false"`; servis ekran içeriğini **okuyamaz**,
 * yalnızca jesti alıp global komut tetikler.
 *
 * Katman `TYPE_ACCESSIBILITY_OVERLAY` ile çiziliyor. Sağ ve sol kenar bağımsız;
 * ikisi aynı anda etkin olabiliyor. Ayarlar SharedPreferences dinleyicisiyle
 * canlı uygulanıyor — kalınlık/uzunluk/konum değişince katman anında yeniden
 * çiziliyor, servisi kapatıp açmaya gerek kalmıyor.
 */
class EdgeGestureService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private val edgeViews = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var settings: GestureSettings
    private val prefsListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        // Herhangi bir ayar değişince en basit ve doğru yol: baştan çiz.
        handler.post { rebuild() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        settings = GestureSettings(this)
        settings.prefs.registerOnSharedPreferenceChangeListener(prefsListener)
        rebuild()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Pencere içeriğini okumuyoruz; bu geri çağrı bilerek boş.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        // Bağlanma tamamlanmadan servis öldürülebilir; lateinit'e körlemesine
        // dokunmak süreci çökertir.
        if (::settings.isInitialized) {
            settings.prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
        }
        handler.removeCallbacksAndMessages(null)
        GestureFeedback.releaseTone()
        removeAll()
        super.onDestroy()
    }

    private fun rebuild() {
        removeAll()
        if (!settings.enabled) return
        val manager = getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        windowManager = manager
        if (settings.showLeft) addEdge(manager, onRight = false)
        if (settings.showRight) addEdge(manager, onRight = true)
    }

    private fun addEdge(manager: WindowManager, onRight: Boolean) {
        val density = resources.displayMetrics.density
        val widthPx = (settings.widthDp * density).toInt().coerceAtLeast(4)
        val heightPx = (settings.heightDp * density).toInt().coerceAtLeast(48)

        val view = EdgeTouchView(this, onRight, density)
        // Renk RGB'sini koru, alfayı saydamlık ayarından ez (0–100 → 0–255).
        val alpha = (settings.opacityPercent * 255 / 100).coerceIn(0, 255)
        val tintedColor = (settings.colorArgb and 0x00FFFFFF) or (alpha shl 24)
        view.background = GradientDrawable().apply {
            cornerRadius = widthPx / 2f
            setColor(tintedColor)
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
            gravity = (if (onRight) Gravity.END else Gravity.START) or Gravity.CENTER_VERTICAL
            y = (settings.verticalOffsetDp * density).toInt()
        }

        runCatching {
            manager.addView(view, params)
            edgeViews += view
        }
    }

    private fun removeAll() {
        handler.removeCallbacksAndMessages(null)
        edgeViews.forEach { view -> runCatching { windowManager?.removeView(view) } }
        edgeViews.clear()
    }

    /** Tek bir kenar şeridinin dokunuşlarını yorumlayan görünüm. */
    private inner class EdgeTouchView(
        context: Context,
        private val onRight: Boolean,
        private val density: Float,
    ) : View(context) {

        private var downX = 0f
        private var downY = 0f
        private var longPressFired = false

        /** Son "kısa dokunuş" anı; ikincisi yeterince hızlı gelirse çift dokunuş. */
        private var lastTapAt = 0L
        private val longPressRunnable = Runnable {
            longPressFired = true
            perform(settings.longPressAction, GestureSettings.GESTURE_LONG)
        }

        /**
         * Jest navigasyonu açıkken sistemin "kenardan içeri çek = geri" hareketi
         * bizim şeridimizle çakışıyor. Bu çağrı sisteme "bu dikdörtgende senin
         * kenar hareketin çalışmasın" diyor; şeridin dışında sistem normal
         * çalışmaya devam ediyor. Yani şerit üzerinde bizim jestimiz baskın.
         *
         * Android kenar başına en fazla 200dp'lik dışlama kabul ediyor; şerit
         * daha uzunsa fazlası sistemde kalır (ayar ekranında uyarıyoruz).
         */
        override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
            super.onLayout(changed, left, top, right, bottom)
            runCatching {
                systemGestureExclusionRects = listOf(Rect(0, 0, width, height))
            }
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            val threshold = SWIPE_THRESHOLD_DP * density
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    longPressFired = false
                    handler.postDelayed(longPressRunnable, LONG_PRESS_MS)
                    return true
                }

                MotionEvent.ACTION_MOVE -> {
                    // Belirgin hareket başladıysa bu bir kaydırma; uzun basışı iptal et.
                    if (abs(event.rawX - downX) > threshold || abs(event.rawY - downY) > threshold) {
                        handler.removeCallbacks(longPressRunnable)
                    }
                    return true
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPressRunnable)
                    if (longPressFired) return true // eylem zaten tetiklendi

                    val dy = event.rawY - downY
                    val dx = event.rawX - downX
                    // İçeri = şeritten ekranın ortasına doğru (sağ kenarda sola, solda sağa).
                    val inward = if (onRight) -dx else dx

                    // Yön ayrımı sabit "hangisi büyük" kuralı yerine ayarlanan
                    // açıyla yapılıyor: geri ile aşağı çekme birbirine karışıyordu.
                    val angle = Math.toDegrees(
                        atan2(abs(dy).toDouble(), abs(inward).toDouble()),
                    )
                    val moved = hypot(dx.toDouble(), dy.toDouble()) > threshold

                    // Kaydırmayan dokunuş: ikincisi yeterince hızlı gelirse
                    // çift dokunuş. Tek dokunuşun eylemi yok, bu yüzden
                    // birincisini bekletmeye gerek kalmıyor — çift dokunuş
                    // anında çalışıyor.
                    if (!moved) {
                        val now = event.eventTime
                        if (now - lastTapAt in 1..DOUBLE_TAP_MS) {
                            lastTapAt = 0L
                            perform(settings.doubleTapAction, GestureSettings.GESTURE_DOUBLE)
                        } else {
                            lastTapAt = now
                        }
                        return true
                    }
                    lastTapAt = 0L

                    val action = when {
                        angle <= settings.backAngleDegrees && inward > 0 ->
                            settings.swipeInAction to GestureSettings.GESTURE_IN

                        angle > settings.backAngleDegrees && dy < 0 ->
                            settings.swipeUpAction to GestureSettings.GESTURE_UP

                        angle > settings.backAngleDegrees && dy > 0 ->
                            settings.swipeDownAction to GestureSettings.GESTURE_DOWN

                        else -> null
                    }
                    if (action != null) perform(action.first, action.second)
                    return true
                }

                MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(longPressRunnable)
                    return true
                }
            }
            return true
        }
    }

    private fun perform(action: GestureAction, gestureKey: String) {
        if (action == GestureAction.NONE) return
        val ok = when (action) {
            GestureAction.OPEN_APP -> openApp(gestureKey)
            GestureAction.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            GestureAction.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
            GestureAction.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            GestureAction.NOTIFICATIONS -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            GestureAction.QUICK_SETTINGS -> performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            GestureAction.LOCK_SCREEN -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            GestureAction.SCREENSHOT -> performGlobalAction(GLOBAL_ACTION_TAKE_SCREENSHOT)
            GestureAction.POWER_DIALOG -> performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            GestureAction.NONE -> false
        }
        if (ok) {
            GestureFeedback.vibrate(this, settings)
            GestureFeedback.beep(settings)
        }
    }

    /**
     * Jeste atanan uygulamayı açar. Şerit erişilebilirlik katmanında olduğu
     * için etkinliği yeni bir görevde başlatmak zorundayız.
     */
    private fun openApp(gestureKey: String): Boolean {
        val packageName = settings.appFor(gestureKey)
        if (packageName.isBlank()) return false
        val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return false
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { startActivity(intent) }.isSuccess
    }

    companion object {
        private const val SWIPE_THRESHOLD_DP = 24f
        private const val LONG_PRESS_MS = 350L

        /** İki dokunuş arasındaki en uzun süre; sistemin varsayılanıyla aynı. */
        private const val DOUBLE_TAP_MS = 300L

        fun isRunning(context: Context): Boolean = GestureSettings(context).enabled
    }
}
