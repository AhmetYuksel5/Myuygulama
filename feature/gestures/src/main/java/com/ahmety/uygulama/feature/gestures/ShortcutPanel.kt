package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
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

/**
 * Diğer uygulamaların üstünde açılan kısayol çubuğu.
 *
 * Dipteki tutamağa çift dokununca açılıyor; tek elle kullanıldığı için
 * kart ekranın dibinde duruyor. Dışına dokunmak ya da geri tuşu kapatıyor.
 *
 * Compose değil düz görünümlerle yazıldı: bu modülde Compose yok ve
 * erişilebilirlik katmanına Compose asmak uygulamanın kendi yaşam
 * döngüsünü gerektiriyor — servisin öyle bir döngüsü yok.
 */
internal class ShortcutPanel(private val service: AccessibilityService) {

    private var root: View? = null

    val showing: Boolean get() = root != null

    fun toggle() {
        if (showing) hide() else show()
    }

    fun show() {
        if (showing) return
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return
        val density = service.resources.displayMetrics.density
        val items = ShortcutStore(service).items

        val frame = object : FrameLayout(service) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                    hide()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        frame.setBackgroundColor(SCRIM)
        frame.setOnClickListener { hide() }

        val pad = (16 * density).toInt()
        val card = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = 24f * density
                setColor(CARD)
            }
            setPadding(pad, pad, pad, pad)
            // Kartın içine basmak paneli kapatmasın.
            isClickable = true
        }

        if (items.isEmpty()) {
            card.addView(
                TextView(service).apply {
                    text = "Kısayol yok. Daha → Tek elle imleç → Kısayollar'dan ekle."
                    setTextColor(Color.WHITE)
                    textSize = 14f
                },
            )
        } else {
            val grid = GridLayout(service).apply { columnCount = COLUMNS }
            items.forEach { item -> grid.addView(itemView(item, density)) }
            card.addView(grid)
        }

        frame.addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                gravity = Gravity.BOTTOM
                val m = (12 * density).toInt()
                // Altta biraz daha boşluk: gezinme çubuğunun üstünde dursun.
                setMargins(m, m, m, (36 * density).toInt())
            },
        )

        val params = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            // Odaklanabilir: geri tuşu panele gelsin. Dokunmayı da bu
            // pencere alıyor, arkadaki uygulamaya geçmesin.
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )
        runCatching {
            manager.addView(frame, params)
            root = frame
        }
    }

    fun hide() {
        val view = root ?: return
        root = null
        val manager = service.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
        runCatching { manager?.removeView(view) }
    }

    /** Tek kısayol: yuvarlak bir simge ve altında adı. */
    private fun itemView(item: Shortcut, density: Float): View {
        val cell = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            isClickable = true
            setOnClickListener {
                // Önce kapanıyor: açılan uygulama panelin altında kalmasın.
                hide()
                val ok = runShortcut(service, item)
                GestureFeedback.vibrateOnce(service, if (ok) 15L else 8L)
            }
            val p = (6 * density).toInt()
            setPadding(p, p, p, p)
        }

        val size = (48 * density).toInt()
        val icon = if (item.kind == ShortcutKind.APP) AppIcons.get(service, item.value) else null
        if (icon != null) {
            cell.addView(
                ImageView(service).apply {
                    setImageDrawable(icon)
                    layoutParams = LinearLayout.LayoutParams(size, size)
                },
            )
        } else {
            // Uygulama değilse ya da simgesi okunamadıysa: baş harfi taşıyan
            // yuvarlak. Türüne göre rengi değişiyor, hangisi olduğu bir
            // bakışta anlaşılsın diye.
            cell.addView(
                TextView(service).apply {
                    text = item.label.trim().take(1).uppercase()
                    setTextColor(Color.WHITE)
                    textSize = 20f
                    gravity = Gravity.CENTER
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(
                            when (item.kind) {
                                ShortcutKind.CALL -> CALL_TINT
                                ShortcutKind.WHATSAPP -> WHATSAPP_TINT
                                else -> SYSTEM_TINT
                            },
                        )
                    }
                    layoutParams = LinearLayout.LayoutParams(size, size)
                },
            )
        }

        cell.addView(
            TextView(service).apply {
                text = item.label
                setTextColor(Color.WHITE)
                textSize = 11f
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, (4 * density).toInt(), 0, 0)
            },
        )

        cell.layoutParams = GridLayout.LayoutParams().apply {
            width = (76 * density).toInt()
            val m = (4 * density).toInt()
            setMargins(m, m, m, m)
        }
        return cell
    }

    private companion object {
        const val COLUMNS = 4
        val SCRIM = Color.argb(150, 0, 0, 0)
        val CARD = Color.argb(255, 28, 28, 32)
        val CALL_TINT = Color.argb(255, 46, 125, 50)
        val WHATSAPP_TINT = Color.argb(255, 18, 140, 126)
        val SYSTEM_TINT = Color.argb(255, 69, 90, 120)
    }
}
