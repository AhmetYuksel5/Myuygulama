package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
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

    /**
     * Jest onaylandığında hafif, kısa bir "bip" sesi. Ayardan kapalıysa sessiz.
     *
     * Tek bir paylaşılan [ToneGenerator] kullanıyoruz: her jestte yenisini
     * yaratıp bırakmak, `startTone` istisna attığında native kaynağı kalıcı
     * sızdırıyordu. Ses akışı olarak müzik değil sistem akışı seçildi; aksi
     * hâlde kulaklıkla müzik dinlerken her jestte müziğin sesi kısılıyordu.
     */
    @Volatile
    private var tone: ToneGenerator? = null

    fun beep(settings: GestureSettings) {
        if (!settings.soundEnabled) return
        val generator = tone ?: runCatching {
            ToneGenerator(AudioManager.STREAM_SYSTEM, BEEP_VOLUME)
        }.getOrNull()?.also { tone = it } ?: return

        val ok = runCatching {
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, BEEP_DURATION_MS)
        }.isSuccess
        if (!ok) releaseTone()
    }

    /** Servis kapanırken native kaynağı bırak. */
    fun releaseTone() {
        val current = tone ?: return
        tone = null
        runCatching { current.release() }
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

    private const val BEEP_VOLUME = 25 // 0–100; "ufak bir bip"
    private const val BEEP_DURATION_MS = 90
}
