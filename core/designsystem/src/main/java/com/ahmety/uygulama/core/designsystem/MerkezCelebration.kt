package com.ahmety.uygulama.core.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Kilometre taşı kutlaması.
 *
 * Her işaretlemede bir şey patlatmak birkaç günde bıkkınlık veriyor;
 * yalnızca yedinci, otuzuncu, yüzüncü ve üç yüz altmış beşinci günde
 * çalışıyor. Duolingo'nun kendi ölçümü de bunu söylüyor: kutlamayı
 * seyrek tutmak etkisini koruyan şey.
 *
 * Parçacıklar tek bir Canvas'a çiziliyor — ne kütüphane var ne dosya.
 */
@Composable
fun MerkezCelebration(
    play: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onFinished: () -> Unit = {},
) {
    if (!play) return

    val progress = remember { Animatable(0f) }
    val particles = remember {
        List(PARTICLE_COUNT) {
            Particle(
                angle = Random.nextFloat() * 2f * Math.PI.toFloat(),
                speed = 0.55f + Random.nextFloat() * 0.75f,
                size = 3f + Random.nextFloat() * 4f,
                tilt = 0.7f + Random.nextFloat() * 0.6f,
            )
        }
    }

    LaunchedEffect(Unit) {
        progress.animateTo(1f, tween(durationMillis = 900, easing = LinearEasing))
        onFinished()
    }

    Canvas(modifier = modifier) {
        val t = progress.value
        val reach = size.minDimension * 0.55f
        particles.forEach { particle ->
            // Yukarı fırlayıp yerçekimine kapılıyor: t² terimi düşüşü veriyor.
            val distance = reach * particle.speed * t
            val x = center.x + cos(particle.angle) * distance
            val y = center.y + sin(particle.angle) * distance * particle.tilt +
                reach * 0.9f * t * t
            drawCircle(
                color = accent,
                radius = particle.size * (1f - t),
                center = Offset(x, y),
                alpha = (1f - t * t).coerceIn(0f, 1f),
            )
        }
    }
}

private data class Particle(
    val angle: Float,
    val speed: Float,
    val size: Float,
    val tilt: Float,
)

private const val PARTICLE_COUNT = 26
