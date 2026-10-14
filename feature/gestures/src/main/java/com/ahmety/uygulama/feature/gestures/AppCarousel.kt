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
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Uygulama geçişinden sonra ekranın ortasında beliren yarı saydam şerit.
 *
 * Son birkaç uygulamanın simgesi yan yana; şu an önde olan (geçilen)
 * vurgulu. Başka birine dokunulursa oraya geçiliyor. Dokunulmazsa kısa
 * süre sonra kendiliğinden kayboluyor.
 *
 * Pencere yalnız şerit kadar: dışına dokunmak arkadaki uygulamaya gidiyor,
 * şerit hiçbir şeyi engellemiyor.
 */
internal object AppCarousel {

    private var root: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private val close = Runnable { hide() }

    fun show(service: AccessibilityService) {
        hide()
        val count = QuickCursorSettings(service).carouselCount
        val apps = AppHistory.snapshot(service, count)
        if (apps.size < 2) return
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val row = LinearLayout(service).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(Color.argb(170, 20, 20, 24))
            }
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        val packages = service.packageManager
        apps.forEachIndexed { index, name ->
            val current = index == 0
            val cell = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(8), dp(8), dp(8), dp(6))
                if (current) {
                    background = GradientDrawable().apply {
                        cornerRadius = 16f * density
                        setColor(Color.argb(70, 255, 255, 255))
                    }
                }
                setOnClickListener {
                    hide()
                    if (!current && launchPackage(service, name)) {
                        AppHistory.promote(service, name)
                    }
                }
            }
            cell.addView(
                ImageView(service).apply {
                    runCatching { setImageDrawable(packages.getApplicationIcon(name)) }
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
                    alpha = if (current) 1f else 0.75f
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
            row.addView(cell, LinearLayout.LayoutParams(dp(80), ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.CENTER }

        runCatching {
            manager.addView(row, params)
            root = row
            row.alpha = 0f
            row.animate().alpha(1f).setDuration(120).start()
        }
        handler.postDelayed(close, VISIBLE_MS)
    }

    fun hide() {
        handler.removeCallbacks(close)
        val view = root ?: return
        root = null
        val manager = view.context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { manager?.removeView(view) }
    }

    private const val VISIBLE_MS = 1_500L
}
