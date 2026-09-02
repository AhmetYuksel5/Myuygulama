package com.ahmety.uygulama.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp

/**
 * Boş durum çizimleri.
 *
 * Hepsi Canvas'a çiziliyor: pakete resim koymak hem yer kaplıyor hem de
 * temayla birlikte değişmiyor. Renk uygulamanın kendi paletinden geldiği
 * için koyu temada da doğru duruyor.
 *
 * Boş ekranlar tek satır gri yazıydı — oysa bunlar uygulamanın en çok
 * görülen anları: desteyi bitirdiğin an, henüz hiçbir şey kaydetmediğin an.
 */
object MerkezGlyphs {

    /** Üst üste duran üç kart — boş deste. */
    @Composable
    fun CardStack(modifier: Modifier = Modifier) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(modifier = modifier.size(96.dp)) {
            val w = size.width * 0.60f
            val h = size.height * 0.66f
            listOf(2, 1, 0).forEach { index ->
                rotate(degrees = -index * 8f, pivot = center) {
                    drawRoundRect(
                        color = color.copy(alpha = 0.20f + 0.24f * (2 - index)),
                        topLeft = Offset((size.width - w) / 2f, (size.height - h) / 2f),
                        size = Size(w, h),
                        cornerRadius = CornerRadius(10.dp.toPx()),
                    )
                }
            }
        }
    }

    /** Yan yana üç kitap sırtı ve altında raf — boş kitaplık. */
    @Composable
    fun Shelf(modifier: Modifier = Modifier) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(modifier = modifier.size(96.dp)) {
            val width = size.width * 0.17f
            val gap = size.width * 0.07f
            val heights = listOf(0.62f, 0.78f, 0.52f)
            var x = (size.width - (width * 3 + gap * 2)) / 2f
            heights.forEachIndexed { index, factor ->
                val h = size.height * factor
                drawRoundRect(
                    color = color.copy(alpha = 0.30f + index * 0.20f),
                    topLeft = Offset(x, size.height * 0.86f - h),
                    size = Size(width, h),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                )
                x += width + gap
            }
            drawRoundRect(
                color = color.copy(alpha = 0.55f),
                topLeft = Offset(size.width * 0.12f, size.height * 0.86f),
                size = Size(size.width * 0.76f, 3.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
        }
    }

    /** Kaydırılmış birkaç sayfa — boş Pocket. */
    @Composable
    fun Pages(modifier: Modifier = Modifier) {
        val color = MaterialTheme.colorScheme.primary
        Canvas(modifier = modifier.size(96.dp)) {
            val w = size.width * 0.52f
            val h = size.height * 0.64f
            listOf(2, 1, 0).forEach { index ->
                val shift = index * size.width * 0.07f
                drawRoundRect(
                    color = color.copy(alpha = 0.20f + 0.26f * (2 - index)),
                    topLeft = Offset(
                        (size.width - w) / 2f + shift,
                        (size.height - h) / 2f + shift,
                    ),
                    size = Size(w, h),
                    cornerRadius = CornerRadius(8.dp.toPx()),
                )
            }
        }
    }
}
