package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.SystemClock
import android.view.View
import kotlin.math.hypot
import kotlin.math.sqrt

/**
 * Ekranın dibindeki tutamak çubuğu: uçları yuvarlatılmış yatay bir dikdörtgen,
 * telefonun kendi gezinme çizgisi gibi.
 *
 * Eni ve kalınlığı ayardan geliyor; çizim pencerenin tamamını dolduruyor, o
 * yüzden burada ölçü yok — boyutu pencere veriyor.
 */
internal class HandleView(context: Context) : View(context) {

    // Boya tam opak; saydamlığı görünümün kendi alfası taşıyor.
    //
    // Eskiden saydamlık hem boyaya hem görünümün alfasına giriyordu ve
    // ikisi çarpılıyordu: sönükleşme %35'i dayattığı için %100 seçmek bile
    // yarı saydam bir çubuk veriyor, %20 ile %60 arasındaki fark küçücük
    // bir çubukta seçilemiyordu. Ayar tek yerde durunca "ne seçtiysen onu
    // görüyorsun" oluyor.
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(255, 210, 210, 214)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(255, 255, 255, 255)
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
    /**
     * Kuyruk tek bir dolu şekil.
     *
     * Eskiden her iki nokta arası ayrı bir çizgiydi ve uçları yuvarlaktı:
     * yarı saydam uçlar üst üste binince o yerlerde renk koyulaşıp boncuk
     * gibi noktalar çıkıyor, kalınlık parça parça değiştiği için kenar
     * tırtıklı oluyordu. Tek şekilde ne üst üste binme var ne basamak.
     */
    private val tail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val tailPath = Path()

    // Düzleştirilmiş yol: x,y ikilileri. Her karede yeniden doluyor, o
    // yüzden dizi bir kez ayrılıp tekrar tekrar kullanılıyor.
    private var smooth = FloatArray(512)
    private var smoothCount = 0
    private var edgeX = 0f
    private var edgeY = 0f

    // Üst köşe bölgesi: imlecin hangi yarıda olduğunu gösteren ince çizgi.
    private var zone = ZONE_NONE
    private val zoneFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

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

    /** İmleç üst köşe bölgesine girdi ya da çıktı. */
    fun setZone(next: Int) {
        if (zone == next) return
        zone = next
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
        drawZone(canvas)
        drawTail(canvas)

        canvas.drawCircle(x, y, 16f * density, halo)
        canvas.drawCircle(x, y, 16f * density, outer)
        canvas.drawCircle(x, y, 3.5f * density, dot)

        // Parmak dursa da kuyruk kendi kendine sönmeli; nokta kaldıkça
        // bir sonraki kareyi iste.
        if (trace.size > 1) postInvalidateOnAnimation()
    }

    /**
     * Üst köşedeki şerit: imleç orada bırakılırsa ne olacağını söylüyor.
     *
     * Şerit yalnız imleç o bölgedeyken görünüyor; sürekli duran bir çubuk
     * ekranın tepesini sahiplenirdi.
     */
    private fun drawZone(canvas: Canvas) {
        if (zone == ZONE_NONE) return
        val half = width / 2f
        val left = if (zone == ZONE_LEFT) 0f else half
        val bottom = ZONE_HEIGHT_DP * density
        zoneFill.color = Color.argb(
            200,
            Color.red(izRengi),
            Color.green(izRengi),
            Color.blue(izRengi),
        )
        canvas.drawRect(left, 0f, left + half, bottom, zoneFill)
    }

    /**
     * Kuyruğu çizer: yumuşatılmış yol, uca doğru incelen kalınlık, boydan
     * boya süzülen bir saydamlık.
     */
    private fun drawTail(canvas: Canvas) {
        if (trace.size < 2) return
        val now = SystemClock.uptimeMillis()
        // Parmak durunca kuyruk hem kısalıyor (eski noktalar düşüyor) hem
        // bütün olarak sönüyor; ikisi birlikte "dağıldı" izlenimi veriyor.
        val fade = (1f - (now - trace.last().at).toFloat() / TAIL_MS).coerceIn(0f, 1f)
        if (fade <= 0f) return
        val count = resample()
        if (count < 3) return

        val maxHalf = TAIL_WIDTH_DP * density * 0.5f
        tailPath.reset()
        // Bir kenar baştan sona, öteki sondan başa: tek kapalı şekil.
        for (i in 0 until count) {
            edgeAt(i, count, maxHalf, 1f)
            if (i == 0) tailPath.moveTo(edgeX, edgeY) else tailPath.lineTo(edgeX, edgeY)
        }
        for (i in count - 1 downTo 0) {
            edgeAt(i, count, maxHalf, -1f)
            tailPath.lineTo(edgeX, edgeY)
        }
        tailPath.close()

        val r = Color.red(izRengi)
        val g = Color.green(izRengi)
        val b = Color.blue(izRengi)
        val solid = Color.argb((TAIL_ALPHA * fade).toInt(), r, g, b)
        val tipX = smooth[0]
        val tipY = smooth[1]
        val headX = smooth[2 * count - 2]
        val headY = smooth[2 * count - 1]
        if (hypot(headX - tipX, headY - tipY) > 2f * density) {
            // Saydamlık parça parça değil, baştan sona süzülerek değişiyor;
            // basamak çıkmasın diye tek bir geçiş olarak veriliyor. Gövde
            // erken dolgunlaşsın diye ortada bir durak var.
            tail.color = Color.WHITE
            tail.shader = LinearGradient(
                tipX,
                tipY,
                headX,
                headY,
                intArrayOf(Color.argb(0, r, g, b), Color.argb((TAIL_ALPHA * fade * 0.55f).toInt(), r, g, b), solid),
                floatArrayOf(0f, 0.35f, 1f),
                Shader.TileMode.CLAMP,
            )
        } else {
            // İki uç üst üsteyse geçişin yönü yok; düz renk.
            tail.shader = null
            tail.color = solid
        }
        canvas.drawPath(tailPath, tail)
    }

