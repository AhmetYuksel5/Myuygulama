package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.Handler
import android.os.Looper
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

    /**
     * Jest onaylandığında hafif, kısa bir "bip" sesi. Ayardan kapalıysa sessiz.
     * Düşük ses seviyeli, tek atımlık bir ton; sistem ses seviyesinden bağımsız
     * olması için kendi düşük seviyesini kullanır.
     */
    fun beep(settings: GestureSettings) {
        if (!settings.soundEnabled) return
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_MUSIC, BEEP_VOLUME)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
            // Ton bitince kaynağı serbest bırak.
            Handler(Looper.getMainLooper()).postDelayed(
                { runCatching { tone.release() } },
                BEEP_DURATION_MS + 120L,
            )
        }
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

    private const val BEEP_VOLUME = 55 // 0–100; "ufak bir bip"
    private const val BEEP_DURATION_MS = 90
}
