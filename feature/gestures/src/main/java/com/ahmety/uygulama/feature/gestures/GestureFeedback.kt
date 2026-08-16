package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Jest algılandığında verilen dokunsal geri bildirim.
 *
 * Güç ayarı hem genliği hem süreyi değiştiriyor; genlik kontrolü olmayan
 * motorlarda (eski/ucuz cihazlar) genlik yok sayılır, süre farkı yine hissedilir.
 */
object GestureFeedback {

    fun vibrate(context: Context, settings: GestureSettings) {
        if (!settings.vibrateEnabled) return

        val vibrator = resolveVibrator(context) ?: return
        val (durationMs, amplitude) = when (settings.vibrateStrength) {
            0 -> 12L to 70
            2 -> 45L to 255
            else -> 25L to 150
        }

        val effect = if (vibrator.hasAmplitudeControl()) {
            VibrationEffect.createOneShot(durationMs, amplitude)
        } else {
            VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        }
        runCatching { vibrator.vibrate(effect) }
    }

    /** Ayardan bağımsız kısa titreşim (Quick Cursor gibi kendi geri bildirimi olanlar için). */
    fun vibrateOnce(context: Context, durationMs: Long) {
        val vibrator = resolveVibrator(context) ?: return
        val effect = VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE)
        runCatching { vibrator.vibrate(effect) }
    }

    private fun resolveVibrator(context: Context): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }?.takeIf { it.hasVibrator() }
}
