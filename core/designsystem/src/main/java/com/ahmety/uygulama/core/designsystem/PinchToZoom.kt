package com.ahmety.uygulama.core.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput

/**
 * İki parmakla yakınlaştırma.
 *
 * Hazır `detectTransformGestures` tek parmaklı sürüklemeyi de tüketiyor;
 * altındaki liste kaydırılamaz oluyor. Burada olay yalnızca ekranda iki
 * parmak varken ve aradaki mesafe gerçekten değişirken tüketiliyor, yani
 * tek parmakla kaydırma olduğu gibi geçiyor.
 *
 * Geri çağrı oranın kendisini değil **çarpanını** veriyor (1'den büyükse
 * açılıyor, küçükse kapanıyor); sınırları çağıran koyuyor.
 */
fun Modifier.pinchToZoom(
    enabled: Boolean = true,
    onZoom: (change: Float) -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this

    pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            do {
                val event = awaitPointerEvent()
                if (event.changes.size >= 2) {
                    val change = event.calculateZoom()
                    // Bire çok yakın değişimler parmak titremesi; onları
                    // tüketmek kaydırmayı tutuklaştırıyor.
                    if (change != 0f && kotlin.math.abs(change - 1f) > 0.002f) {
                        onZoom(change)
                        event.changes.forEach { it.consume() }
                    }
                }
            } while (event.changes.any { it.pressed })
        }
    }
}
