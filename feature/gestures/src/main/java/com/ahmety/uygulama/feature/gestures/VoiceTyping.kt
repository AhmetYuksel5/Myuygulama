package com.ahmety.uygulama.feature.gestures

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.Toast
import androidx.core.content.ContextCompat
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.math.hypot

/**
 * Klavyenin üstünde yüzen mikrofon: konuşulanı OpenAI ile yazıya çevirip
 * o an yazılan kutuya ekler.
 *
 * Gboard'un içine bir şey eklemenin yolu yok; başka bir klavyeye geçip
 * dönmek de her seferinde iki dokunuş. Bunun yerine klavye açıldığında
 * erişilebilirlik servisi onun üst kenarını buluyor ve düğmeyi oraya
 * koyuyor — Gboard yerinde kalıyor.
 *
 * Düğme sürüklenerek kaydırılabiliyor; yeri klavyenin sağ üst köşesine
 * göre saklanıyor. Uygulamaların kendi alt çubuğu (WhatsApp'ın gönder
 * düğmesi gibi) tam o köşede olabiliyor, sabit bir yer bir yerde
 * mutlaka bir şeyin üstüne düşerdi.
 */
class VoiceTyping internal constructor(private val service: AccessibilityService) {

    private val handler = Handler(Looper.getMainLooper())
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private var button: MicButton? = null
    private var params: WindowManager.LayoutParams? = null
    private val keyboard = Rect()

    private var recorder: MediaRecorder? = null
    private var audio: File? = null
    private var startedAt = 0L
    private var loudest = 0

    private val refresh = Runnable { update() }

    // Kayıt sürerken sesin gelip gelmediğine bakılıyor: mikrofon sistemce
    // engellendiyse Android hata vermiyor, sessizlik kaydediyor.
    private val meter = object : Runnable {
        override fun run() {
            val rec = recorder ?: return
            val level = runCatching { rec.maxAmplitude }.getOrDefault(0)
            if (level > loudest) loudest = level
            button?.level = (level / 12000f).coerceIn(0f, 1f)
            handler.postDelayed(this, METER_MS)
        }
    }

