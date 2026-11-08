package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject

/** Klavyenin yanındaki düğmeyle yazılan hazır metin: IBAN, adres, fiyat listesi… */
data class Snippet(val title: String, val text: String)

/**
 * Hazır metinlerin kaydı.
 *
 * Sesli yazmanın dosyasında duruyor: ikisi de klavyenin üstündeki aynı
 * katmandan çalışıyor, servis tek dosyayı okuyor.
 */
class SnippetStore(context: Context) {
    private val prefs = context.getSharedPreferences(VoiceTyping.PREFS, Context.MODE_PRIVATE)

    fun all(): List<Snippet> = runCatching {
        val array = JSONArray(prefs.getString(KEY, "[]"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            Snippet(item.optString("title"), item.optString("text"))
        }.filter { it.text.isNotBlank() }
    }.getOrDefault(emptyList())

    fun save(items: List<Snippet>) {
        val array = JSONArray()
        items.forEach { array.put(JSONObject().put("title", it.title).put("text", it.text)) }
        prefs.edit().putString(KEY, array.toString()).apply()
    }

    private companion object {
        const val KEY = "snippets"
    }
}

/**
 * Mikrofonun solundaki küçük düğme ve dokununca açılan liste.
 *
 * Liste ayrı bir pencere: düğmeye bağlı kalırsa düğmenin küçük penceresi
 * listeyi keserdi. Dışarı dokunmak ya da klavyenin kapanması listeyi
 * kapatıyor. Hiç hazır metin yoksa düğme de çıkmıyor; boş bir listeye
 * götüren düğme yalnız yer kaplardı.
 */
internal class SnippetBar(
    private val service: AccessibilityService,
    private val insert: (String) -> Unit,
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private val store = SnippetStore(service)

    private var button: View? = null
    private var buttonParams: WindowManager.LayoutParams? = null
    private var panel: View? = null

    /** Düğmeyi mikrofonun soluna koyar; mikrofonun sol üst köşesi ve boyu verilir. */
    fun place(micX: Int, micY: Int, micSize: Int) {
        if (store.all().isEmpty()) {
            hide()
            return
        }
        val size = dp(SIZE_DP)
        val x = (micX - size - dp(GAP_DP)).coerceAtLeast(0)
        val y = micY + (micSize - size) / 2
        val lp = buttonParams
        val shown = button
        if (shown != null && lp != null) {
            if (lp.x != x || lp.y != y) {
                lp.x = x
                lp.y = y
                runCatching { windowManager.updateViewLayout(shown, lp) }
            }
            return
        }
        val view = SnippetButton(service).apply { setOnClickListener { toggle() } }
        val params = overlayParams(size, size, x, y)
        runCatching { windowManager.addView(view, params) }.onSuccess {
            button = view
            buttonParams = params
        }
    }

    fun hide() {
        closePanel()
        button?.let { runCatching { windowManager.removeView(it) } }
        button = null
        buttonParams = null
    }

    private fun toggle() {
        if (panel != null) closePanel() else openPanel()
    }

    private fun openPanel() {
        val anchor = buttonParams ?: return
        val items = store.all()
        if (items.isEmpty()) return

        val list = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        items.forEach { item ->
            list.addView(row(item))
        }
        val scroll = ScrollView(service).apply {
            addView(list)
            background = GradientDrawable().apply {
                setColor(Color.argb(240, 0x2B, 0x2F, 0x36))
                cornerRadius = dp(14f).toFloat()
            }
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))
        }

        val width = dp(PANEL_WIDTH_DP)
        scroll.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(dp(PANEL_MAX_DP), View.MeasureSpec.AT_MOST),
        )
        val height = scroll.measuredHeight.coerceAtMost(dp(PANEL_MAX_DP))
        val screenWidth = service.resources.displayMetrics.widthPixels
        val x = (anchor.x + anchor.width - width).coerceIn(0, (screenWidth - width).coerceAtLeast(0))
        val y = (anchor.y - height - dp(GAP_DP)).coerceAtLeast(0)
        val params = overlayParams(width, height, x, y).apply {
            flags = flags or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
        }
        scroll.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_OUTSIDE) closePanel()
            false
        }
        runCatching { windowManager.addView(scroll, params) }.onSuccess { panel = scroll }
    }

    private fun row(item: Snippet): View = LinearLayout(service).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(12f), dp(9f), dp(12f), dp(9f))
        isClickable = true
        background = GradientDrawable().apply {
            setColor(Color.TRANSPARENT)
            cornerRadius = dp(10f).toFloat()
        }
        if (item.title.isNotBlank()) {
            addView(
                TextView(service).apply {
                    text = item.title
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    typeface = Typeface.DEFAULT_BOLD
                },
            )
        }
        addView(
            TextView(service).apply {
                text = item.text
                setTextColor(Color.argb(200, 255, 255, 255))
                textSize = 13f
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            },
        )
        setOnClickListener {
            closePanel()
            GestureFeedback.vibrateOnce(service, 12L)
            insert(item.text)
        }
    }

    private fun closePanel() {
        panel?.let { runCatching { windowManager.removeView(it) } }
        panel = null
    }

    private fun overlayParams(w: Int, h: Int, x: Int, y: Int) = WindowManager.LayoutParams(
        w,
        h,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun dp(value: Float) = (value * density).toInt()

    private companion object {
        const val SIZE_DP = 36f
        const val GAP_DP = 6f
        const val PANEL_WIDTH_DP = 260f
        const val PANEL_MAX_DP = 300f
    }
}

/** Yuvarlak düğme, içinde üç kısa satır: "hazır yazı" diye okunuyor. */
private class SnippetButton(context: Context) : View(context) {
    private val d = resources.displayMetrics.density
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(215, 0x2B, 0x2F, 0x36) }
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * d
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        canvas.drawCircle(cx, cy, width / 2f - 1.5f * d, disc)
        // Belge gibi: üstte uzun, altta kısa satırlar.
        val left = cx - 6f * d
        canvas.drawLine(left, cy - 5f * d, cx + 6f * d, cy - 5f * d, ink)
        canvas.drawLine(left, cy, cx + 6f * d, cy, ink)
        canvas.drawLine(left, cy + 5f * d, cx + 2f * d, cy + 5f * d, ink)
    }
}
