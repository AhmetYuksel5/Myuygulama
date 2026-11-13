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
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.text.InputType
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

    private var recorder: LiveRecorder? = null
    private var startedAt = 0L
    private val bubble = PreviewBubble(service)

    private val refresh = Runnable { update() }

    // Renk değişince düğme yeniden kuruluyor; çizim rengi kurulurken alınıyor.
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == KEY_COLOR && button?.state == MicButton.IDLE) {
            hide()
            handler.post(refresh)
        }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(prefsListener)
    }

    /** Klavye ya da odak değişti; düğmenin yeri yeniden hesaplanıyor. */
    fun onEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOWS_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_VIEW_FOCUSED,
            -> {
                // Hemen bakılıyor: önceden haberlerin durulması beklenip
                // 150 ms sonra bakılıyordu, mikrofon klavyeden sonra gelip
                // klavye kapandıktan sonra gidiyordu. Klavye açılırken
                // haberler art arda geldiği için yer bir kez de durulunca
                // düzeltiliyor.
                update()
                handler.removeCallbacks(refresh)
                handler.postDelayed(refresh, SETTLE_MS)
            }
        }
    }

    fun release() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
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
        if (!enabled() || !findKeyboard() || !speakable()) {
            hide()
            return
        }
        show()
    }

    /**
     * Odaktaki kutuya konuşarak yazılır mı. Rakam, telefon, tarih ve şifre
     * kutularında mikrofonun işi yok; klavyenin sayı tuşlarının üstünde
     * yalnız yer kaplıyordu.
     */
    private fun speakable(): Boolean {
        val node = runCatching { service.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) }.getOrNull()
            ?: return true
        if (node.isPassword) return false
        val type = node.inputType
        return when (type and InputType.TYPE_MASK_CLASS) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME,
            -> false
            else -> true
        }
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
        val view = MicButton(service, buttonColor(prefs))
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
            // Klavye aşağıdan kayarak geliyor; mikrofon da yumuşakça
            // belirsin, klavyeden önce sert bir şekilde ortaya çıkmasın.
            view.alpha = 0f
            view.animate().alpha(1f).setDuration(FADE_MS).start()
        }
    }

    private fun hide() {
        bubble.hide()
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
        // Sesin gelip gelmediğine de bakılıyor: mikrofon sistemce
        // engellendiyse Android hata vermiyor, sessizlik kaydediyor.
        val rec = LiveRecorder(
            http = http,
            key = apiKey(),
            preview = prefs.getBoolean(KEY_PREVIEW, true),
            onLevel = { level -> handler.post { button?.level = level } },
            onText = { text -> handler.post { if (recorder != null) bubble.show(text, anchor()) } },
        )
        if (!rec.start()) {
            VoiceRecordService.stop(service)
            toast("Mikrofon açılamadı; başka bir uygulama kullanıyor olabilir.")
            return
        }
        recorder = rec
        startedAt = SystemClock.uptimeMillis()
        button?.state = MicButton.RECORDING
        if (prefs.getBoolean(KEY_PREVIEW, true)) bubble.show("", anchor())
        GestureFeedback.vibrateOnce(service, 15L)
    }

    /** Önizleme balonunun dayanacağı yer: mikrofonun sol üst köşesi ve boyu. */
    private fun anchor(): Rect {
        val lp = params ?: return Rect()
        val size = (SIZE_DP * density).toInt()
        return Rect(lp.x, lp.y, lp.x + size, lp.y + size)
    }

    private fun cancelRecording() {
        recorder?.cancel()
        recorder = null
        VoiceRecordService.stop(service)
        bubble.hide()
        button?.state = MicButton.IDLE
    }

    private fun stopAndTranscribe() {
        val rec = recorder ?: return
        recorder = null
        val duration = SystemClock.uptimeMillis() - startedAt
        val file = File(service.cacheDir, "sesli_yazma.wav")
        val saved = rec.stop(file)
        VoiceRecordService.stop(service)
        GestureFeedback.vibrateOnce(service, 15L)
        when {
            !saved || duration < MIN_MS -> {
                button?.state = MicButton.IDLE
                bubble.hide()
                toast("Kayıt çok kısa.")
                return
            }
            rec.loudest < SILENCE -> {
                button?.state = MicButton.IDLE
                bubble.hide()
                toast("Ses gelmedi. Mikrofon engellenmiş olabilir.")
                return
            }
        }
        button?.state = MicButton.BUSY
        bubble.busy()
        val key = apiKey()
        Thread {
            val result = runCatching { transcribe(file, key) }
            file.delete()
            handler.post {
                button?.state = MicButton.IDLE
                bubble.hide()
                result
                    .onSuccess { text -> if (text.isNotBlank()) insert(text.trim()) else toast("Konuşma anlaşılamadı.") }
                    .onFailure { toast(it.message ?: "Yazıya çevrilemedi.") }
                handler.post(refresh)
            }
        }.start()
    }

    /**
     * Yeni model reddederse eski modelle bir kez daha dener: `gpt-transcribe`
     * telefonda denenmeden geliyor, eski model 26 Şubat 2027'ye kadar açık.
     */
    private fun transcribe(file: File, key: String): String =
        runCatching { transcribe(file, key, MODEL) }.getOrElse { first ->
            if (first is ModelRejected) transcribe(file, key, OLD_MODEL) else throw first
        }

    private class ModelRejected(message: String) : IllegalStateException(message)

    private fun transcribe(file: File, key: String, model: String): String {
        val body = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("model", model)
            .addFormDataPart("file", file.name, file.asRequestBody("audio/wav".toMediaType()))
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
                if (response.code in setOf(400, 404, 422)) throw ModelRejected("OpenAI: $reason")
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
        // Boş kutunun soluk ipucu yazısı ("Mesaj") bazı uygulamalarda metin
        // olarak geliyor ve "ipucu gösteriliyor" işareti konmuyor. Önceden
        // yalnız işarete bakılıyordu; ipucu konuşulanın başına ekleniyordu.
        val raw = node.text?.toString().orEmpty()
        val hint = node.hintText?.toString().orEmpty()
        val current = if (node.isShowingHintText || (hint.isNotEmpty() && raw == hint)) "" else raw
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
        const val KEY_PREVIEW = "preview"
        const val KEY_COLOR = "color"

        /**
         * Düğme renkleri. İlki eski koyu gri; turuncu, sohbet uygulamalarının
         * gönder düğmesine uysun diye istendi.
         */
        val COLORS = listOf(
            0xFF2B2F36.toInt(),
            0xFFC96442.toInt(),
            0xFF2F6FD6.toInt(),
            0xFF2E9E6A.toInt(),
            0xFF7A5AC8.toInt(),
            0xFF111111.toInt(),
        )

        fun buttonColor(prefs: android.content.SharedPreferences): Int =
            prefs.getInt(KEY_COLOR, COLORS.first())
        private const val KEY_RIGHT = "right_dp"
        private const val KEY_UP = "up_dp"

        // Anahtar, yapay zekâ ayarlarının durduğu dosyadan okunuyor; bu
        // modül o modüle bağlı değil, anahtarı ikinci bir yere yazmamak için.
        private const val AI_PREFS = "merkez_yapay_zeka"
        private const val AI_KEY = "openai_key"

        // Whisper'dan iyi: Türkçe noktalamayı ve karışık dilleri daha doğru
        // yazıyor. Önceki `gpt-4o-transcribe` 26 Şubat 2027'de kapanıyor;
        // yerine geçen bu, dakikası da daha ucuz.
        private const val MODEL = "gpt-transcribe"
        private const val OLD_MODEL = "gpt-4o-transcribe"

        private const val SIZE_DP = 44f
        private const val DEFAULT_RIGHT_DP = 8f
        private const val DEFAULT_UP_DP = 6f
        private const val DRAG_DP = 10f
        private const val SETTLE_MS = 150L
        private const val FADE_MS = 180L
        private const val LONG_MS = 600L
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
internal class MicButton(context: Context, private val idle: Int) : View(context) {

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
            else -> Color.argb(235, Color.red(idle), Color.green(idle), Color.blue(idle))
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

/**
 * Konuşurken anlaşılan yazının göründüğü balon, mikrofonun hemen üstünde.
 *
 * Yalnız gösterim: dokunuş almıyor, arkadaki uygulamaya geçiyor. Uzun
 * konuşmada son birkaç satır görünüyor; baş tarafı zaten söylenmiş oluyor.
 */
internal class PreviewBubble(private val service: AccessibilityService) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = service.resources.displayMetrics.density
    private var view: android.widget.TextView? = null
    private var params: WindowManager.LayoutParams? = null
    private var anchor = Rect()

    fun show(text: String, at: Rect) {
        anchor = at
        val shown = view ?: create() ?: return
        shown.text = text.ifBlank { "Dinliyor…" }.takeLast(MAX_CHARS)
        shown.alpha = if (text.isBlank()) 0.7f else 1f
        place(shown)
    }

    fun busy() {
        val shown = view ?: return
        val current = shown.text.toString().takeIf { it != "Dinliyor…" }.orEmpty()
        shown.text = if (current.isBlank()) "Yazıya çevriliyor…" else current
        shown.alpha = 0.7f
    }

    fun hide() {
        view?.let { runCatching { windowManager.removeView(it) } }
        view = null
        params = null
    }

    private fun create(): android.widget.TextView? {
        val text = android.widget.TextView(service).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 5
            ellipsize = android.text.TextUtils.TruncateAt.START
            val p = (12 * density).toInt()
            setPadding(p, (9 * density).toInt(), p, (9 * density).toInt())
            background = android.graphics.drawable.GradientDrawable().apply {
                cornerRadius = 16f * density
                setColor(Color.argb(235, 0x24, 0x27, 0x2E))
            }
        }
        val lp = WindowManager.LayoutParams(
            (WIDTH_DP * density).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = Gravity.TOP or Gravity.START }
        return runCatching {
            windowManager.addView(text, lp)
            view = text
            params = lp
            text
        }.getOrNull()
    }

    /** Balonun alt kenarı mikrofonun üstünde, sağ kenarı mikrofonla hizalı. */
    private fun place(shown: android.widget.TextView) {
        val lp = params ?: return
        val width = lp.width
        shown.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val screen = service.resources.displayMetrics.widthPixels
        lp.x = (anchor.right - width).coerceIn(0, (screen - width).coerceAtLeast(0))
        lp.y = (anchor.top - shown.measuredHeight - (8 * density).toInt()).coerceAtLeast(0)
        runCatching { windowManager.updateViewLayout(shown, lp) }
    }

    private companion object {
        const val WIDTH_DP = 280f
        const val MAX_CHARS = 400
    }
}
