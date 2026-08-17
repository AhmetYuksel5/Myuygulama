package com.ahmety.uygulama.launcher

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Tek bir simge için birleşik jest algılayıcı:
 *  - tek dokun
 *  - çift dokun
 *  - yukarı / aşağı sürükleme (dikey; yatay sürükleme sayfaya — pager'a — bırakılır)
 *  - uzun bas (simgeyi düzenlemek için)
 *
 * Dikey hareketleri tüketiyoruz ki yatay çalışan HorizontalPager dikey bir
 * kaydırmayı çalmasın; yatayı tüketmeyip pager'ın sayfa geçişine izin veriyoruz.
 */
fun Modifier.iconGestures(
    onTap: () -> Unit,
    onDoubleTap: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDown: () -> Unit,
    onLongPress: () -> Unit,
): Modifier = pointerInput(Unit) {
    val slop = viewConfiguration.touchSlop
    val swipeThreshold = 48.dp.toPx()
    val doubleTapTimeout = viewConfiguration.doubleTapTimeoutMillis
    val longPressTimeout = viewConfiguration.longPressTimeoutMillis

    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var totalX = 0f
        var totalY = 0f
        var isDrag = false

        // 1) Aşağıdakilerden biri olana kadar bekle: parmak kalktı, sürükleme
        //    eşiği aşıldı ya da uzun-bas süresi doldu.
        val settled = withTimeoutOrNull(longPressTimeout) {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null || change.changedToUp()) {
                    return@withTimeoutOrNull "up"
                }
                totalX = change.position.x - down.position.x
                totalY = change.position.y - down.position.y
                if (abs(totalX) > slop || abs(totalY) > slop) {
                    isDrag = true
                    return@withTimeoutOrNull "drag"
                }
            }
            @Suppress("UNREACHABLE_CODE")
            "up"
        }

        if (settled == null) {
            // Uzun bas: parmak kıpırdamadan basılı kaldı.
            onLongPress()
            consumeUntilUp(down.id)
            return@awaitEachGesture
        }

        if (isDrag) {
            // Sürüklemeyi parmak kalkana kadar izle; dikey baskınsa tüket.
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                totalX = change.position.x - down.position.x
                totalY = change.position.y - down.position.y
                if (abs(totalY) > abs(totalX)) change.consume()
                if (change.changedToUp()) break
            }
            if (abs(totalY) > swipeThreshold && abs(totalY) > abs(totalX)) {
                if (totalY < 0) onSwipeUp() else onSwipeDown()
            }
            return@awaitEachGesture
        }

        // 2) Hızlı dokunma oldu. İkinci bir dokunma gelirse çift dokun.
        val second = withTimeoutOrNull(doubleTapTimeout) {
            awaitFirstDown(requireUnconsumed = false)
        }
        if (second != null) onDoubleTap() else onTap()
    }
}

private suspend fun androidx.compose.ui.input.pointer.AwaitPointerEventScope.consumeUntilUp(
    id: PointerId,
) {
    while (true) {
        val event = awaitPointerEvent()
        val change = event.changes.firstOrNull { it.id == id } ?: return
        change.consume()
        if (change.changedToUp()) return
    }
}