    /** Klavye ya da odak değişti; düğmenin yeri yeniden hesaplanıyor. */
    fun onEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            -> {
                // Klavye açılırken pencere haberleri art arda geliyor;
                // en sonuncusundan sonra bir kez bakmak yetiyor.
                handler.removeCallbacks(refresh)
                handler.postDelayed(refresh, SETTLE_MS)
            }
        }
    }

    fun release() {
        handler.removeCallbacksAndMessages(null)
        cancelRecording()
        hide()
    }

    private fun enabled() = prefs.getBoolean(KEY_ENABLED, true)

    private fun update() {
        val state = button?.state ?: MicButton.IDLE
        // Kayıt ya da çeviri sürerken düğme yerinde kalıyor: klavye bir an
        // kapanıp açılsa da iş yarıda kesilmesin.
        if (state != MicButton.IDLE) return
        if (!enabled() || !findKeyboard()) {
            hide()
            return
        }
        show()
    }

    private fun findKeyboard(): Boolean {
        val windows = runCatching { service.windows }.getOrNull() ?: return false
        val ime = windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            ?: return false
        ime.getBoundsInScreen(keyboard)
        return keyboard.height() > 0
    }

    private fun show() {
        val size = (SIZE_DP * density).toInt()
        val right = prefs.getFloat(KEY_RIGHT, DEFAULT_RIGHT_DP) * density
        val up = prefs.getFloat(KEY_UP, DEFAULT_UP_DP) * density
        val x = (keyboard.right - right - size).toInt().coerceAtLeast(0)
        val y = (keyboard.top - up - size).toInt().coerceAtLeast(0)

        val shown = button
        val lp = params
        if (shown != null && lp != null) {
            if (lp.x != x || lp.y != y) {
                lp.x = x
                lp.y = y
                runCatching { windowManager.updateViewLayout(shown, lp) }
            }
            return
        }
        val view = MicButton(service)
        val newParams = WindowManager.LayoutParams(
            size,
            size,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        view.setOnTouchListener(Touch(newParams))
        runCatching { windowManager.addView(view, newParams) }.onSuccess {
            button = view
            params = newParams
        }
    }

    private fun hide() {
        button?.let { view -> runCatching { windowManager.removeView(view) } }
        button = null
        params = null
    }

    /**
     * Dokunuş: kaydı başlatır ya da bitirir. Sürükleme: düğmeyi taşır.
     * Kayıt sürerken uzun basma: kaydı çöpe atar.
     */
    private inner class Touch(private val lp: WindowManager.LayoutParams) : View.OnTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragging = false
        private var longFired = false
        private val longPress = Runnable {
            if (button?.state == MicButton.RECORDING) {
                longFired = true
                cancelRecording()
                GestureFeedback.vibrateOnce(service, 30L)
                toast("Kayıt iptal edildi.")
            }
        }

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = event.rawX
                    downY = event.rawY
                    startX = lp.x
                    startY = lp.y
                    dragging = false
                    longFired = false
                    handler.postDelayed(longPress, LONG_MS)
                }

                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && hypot(dx, dy) > DRAG_DP * density &&
                        button?.state == MicButton.IDLE
                    ) {
                        dragging = true
                        handler.removeCallbacks(longPress)
                    }
                    if (dragging) {
                        lp.x = (startX + dx).toInt().coerceAtLeast(0)
                        lp.y = (startY + dy).toInt().coerceAtLeast(0)
                        runCatching { windowManager.updateViewLayout(v, lp) }
                    }
                }

                MotionEvent.ACTION_UP -> {
                    handler.removeCallbacks(longPress)
                    if (dragging) {
                        rememberPlace(lp)
                    } else if (!longFired) {
                        tap()
                    }
                }

                MotionEvent.ACTION_CANCEL -> handler.removeCallbacks(longPress)
            }
            return true
        }
    }

    private fun rememberPlace(lp: WindowManager.LayoutParams) {
        val size = SIZE_DP * density
        prefs.edit()
            .putFloat(KEY_RIGHT, ((keyboard.right - lp.x - size) / density).coerceAtLeast(0f))
            .putFloat(KEY_UP, ((keyboard.top - lp.y - size) / density))
            .apply()
    }

    private fun tap() {
        when (button?.state) {
            MicButton.IDLE -> startRecording()
            MicButton.RECORDING -> stopAndTranscribe()
            else -> Unit // çeviri sürüyor
        }
    }

    private fun startRecording() {
        if (ContextCompat.checkSelfPermission(service, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            toast("Mikrofon izni yok: Merkez → İzinler → Mikrofon.")
            return
        }
        if (apiKey().isBlank()) {
            toast("OpenAI anahtarı girilmemiş: Merkez → Yapay zekâ.")
            return
        }
        // Arka plandaki bir uygulamanın mikrofonu açık tutabilmesi için
        // bildirimli bir ön plan hizmeti gerekiyor; Android aksi hâlde
        // hata vermeden sessizlik kaydediyor.
        VoiceRecordService.start(service)
        val file = File(service.cacheDir, "sesli_yazma.m4a")
        val rec = newRecorder()
        val ok = runCatching {
            rec.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setAudioChannels(1)
            rec.setAudioSamplingRate(16_000)
            rec.setAudioEncodingBitRate(32_000)
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            rec.start()
        }.isSuccess
        if (!ok) {
            runCatching { rec.release() }
            VoiceRecordService.stop(service)
            toast("Mikrofon açılamadı; başka bir uygulama kullanıyor olabilir.")
            return
        }
        recorder = rec
        audio = file
        startedAt = SystemClock.uptimeMillis()
        loudest = 0
        button?.state = MicButton.RECORDING
        GestureFeedback.vibrateOnce(service, 15L)
        handler.post(meter)
    }

    @Suppress("DEPRECATION")
    private fun newRecorder(): MediaRecorder =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(service) else MediaRecorder()

    /** Kaydı durdurur; kayıt geçerliyse dosyayı, değilse null döner. */
    private fun finishRecording(): File? {
        handler.removeCallbacks(meter)
        val rec = recorder ?: return null
        recorder = null
        val stopped = runCatching { rec.stop() }.isSuccess
        runCatching { rec.release() }
        VoiceRecordService.stop(service)
        return if (stopped) audio else null
    }

    private fun cancelRecording() {
        finishRecording()
        audio?.delete()
        button?.state = MicButton.IDLE
    }

    private fun stopAndTranscribe() {
        val duration = SystemClock.uptimeMillis() - startedAt
        val heard = loudest
        val file = finishRecording()
        GestureFeedback.vibrateOnce(service, 15L)
        when {
            file == null || duration < MIN_MS -> {
                button?.state = MicButton.IDLE
                toast("Kayıt çok kısa.")
                return
            }
            heard < SILENCE -> {
                button?.state = MicButton.IDLE
                toast("Ses gelmedi. Mikrofon engellenmiş olabilir.")
                return
            }
        }
        button?.state = MicButton.BUSY
        val key = apiKey()
        Thread {
            val result = runCatching { transcribe(file!!, key) }
            file!!.delete()
            handler.post {
                button?.state = MicButton.IDLE
                result
                    .onSuccess { text -> if (text.isNotBlank()) insert(text.trim()) else toast("Konuşma anlaşılamadı.") }
                    .onFailure { toast(it.message ?: "Yazıya çevrilemedi.") }
                handler.post(refresh)
            }
        }.start()
    }

    private fun transcribe(file: File, key: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", MODEL)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/mp4".toMediaType()))
            .build()
        val request = Request.Builder()
            .url("https://api.openai.com/v1/audio/transcriptions")
            .header("Authorization", "Bearer $key")
            .post(body)
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                // Anahtar hata mesajına konmuyor; yalnız sunucunun açıklaması.
                val reason = runCatching {
                    JSONObject(text).getJSONObject("error").getString("message")
                }.getOrDefault("HTTP ${response.code}")
                throw IllegalStateException("OpenAI: $reason")
            }
            return JSONObject(text).optString("text")
        }
    }

    /**
     * Yazıyı imlecin olduğu yere ekler; seçili bir parça varsa onun yerine.
     *
     * Kutunun metni baştan kuruluyor, çünkü erişilebilirlikte "şuraya ekle"
     * diye bir komut yok. Bu olmazsa yapıştırmaya düşülüyor; o da olmazsa
     * yazı panoda kalıyor, kaybolmuyor.
     */
    private fun insert(spoken: String) {
        val node = runCatching { service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
        if (node == null || !node.isEditable) {
            copy(spoken)
            toast("Yazı kutusu bulunamadı; panoya kopyalandı.")
            return
        }
        val current = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        var start = node.textSelectionStart
        var end = node.textSelectionEnd
        if (start < 0 || start > current.length) start = current.length
        if (end < start || end > current.length) end = start
        val before = current.substring(0, start)
        val glue = if (before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val piece = glue + spoken
        val next = before + piece + current.substring(end)

        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, next)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            val caret = before.length + piece.length
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
                },
            )
            return
        }
        copy(piece)
        if (!node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
            toast("Kutuya yazılamadı; panoya kopyalandı.")
        }
    }

    private fun copy(text: String) {
        val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("Sesli yazma", text))
    }

    private fun apiKey(): String =
        service.getSharedPreferences(AI_PREFS, Context.MODE_PRIVATE)
            .getString(AI_KEY, null).orEmpty().trim()

    private fun toast(text: String) {
        Toast.makeText(service, text, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val PREFS = "merkez_sesli_yazma"
        const val KEY_ENABLED = "enabled"
        private const val KEY_RIGHT = "right_dp"
        private const val KEY_UP = "up_dp"

        // Anahtar, yapay zekâ ayarlarının durduğu dosyadan okunuyor; bu
        // modül o modüle bağlı değil, anahtarı ikinci bir yere yazmamak için.
        private const val AI_PREFS = "merkez_yapay_zeka"
        private const val AI_KEY = "openai_key"

        // Whisper'dan iyi: Türkçe noktalamayı ve karışık dilleri daha
        // doğru yazıyor.
        private const val MODEL = "gpt-4o-transcribe"

        private const val SIZE_DP = 44f
        private const val DEFAULT_RIGHT_DP = 8f
        private const val DEFAULT_UP_DP = 6f
        private const val DRAG_DP = 10f
        private const val SETTLE_MS = 150L
        private const val LONG_MS = 600L
        private const val METER_MS = 80L
        private const val MIN_MS = 500L

        /** Bunun altındaki en yüksek ses seviyesi "hiç ses yok" sayılıyor. */
        private const val SILENCE = 200

        private val http: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
        }
    }
}