    /**
     * İzi düzleştirir: dokunma noktaları arasına Catmull-Rom eğrisiyle ara
     * noktalar konuyor.
     *
     * Dokunma olayları seyrek geldiği için ham iz köşeli bir çoklu çizgi;
     * doğrudan çizilince dönüşler kırık görünüyordu. Ara noktalar hem
     * kıvrımı yuvarlıyor hem de kalınlığın her adımda azıcık değişmesini
     * sağlıyor — basamak böyle kayboluyor.
     */
    private fun resample(): Int {
        smoothCount = 0
        val n = trace.size
        if (n < 2) return 0
        val step = STEP_DP * density
        for (i in 0 until n - 1) {
            val p0 = trace[(i - 1).coerceAtLeast(0)]
            val p1 = trace[i]
            val p2 = trace[i + 1]
            val p3 = trace[(i + 2).coerceAtMost(n - 1)]
            val span = hypot(p2.x - p1.x, p2.y - p1.y)
            val steps = (span / step).toInt().coerceIn(1, 12)
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                add(
                    catmull(p0.x, p1.x, p2.x, p3.x, t),
                    catmull(p0.y, p1.y, p2.y, p3.y, t),
                )
            }
        }
        val last = trace.last()
        add(last.x, last.y)
        return smoothCount
    }

    private fun add(px: Float, py: Float) {
        if (smoothCount * 2 + 2 > smooth.size) smooth = smooth.copyOf(smooth.size * 2)
        smooth[smoothCount * 2] = px
        smooth[smoothCount * 2 + 1] = py
        smoothCount++
    }

    private fun catmull(a: Float, b: Float, c: Float, d: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        return 0.5f * (
            2f * b +
                (c - a) * t +
                (2f * a - 5f * b + 4f * c - d) * t2 +
                (3f * b - a - 3f * c + d) * t3
            )
    }

    /**
     * Yolun [i]. noktasındaki kenar noktası; [side] hangi kenar.
     *
     * Kalınlık uçta sıfır, halkaya doğru karekökle artıyor: uç sivri
     * kalıyor, gövde çabuk dolgunlaşıyor. Uçta sıfır olması, en eski
     * nokta düştüğünde bir şeyin "kopmasını" da engelliyor.
     */
    private fun edgeAt(i: Int, count: Int, maxHalf: Float, side: Float) {
        val px = smooth[2 * i]
        val py = smooth[2 * i + 1]
        val prev = (i - 1).coerceAtLeast(0)
        val next = (i + 1).coerceAtMost(count - 1)
        var dx = smooth[2 * next] - smooth[2 * prev]
        var dy = smooth[2 * next + 1] - smooth[2 * prev + 1]
        val len = hypot(dx, dy)
        if (len < 0.001f) {
            dx = 0f
            dy = 1f
        } else {
            dx /= len
            dy /= len
        }
        val half = maxHalf * sqrt(i.toFloat() / (count - 1))
        edgeX = px - dy * half * side
        edgeY = py + dx * half * side
    }

    internal companion object {
        const val ZONE_NONE = 0
        const val ZONE_LEFT = 1
        const val ZONE_RIGHT = 2

        /**
         * Şeridin kalınlığı. İnce tutuluyor: kalın ve yazılı şerit ekranın
         * tepesini kaplıyordu; titreşim zaten bölgeye girildiğini söylüyor.
         */
        const val ZONE_HEIGHT_DP = 3f

        const val TAIL_MS = 220L
        const val TAIL_WIDTH_DP = 7f
        const val TAIL_ALPHA = 215f
        const val MAX_TAIL_DP = 280f

        /** Düzleştirilmiş yolda iki nokta arası hedef uzaklık (dp). */
        const val STEP_DP = 3f
    }
}
