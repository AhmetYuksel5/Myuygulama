package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Uygulama geçişinden sonra ekranın ortasında beliren yarı saydam şerit.
 *
 * Simgeler yerinde duruyor, yalnız vurgu kutusu aktif uygulamanın üstüne
 * kayıyor. İlk sürüm her geçişte sırayı yeniden diziyordu (en yenisi
 * başta); iki uygulama arasında gidip gelirken simgeler yer değiştiriyor,
 * göz neyin nerede olduğunu kaybediyordu. Artık uygulama kümesi aynı
 * kaldıkça sıra da aynı kalıyor.
 *
 * Pencere yalnız şerit kadar: dışına dokunmak arkadaki uygulamaya gidiyor.
 */
internal object AppCarousel {

    private var root: View? = null
    private var highlight: View? = null
    private var order: List<String> = emptyList()
    private val handler = Handler(Looper.getMainLooper())
    private val close = Runnable { hide() }

    fun show(service: AccessibilityService) {
        val count = QuickCursorSettings(service).carouselCount
        val recent = AppHistory.snapshot(service, count)
        if (recent.size < 2) return
        val active = recent.first()

        // Aynı uygulamalar arasında dolaşılıyorsa sıra korunuyor.
        val sameSet = order.size == recent.size && order.toSet() == recent.toSet()
        if (!sameSet) order = recent

        val shown = root
        if (sameSet && shown != null) {
            moveHighlight(order.indexOf(active), animate = true)
            restartTimer()
            return
        }
        hide()
        build(service, active)
        restartTimer()
    }

    /**
     * Geçilecek uygulama: her zaman bir önceki.
     *
     * Önceki sürüm şeritteki sabit sıranın bir sağındakine gidiyordu; üç
     * uygulamada bu, bir öncekine değil sırayla üçüncüsüne düşmek demekti.
     * Şerit sırası yalnız gösterim için sabit; hedef kullanım sırasından.
     */
    fun nextTarget(service: AccessibilityService): String? =
        AppHistory.snapshot(service, 2).getOrNull(1)

    private fun build(service: AccessibilityService, active: String) {
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()
        val cellWidth = dp(CELL_DP)

        val frame = FrameLayout(service).apply {
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(Color.argb(170, 20, 20, 24))
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val marker = View(service).apply {
            background = GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(Color.argb(80, 255, 255, 255))
            }
        }
        // Yükseklik sabit: "ebeveyn kadar" verilince kaydırılabilir pencere
        // tam ekran boyu açılıyor, şerit ekranı boydan boya kaplıyordu.
        frame.addView(marker, FrameLayout.LayoutParams(cellWidth, dp(CELL_HEIGHT_DP)))

        val row = LinearLayout(service).apply { orientation = LinearLayout.HORIZONTAL }
        val packages = service.packageManager
        order.forEach { name ->
            val cell = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(6))
                setOnClickListener {
                    val now = AppHistory.snapshot(service, 1).firstOrNull()
                    if (name != now && launchPackage(service, name)) {
                        AppHistory.promote(service, name)
                        moveHighlight(order.indexOf(name), animate = true)
                    }
                    restartTimer()
                }
                // Uzun basış: kapatılmış uygulamayı listeden çıkar. Şerit
                // yeni listeyle baştan çiziliyor.
                setOnLongClickListener {
                    AppHistory.forget(service, name)
                    GestureFeedback.vibrateOnce(service, 25L)
                    order = emptyList()
                    val now = AppHistory.snapshot(service, 1).firstOrNull()
                    hide()
                    if (now != null) show(service)
                    true
                }
            }
            cell.addView(
                ImageView(service).apply {
                    runCatching { setImageDrawable(packages.getApplicationIcon(name)) }
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                },
            )
            cell.addView(
                TextView(service).apply {
                    text = runCatching {
                        packages.getApplicationLabel(packages.getApplicationInfo(name, 0))
                    }.getOrDefault(name)
                    setTextColor(Color.WHITE)
                    textSize = 11f
                    maxLines = 1
                    maxWidth = dp(72)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(4), 0, 0)
                },
            )
            row.addView(cell, LinearLayout.LayoutParams(cellWidth, dp(CELL_HEIGHT_DP)))
        }
        frame.addView(row)

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            manager.addView(frame, params)
            root = frame
            highlight = marker
            // İlk açılışta vurgu, geçilmeden önceki uygulamanın üstünden
            // yeni uygulamaya kayarak geliyor: geçişin yönü görülsün.
            val from = order.indexOf(active).let { if (it == 0) 1 else 0 }
            moveHighlight(from, animate = false)
            moveHighlight(order.indexOf(active), animate = true)
            frame.alpha = 0f
            frame.animate().alpha(1f).setDuration(120).start()
        }
    }

    private fun moveHighlight(index: Int, animate: Boolean) {
        val marker = highlight ?: return
        if (index < 0) return
        val x = index * CELL_DP * marker.resources.displayMetrics.density
        if (animate) {
            marker.animate()
                .translationX(x)
                .setDuration(220)
                .setInterpolator(DecelerateInterpolator())
                .start()
        } else {
            marker.animate().cancel()
            marker.translationX = x
        }
    }

    private fun restartTimer() {
        handler.removeCallbacks(close)
        handler.postDelayed(close, VISIBLE_MS)
    }

    fun hide() {
        handler.removeCallbacks(close)
        val view = root ?: return
        root = null
        highlight = null
        val manager = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { manager?.removeView(view) }
    }

    private const val CELL_DP = 80
    private const val CELL_HEIGHT_DP = 88
    private const val VISIBLE_MS = 1_500L
}