/** Yuvarlak mikrofon düğmesi: boşta koyu, kayıtta kırmızı, çeviride dönen halka. */
internal class MicButton(context: Context) : View(context) {

    var state = IDLE
        set(value) {
            field = value
            level = 0f
            invalidate()
        }

    /** Kayıttaki ses seviyesi, 0–1; düğmenin etrafındaki halka büyüyor. */
    var level = 0f
        set(value) {
            field = value
            invalidate()
        }

    private val d = resources.displayMetrics.density
    private val disc = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeWidth = 2f * d
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val arc = RectF()

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = width / 2f - 2f * d
        disc.color = when (state) {
            RECORDING -> Color.rgb(0xD9, 0x3B, 0x3B)
            else -> Color.argb(215, 0x2B, 0x2F, 0x36)
        }
        // Kayıtta sesle büyüyen dış halka: konuşmanın duyulduğu görülsün.
        if (state == RECORDING && level > 0f) {
            disc.alpha = 90
            canvas.drawCircle(cx, cy, r * (0.8f + 0.2f * level) + 2f * d * level, disc)
            disc.alpha = 255
        }
        canvas.drawCircle(cx, cy, r * 0.88f, disc)

        if (state == BUSY) {
            val now = SystemClock.uptimeMillis()
            val sweep = (now % 900L) / 900f * 360f
            arc.set(cx - r * 0.45f, cy - r * 0.45f, cx + r * 0.45f, cy + r * 0.45f)
            canvas.drawArc(arc, sweep, 270f, false, ink)
            postInvalidateOnAnimation()
            return
        }
        if (state == RECORDING) {
            // Durdurmak için kare: "bas, bitsin" diye okunuyor.
            val s = 5.5f * d
            arc.set(cx - s, cy - s, cx + s, cy + s)
            canvas.drawRoundRect(arc, 2f * d, 2f * d, solid)
            return
        }
        // Mikrofon: kapsül, altında kucaklayan yay ve sap.
        val w = 3.2f * d
        arc.set(cx - w, cy - 8f * d, cx + w, cy + 2f * d)
        canvas.drawRoundRect(arc, w, w, solid)
        arc.set(cx - 6f * d, cy - 5f * d, cx + 6f * d, cy + 5f * d)
        canvas.drawArc(arc, 0f, 180f, false, ink)
        canvas.drawLine(cx, cy + 5f * d, cx, cy + 8.5f * d, ink)
    }

    companion object {
        const val IDLE = 0
        const val RECORDING = 1
        const val BUSY = 2
    }
}
