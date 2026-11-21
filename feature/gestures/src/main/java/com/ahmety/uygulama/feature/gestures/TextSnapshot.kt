package com.ahmety.uygulama.feature.gestures

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * "Yazı görüntüsü": ekran görüntüsü alınamayan ekranlarda ekrandaki
 * yazılardan bir resim.
 *
 * Banka uygulamaları ekranlarını güvenli pencere olarak işaretliyor; sistem
 * ekran görüntüsünü engelliyor ya da siyah çıkarıyor. Ödemeyi göstermek için
 * başka telefondan fotoğraf çekmek gerekiyordu. Güvenli pencere görüntüyü
 * engelliyor ama yazıların erişilebilirlikle okunmasını genelde engellemiyor
 * (ekran okuyucular böyle çalışıyor). Yazılar ekrandaki yerlerinden okunup
 * aynı yerleşimle beyaz bir sayfaya çiziliyor; tutar, alıcı, tarih ekrandaki
 * gibi kendi yerinde duruyor.
 *
 * Altına uygulamanın adı, zaman ve "ekran yazısından oluşturuldu" notu
 * ekleniyor: bu bir ekran görüntüsü değil, karşı taraf bunu bilsin.
 */
internal object TextSnapshot {

    private val handler = Handler(Looper.getMainLooper())

    fun take(service: AccessibilityService): Boolean {
        val texts = collect(service)
        if (texts.isEmpty()) {
            toast(service, "Bu ekranda okunabilen yazı yok; uygulama yazılarını da gizliyor olabilir.")
            return false
        }
        val app = appName(service)
        Thread {
            val bitmap = runCatching { render(service, texts, app) }.getOrNull()
            val uri = bitmap?.let { runCatching { save(service, it) }.getOrNull() }
            handler.post {
                if (uri == null) {
                    toast(service, "Yazı görüntüsü kaydedilemedi.")
                    return@post
                }
                toast(service, "Galeriye kaydedildi: Pictures/Merkez")
                val send = Intent(Intent.ACTION_SEND)
                    .setType("image/jpeg")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                runCatching {
                    service.startActivity(
                        Intent.createChooser(send, "Yazı görüntüsünü paylaş")
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION),
                    )
                }
            }
        }.start()
        return true
    }

    private class Piece(val text: String, val bounds: Rect)

    /** Ekrandaki görünür yazılar ve yerleri; Merkez'in kendi katmanları hariç. */
    private fun collect(service: AccessibilityService): List<Piece> {
        val out = mutableListOf<Piece>()
        val roots = runCatching { service.windows }.getOrNull().orEmpty()
            .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
            .mapNotNull { runCatching { it.root }.getOrNull() }
            .filter { it.packageName?.toString() != service.packageName }
            .ifEmpty { listOfNotNull(runCatching { service.rootInActiveWindow }.getOrNull()) }
        roots.forEach { walk(it, out, 0) }
        return out
    }

    private fun walk(node: AccessibilityNodeInfo?, out: MutableList<Piece>, depth: Int) {
        if (node == null || depth > MAX_DEPTH || out.size > MAX_PIECES) return
        if (node.isVisibleToUser && !node.isPassword) {
            // Yalnız kendi yazısı olan düğüm: kapsayıcının açıklaması çocuklarının
            // yazısını tekrar ediyordu.
            val text = node.text?.toString()?.trim().orEmpty()
                .ifEmpty { if (node.childCount == 0) node.contentDescription?.toString()?.trim().orEmpty() else "" }
            if (text.isNotEmpty()) {
                val bounds = Rect().also { node.getBoundsInScreen(it) }
                if (!bounds.isEmpty) out += Piece(text, bounds)
            }
        }
        for (i in 0 until node.childCount) walk(node.getChild(i), out, depth + 1)
    }

    private fun render(service: AccessibilityService, pieces: List<Piece>, app: String): Bitmap {
        val metrics = service.resources.displayMetrics
        val width = metrics.widthPixels
        // Sayfa yalnız yazıların kapladığı yükseklik kadar; üstteki durum
        // çubuğu ve alttaki boşluk kırpılıyor.
        val top = (pieces.minOf { it.bounds.top } - PAD).coerceAtLeast(0)
        val bottom = pieces.maxOf { it.bounds.bottom } + PAD
        val footer = (metrics.density * 56).toInt()
        val height = (bottom - top) + footer

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1E.toInt() }

        pieces.forEach { piece ->
            val box = piece.bounds
            val boxWidth = box.width().coerceAtLeast(1)
            // Punto kutunun boyuna göre: tek satırlık kutuda yazı kutuyu doldursun.
            paint.textSize = (box.height() * 0.55f).coerceIn(metrics.density * 11, metrics.density * 26)
            paint.typeface = if (box.height() > metrics.density * 40) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val layout = StaticLayout.Builder.obtain(piece.text, 0, piece.text.length, paint, boxWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setMaxLines(((box.height() / paint.textSize).toInt()).coerceAtLeast(1))
                .setEllipsize(TextUtils.TruncateAt.END)
                .build()
            canvas.save()
            val y = box.top - top + ((box.height() - layout.height) / 2f).coerceAtLeast(0f)
            canvas.translate(box.left.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
        }

        // Alt not: ne olduğu açıkça yazıyor.
        val noteTop = (bottom - top).toFloat()
        canvas.drawRect(0f, noteTop, width.toFloat(), height.toFloat(), Paint().apply { color = 0xFFF2F2F4.toInt() })
        val note = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = 0xFF6B6B70.toInt()
            textSize = metrics.density * 12
        }
        val time = SimpleDateFormat("d MMMM yyyy HH:mm", Locale.forLanguageTag("tr")).format(Date())
        canvas.drawText(
            "$app · $time · ekran yazısından oluşturuldu (ekran görüntüsü değil)",
            metrics.density * 16,
            noteTop + footer / 2f + note.textSize / 3f,
            note,
        )
        return bitmap
    }

    private fun save(service: AccessibilityService, bitmap: Bitmap): android.net.Uri? {
        val name = "yazi_goruntusu_" + SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(Date()) + ".jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Merkez")
        }
        val resolver = service.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        return uri
    }

    private fun appName(service: AccessibilityService): String = runCatching {
        val pkg = service.rootInActiveWindow?.packageName?.toString() ?: return@runCatching ""
        service.packageManager.getApplicationLabel(service.packageManager.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault("")

    private fun toast(service: AccessibilityService, text: String) {
        Toast.makeText(service, text, Toast.LENGTH_LONG).show()
    }

    private const val MAX_DEPTH = 80
    private const val MAX_PIECES = 400
    private const val PAD = 24
}
