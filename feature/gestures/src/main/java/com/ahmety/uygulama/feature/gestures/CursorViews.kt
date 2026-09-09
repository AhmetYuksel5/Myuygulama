package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.view.View

/**
 * Ekranın dibindeki tutamak çubuğu: uçları yuvarlatılmış yatay bir dikdörtgen,
 * telefonun kendi gezinme çizgisi gibi.
 *
 * Eni ve kalınlığı ayardan geliyor; çizim pencerenin tamamını dolduruyor, o
 * yüzden burada ölçü yok — boyutu pencere veriyor.
 */
internal class HandleView(context: Context, private val opacityPercent: Int) : View(context) {

    private val alpha = (255 * opacityPercent / 100).coerceIn(40, 255)

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(alpha, 210, 210, 214)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(alpha, 255, 255, 255)
        strokeWidth = 2f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val inset = ring.strokeWidth / 2f
        val h = height - ring.strokeWidth
        // Yarıçap kalınlığın yarısı: çubuk ne kadar inceyse o kadar hap
        // biçiminde, kalınlaşınca köşeleri yumuşak dikdörtgen kalıyor.
        val r = h / 2f
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, r, r, fill)
        canvas.drawRoundRect(inset, inset, width - inset, height - inset, r, r, ring)
    }
}

/**
 * Ekranda gezen sanal imleç: beyaz nişan halkası, ortada nokta ve
 * arkasında kuyruklu yıldız gibi mat kırmızı bir iz.
 *
 * Görünüm tam ekran bir pencere; imleç küçük bir pencere olsaydı kuyruk
 * onun dışına taşamazdı. Hareket pencereyi taşımak değil yeniden çizmek,
 * o da daha ucuz.
 *
 * Kuyruk son [TAIL_MS] içinde geçilen noktalardan çiziliyor. Uzunluğu
 * bilerek zamana bağlı: hızlı giderken aynı sürede daha uzun yol
 * alındığı için kuyruk uzuyor, yavaşken kısalıyor, durunca noktalar
 * eskiyip kuyruk sönüyor — elastikiyet buradan geliyor.
 */
internal class CursorView(context: Context, private val izRengi: Int) : View(context) {

    private val density = resources.displayMetrics.density

    // Gölge katmanı yerine halkanın altına koyu bir hale: gölge tam ekran
    // bir yazılım katmanı istiyor, her karede ekran boyu bitmap demek.
    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(110, 0, 0, 0)
        strokeWidth = 5f * density
    }
    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f * density
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }
    private val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }

    private class Trace(val x: Float, val y: Float, val at: Long)

    private val trace = ArrayDeque<Trace>()
    private var x = 0f
    private var y = 0f
    private val onScreen = IntArray(2)

    /** İmleç ekran koordinatında buraya geldi. */
    fun moveTo(nx: Float, ny: Float) {
        x = nx
        y = ny
        trace.addLast(Trace(nx, ny, SystemClock.uptimeMillis()))
        prune()
        invalidate()
    }

    /** İmleç yeni doğdu; eski iz onunla birlikte gelmesin. */
    fun reset(nx: Float, ny: Float) {
        trace.clear()
        moveTo(nx, ny)
    }

    private fun prune() {
        val now = SystemClock.uptimeMillis()
        while (trace.isNotEmpty() && now - trace.first().at > TAIL_MS) trace.removeFirst()
        // Çok hızlı savrulunca kuyruk ekranı boydan boya kesmesin.
        var length = 0f
        var keep = trace.size
        for (i in trace.size - 1 downTo 1) {
            length += kotlin.math.hypot(
                trace[i].x - trace[i - 1].x,
                trace[i].y - trace[i - 1].y,
            )
            if (length > MAX_TAIL_DP * density) { keep = trace.size - i; break }
        }
        while (trace.size > keep) trace.removeFirst()
    }

    override fun onDraw(canvas: Canvas) {
        // Pencere ekranın tepesinden başlamıyor olabilir; çizim yine de
        // ekran koordinatında yapılsın.
        getLocationOnScreen(onScreen)
        canvas.translate(-onScreen[0].toFloat(), -onScreen[1].toFloat())

        prune()
        val now = SystemClock.uptimeMillis()
        for (i in 1 until trace.size) {
            val a = trace[i - 1]
            val b = trace[i]
            // Her parça kendi tazeliği kadar kalın ve koyu: uçta incelip
            // soluyor, halkaya yaklaştıkça dolgunlaşıyor.
            val fresh = (1f - (now - b.at).toFloat() / TAIL_MS).coerceIn(0f, 1f)
            if (fresh <= 0f) continue
            tail.strokeWidth = (TAIL_WIDTH_DP * density * fresh).coerceAtLeast(1f)
            // Renk ayardan, saydamlığı tazeliğinden: uçtaki parça
            // sönük, halkaya yakın olan dolgun.
            tail.color = Color.argb(
                (TAIL_ALPHA * fresh).toInt(),
                Color.red(izRengi),
                Color.green(izRengi),
                Color.blue(izRengi),
            )
            canvas.drawLine(a.x, a.y, b.x, b.y, tail)
        }

        canvas.drawCircle(x, y, 16f * density, halo)
        canvas.drawCircle(x, y, 16f * density, outer)
        canvas.drawCircle(x, y, 3.5f * density, dot)

        // Parmak dursa da kuyruk kendi kendine sönmeli; nokta kaldıkça
        // bir sonraki kareyi iste.
        if (trace.size > 1) postInvalidateOnAnimation()
    }

    private companion object {
        const val TAIL_MS = 220L
        const val TAIL_WIDTH_DP = 7f
        const val TAIL_ALPHA = 215f
        const val MAX_TAIL_DP = 280f
    }
}
