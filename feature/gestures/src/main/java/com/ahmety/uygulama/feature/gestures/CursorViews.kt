package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View

/** Kenardaki tutamak topu — radyal parlaklıkla, fotoğraftaki gri küre gibi. */
internal class HandleView(context: Context, private val opacityPercent: Int) : View(context) {

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb((255 * opacityPercent / 100).coerceIn(40, 255), 210, 210, 214)
    }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb((255 * opacityPercent / 100).coerceIn(40, 255), 255, 255, 255)
        strokeWidth = 2f * resources.displayMetrics.density
    }

    override fun onDraw(canvas: Canvas) {
        val r = minOf(width, height) / 2f - ring.strokeWidth
        canvas.drawCircle(width / 2f, height / 2f, r, fill)
        canvas.drawCircle(width / 2f, height / 2f, r, ring)
    }
}

/**
 * Ekranda gezen sanal imleç: beyaz nişan halkası + ortada nokta.
 * Fotoğraftaki sol üstteki gösterge.
 */
internal class CursorView(context: Context) : View(context) {

    private val outer = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.WHITE
        strokeWidth = 3f * resources.displayMetrics.density
        setShadowLayer(4f, 0f, 0f, Color.argb(160, 0, 0, 0))
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
        setShadowLayer(4f, 0f, 0f, Color.argb(160, 0, 0, 0))
    }

    init {
        // setShadowLayer donanım hızlandırmada bazı sürümlerde sorun çıkarır.
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val density = resources.displayMetrics.density
        canvas.drawCircle(cx, cy, 16f * density, outer)
        canvas.drawCircle(cx, cy, 3.5f * density, dot)
    }
}
