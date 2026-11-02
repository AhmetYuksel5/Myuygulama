package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * İmlecin başı: dokunmanın gideceği noktayı gösteren şekil.
 *
 * Her biçim aynı kurala uyuyor: önce koyu, kalın bir hale, üstüne beyaz
 * ince çizgi. Beyaz tek başına açık bir sayfada, koyu tek başına koyu bir
 * sayfada kayboluyor; ikisi üst üste her zeminde seçiliyor.
 *
 * Çizim burada tek yerde: hem imleç hem ayarlardaki önizleme bunu
 * çağırıyor, ikisi birbirinden ayrışamıyor.
 */
object CursorMarker {
    const val RING = 0
    const val RETICLE = 1
    const val DIAMOND = 2
    const val FOCUS = 3
    const val ARROW = 4
    const val GLASS = 5

    val NAMES = listOf("Halka", "Nişangâh", "Elmas", "Odak", "Ok", "Cam")

    private val halo = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(110, 0, 0, 0)
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()

    fun draw(canvas: Canvas, style: Int, x: Float, y: Float, density: Float, accent: Int) {
        val d = density
        when (style) {
            RETICLE -> {
                // Halka ince, çentikler içeri bakıyor: merkez açık kalıyor,
                // altındaki harf görünüyor; küçük hedefte işe yarayan bu.
                path.reset()
                path.addCircle(x, y, 14f * d, Path.Direction.CW)
                for ((dx, dy) in TICKS) {
                    path.moveTo(x + dx * 14f * d, y + dy * 14f * d)
                    path.lineTo(x + dx * 7.5f * d, y + dy * 7.5f * d)
                }
                stroke(canvas, d, 2f)
                dot(canvas, x, y, 2f * d, Color.WHITE)
            }

            DIAMOND -> {
                val r = 15f * d
                path.reset()
                path.moveTo(x, y - r)
                path.lineTo(x + r, y)
                path.lineTo(x, y + r)
                path.lineTo(x - r, y)
                path.close()
                stroke(canvas, d, 2.5f)
                dot(canvas, x, y, 3f * d, Color.WHITE)
            }

            FOCUS -> {
                // Kameranın odak çerçevesi: yalnız köşeler. Kapalı bir şekil
                // değil, hedefi çerçeveliyor ama örtmüyor.
                val h = 14f * d
                val arm = 6f * d
                path.reset()
                for ((sx, sy) in CORNERS) {
                    val cx = x + sx * h
                    val cy = y + sy * h
                    path.moveTo(cx - sx * arm, cy)
                    path.lineTo(cx, cy)
                    path.lineTo(cx, cy - sy * arm)
                }
                stroke(canvas, d, 2.5f)
                dot(canvas, x, y, 2.5f * d, Color.WHITE)
            }

            ARROW -> {
                // Dokunma okun ucuna gidiyor; şekil ucun sağ altına uzanıyor.
                val s = d * 1.1f
                path.reset()
                path.moveTo(x, y)
                path.lineTo(x, y + 20f * s)
                path.lineTo(x + 5f * s, y + 15.5f * s)
                path.lineTo(x + 8.5f * s, y + 23f * s)
                path.lineTo(x + 11.5f * s, y + 21.5f * s)
                path.lineTo(x + 8f * s, y + 14.5f * s)
                path.lineTo(x + 14.5f * s, y + 14.5f * s)
                path.close()
                halo.strokeWidth = 4f * d
                canvas.drawPath(path, halo)
                fill.color = Color.WHITE
                canvas.drawPath(path, fill)
                line.strokeWidth = 1.2f * d
                line.color = Color.argb(160, 0, 0, 0)
                canvas.drawPath(path, line)
                line.color = Color.WHITE
            }

            GLASS -> {
                // Buzlu cam: altı hafifçe görünüyor, merkez iz renginde.
                fill.color = Color.argb(70, 255, 255, 255)
                canvas.drawCircle(x, y, 17f * d, fill)
                path.reset()
                path.addCircle(x, y, 17f * d, Path.Direction.CW)
                stroke(canvas, d, 1.5f)
                dot(canvas, x, y, 5f * d, Color.WHITE)
                dot(canvas, x, y, 3.5f * d, Color.rgb(Color.red(accent), Color.green(accent), Color.blue(accent)))
            }

            else -> {
                path.reset()
                path.addCircle(x, y, 16f * d, Path.Direction.CW)
                stroke(canvas, d, 3f)
                dot(canvas, x, y, 3.5f * d, Color.WHITE)
            }
        }
    }

    private fun stroke(canvas: Canvas, d: Float, width: Float) {
        halo.strokeWidth = (width + 2f) * d
        line.strokeWidth = width * d
        canvas.drawPath(path, halo)
        canvas.drawPath(path, line)
    }

    private fun dot(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
        fill.color = color
        canvas.drawCircle(x, y, r, fill)
    }

    private val TICKS = listOf(0f to -1f, 1f to 0f, 0f to 1f, -1f to 0f)
    private val CORNERS = listOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)
}

/** Ayarlarda bir biçimin küçük önizlemesi. */
class CursorMarkerPreview(context: Context) : View(context) {
    var style: Int = CursorMarker.RING
        set(value) { field = value; invalidate() }
    var accent: Int = Color.WHITE
        set(value) { field = value; invalidate() }

    override fun onDraw(canvas: Canvas) {
        val d = resources.displayMetrics.density
        // Ok ucundan çiziliyor; ortada dursun diye ucu sol üste kaydırılıyor.
        val shift = if (style == CursorMarker.ARROW) 8f * d else 0f
        CursorMarker.draw(canvas, style, width / 2f - shift, height / 2f - shift * 1.4f, d, accent)
    }
}
