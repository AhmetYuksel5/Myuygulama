package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * "Son uygulamalar" eylemi: ayara göre Merkez'in paneli ya da sistemin ekranı.
 */
internal fun openRecents(service: AccessibilityService): Boolean =
    if (GestureSettings(service).ownRecents) {
        RecentAppsPanel.show(service)
    } else {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
    }

/**
 * Merkez'in kendi son uygulamalar paneli.
 *
 * Bazı telefonlarda sistemin son uygulamalar ekranı kayıyor, sırayı
 * karıştırıyor. Bu panel sırayı "önceki uygulama" geçişinin tuttuğu
 * kayıttan alıyor: en son kullanılan başta, içinde olunan uygulama yok.
 * Dokunmak o uygulamaya geçiyor, uzun basmak listeden çıkarıyor.
 */
internal object RecentAppsPanel {

    private var root: View? = null

    fun show(service: AccessibilityService): Boolean {
        if (root != null) {
            hide(service)
            return true
        }
        val apps = AppHistory.others(service, COUNT)
        if (apps.isEmpty()) {
            Toast.makeText(service, "Henüz son uygulama yok; uygulamalar arasında geçtikçe dolacak.", Toast.LENGTH_SHORT).show()
            return false
        }
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return false
        val d = service.resources.displayMetrics.density
        fun dp(v: Int) = (v * d).toInt()

        val frame = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hide(service)
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setBackgroundColor(Color.argb(150, 0, 0, 0))
        frame.setOnClickListener { hide(service) }

        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f * d
                setColor(Color.argb(255, 28, 28, 32))
            }
            setPadding(dp(14), dp(14), dp(14), dp(10))
            isClickable = true
        }
        card.addView(
            TextView(service).apply {
                text = "Son uygulamalar"
                setTextColor(Color.argb(170, 255, 255, 255))
                textSize = 13f
                setPadding(dp(6), 0, 0, dp(8))
            },
        )
        val grid = GridLayout(service).apply { columnCount = COLUMNS }
        apps.forEach { pkg -> grid.addView(cell(service, pkg, d)) }
        card.addView(grid)

        frame.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                // Altta: tek elle başparmağın ulaştığı yer.
                gravity = Gravity.BOTTOM
                setMargins(dp(12), dp(12), dp(12), dp(36))
            },
        )

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        return runCatching {
            manager.addView(frame, params)
            root = frame
        }.isSuccess
    }

    fun hide(service: AccessibilityService) {
        val view = root ?: return
        root = null
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { manager?.removeView(view) }
    }

    private fun cell(service: AccessibilityService, pkg: String, d: Float): View {
        val width = ((service.resources.displayMetrics.widthPixels - 52 * d) / COLUMNS).toInt()
        return LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((4 * d).toInt(), (8 * d).toInt(), (4 * d).toInt(), (8 * d).toInt())
            layoutParams = GridLayout.LayoutParams().apply { this.width = width }
            isClickable = true
            setOnClickListener {
                hide(service)
                val ok = launchPackage(service, pkg)
                if (ok) AppHistory.promote(service, pkg)
                GestureFeedback.vibrateOnce(service, if (ok) 15L else 8L)
            }
            // Kapatılan uygulamayı Android haber vermiyor; elle çıkarılıyor.
            setOnLongClickListener {
                AppHistory.forget(service, pkg)
                GestureFeedback.vibrateOnce(service, 25L)
                hide(service)
                show(service)
                true
            }
            addView(
                ImageView(service).apply {
                    setImageDrawable(AppIcons.get(service, pkg))
                    layoutParams = LinearLayout.LayoutParams((48 * d).toInt(), (48 * d).toInt())
                },
            )
            addView(
                TextView(service).apply {
                    text = label(service, pkg)
                    setTextColor(Color.WHITE)
                    textSize = 12f
                    maxLines = 1
                    ellipsize = TextUtils.TruncateAt.END
                    gravity = Gravity.CENTER
                    setPadding(0, (6 * d).toInt(), 0, 0)
                },
            )
        }
    }

    private fun label(context: Context, pkg: String): String = runCatching {
        context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private const val COUNT = 10
    private const val COLUMNS = 5
}
