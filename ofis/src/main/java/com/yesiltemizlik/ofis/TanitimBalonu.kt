package com.yesiltemizlik.ofis

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlin.math.abs

/**
 * Tanıtımın ikinci adımı için WhatsApp'ın üstünde duran küçük kare düğme
 * (kullanıcı 2026-11-26: "geri gelip tekrar basmak zorunda kalıyoruz").
 * Selam yazısı gönderilince belirir; basınca dosyalar aynı sohbete gider ve
 * kaybolur. Sürüklenir; uzun basınca kapanır; kullanılmazsa 3 dk sonra gider.
 *
 * Compose değil düz View: üstte gösterilen pencerenin bir etkinliği yok,
 * Compose'un istediği yaşam döngüsü burada kurulmuyor.
 */
object TanitimBalonu {
    private const val OMUR_MS = 3 * 60_000L
    private val ana = Handler(Looper.getMainLooper())
    private var gorunum: View? = null
    private val zamanAsimi = Runnable { gorunum?.let { kaldir(it.context) } }

    /** Gösterilebildiyse true; üstte gösterme izni yoksa false (o zaman ikinci adım kartta kalır). */
    @SuppressLint("ClickableViewAccessibility")
    fun goster(c: Context, tel: String): Boolean {
        val app = c.applicationContext
        if (!Settings.canDrawOverlays(app)) return false
        kaldir(app)
        val wm = app.getSystemService(WindowManager::class.java) ?: return false
        val d = app.resources.displayMetrics.density
        fun dp(x: Int) = (x * d).toInt()

        // Boyut yazıya göre: sabit 72 dp'lik kutuda yazı kayıyordu (kullanıcı 2026-11-26).
        val kutu = LinearLayout(app).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            minimumWidth = dp(76)
            minimumHeight = dp(68)
            setPadding(dp(12), dp(10), dp(12), dp(10))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xFF1F6B3A.toInt())
            }
            elevation = dp(8).toFloat()
            contentDescription = "Referansları gönder"
        }
        kutu.addView(
            ImageView(app).apply {
                setImageResource(R.drawable.s_tanitim)
                setColorFilter(Color.WHITE)
            },
            LinearLayout.LayoutParams(dp(24), dp(24)),
        )
        kutu.addView(
            TextView(app).apply {
                text = "Referanslar"
                setTextColor(Color.WHITE)
                textSize = 12f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
                gravity = Gravity.CENTER
                setPadding(0, dp(4), 0, 0)
            },
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT),
        )

        // Köşede küçük ×: balonu kapatır. Kutunun dışına taşsın diye dış çerçeve
        // biraz boşluklu; × çerçevenin sağ üst köşesinde.
        val cerceve = FrameLayout(app)
        cerceve.addView(
            kutu,
            FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(10), dp(10), dp(10), dp(10))
            },
        )
        val kapat = TextView(app).apply {
            text = "×"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            includeFontPadding = false
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF3C4A42.toInt())
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(10).toFloat()
            contentDescription = "Kapat"
            setOnClickListener { kaldir(app) }
        }
        cerceve.addView(
            kapat,
            FrameLayout.LayoutParams(dp(22), dp(22)).apply {
                gravity = Gravity.TOP or Gravity.END
            },
        )

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16)
            y = dp(140)
        }

        // Dokunma: kısa basış gönderir, sürükleme taşır, uzun basış kapatır.
        val esik = ViewConfiguration.get(app).scaledTouchSlop
        var basX = 0f
        var basY = 0f
        var ilkX = 0
        var ilkY = 0
        var tasindi = false
        var uzunOldu = false
        val uzun = Runnable {
            uzunOldu = true
            kutu.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
            kaldir(app)
        }
        kutu.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    basX = e.rawX
                    basY = e.rawY
                    ilkX = lp.x
                    ilkY = lp.y
                    tasindi = false
                    uzunOldu = false
                    ana.postDelayed(uzun, ViewConfiguration.getLongPressTimeout().toLong())
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - basX
                    val dy = e.rawY - basY
                    if (!tasindi && (abs(dx) > esik || abs(dy) > esik)) {
                        tasindi = true
                        ana.removeCallbacks(uzun)
                    }
                    if (tasindi) {
                        // Sağdan ölçülüyor (Gravity.END): sağa çekmek x'i azaltır.
                        lp.x = ilkX - dx.toInt()
                        lp.y = (ilkY + dy.toInt()).coerceAtLeast(0)
                        try {
                            wm.updateViewLayout(cerceve, lp)
                        } catch (_: Exception) {
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    ana.removeCallbacks(uzun)
                    if (!tasindi && !uzunOldu) {
                        kaldir(app)
                        if (!Tanitim.dosyaGonder(app, tel)) {
                            Toast.makeText(app, "WhatsApp açılamadı", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                MotionEvent.ACTION_CANCEL -> ana.removeCallbacks(uzun)
            }
            true
        }

        return try {
            wm.addView(cerceve, lp)
            gorunum = cerceve
            ana.postDelayed(zamanAsimi, OMUR_MS)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun kaldir(c: Context) {
        ana.removeCallbacks(zamanAsimi)
        val v = gorunum ?: return
        gorunum = null
        try {
            c.applicationContext.getSystemService(WindowManager::class.java)?.removeView(v)
        } catch (_: Exception) {
        }
    }
}
