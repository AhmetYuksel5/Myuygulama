package com.ahmety.uygulama.feature.gestures

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.abs

/**
 * Sesli yazmanın kaydı: ham ses hem saklanıyor hem anlık önizleme için
 * OpenAI'ye akıtılıyor.
 *
 * Önceden ses doğrudan sıkıştırılmış dosyaya yazılıyordu; konuşurken ne
 * anlaşıldığı görünmüyordu. Şimdi ses parça parça okunuyor: her parça
 * belleğe ekleniyor ve açıksa canlı yazıya çevirme bağlantısına da
 * gönderiliyor.
 *
 * Kutuya yazılan metin yine kayıt bitince bütün sesin bir kerede yazıya
 * çevrilmesinden geliyor. Canlı model hız için yapılmış, bütün kayda
 * bakan model daha isabetli; önizleme bozulsa bile sonuç etkilenmiyor.
 */
internal class LiveRecorder(
    private val http: OkHttpClient,
    private val key: String,
    private val preview: Boolean,
    /** Ses seviyesi, 0–1; arka plan iş parçacığından çağrılıyor. */
    private val onLevel: (Float) -> Unit,
    /** O ana kadar anlaşılan yazı; OkHttp'nin iş parçacığından çağrılıyor. */
    private val onText: (String) -> Unit,
) {
    @Volatile
    private var running = false
    private var thread: Thread? = null
    private val pcm = Pcm()

    @Volatile
    var loudest = 0
        private set

    @Volatile
    private var socket: WebSocket? = null

    /**
     * Oturum ayarı gönderildi mi. Ondan önce giden ses, sunucunun
     * varsayılan ayarlarıyla karşılanıp atılıyordu: bağlantı kurulurken
     * söylenen ilk kelime önizlemede hiç görünmüyordu. Ses o zamana kadar
     * bekletiliyor, ayar gidince baştan gönderiliyor.
     */
    @Volatile
    private var ready = false

    /** Önizlemeye gönderilmiş ses, bayt. */
    private var sent = 0

    // Konuşma parçaları gelen sırayla; aynı parçanın yazısı büyüyerek geliyor.
    private val items = LinkedHashMap<String, String>()

    @SuppressLint("MissingPermission") // izin çağıran tarafta denetleniyor
    fun start(): Boolean {
        val min = AudioRecord.getMinBufferSize(RATE, CHANNEL, ENCODING)
        val record = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                RATE,
                CHANNEL,
                ENCODING,
                maxOf(min, CHUNK * 4),
            )
        }.getOrNull() ?: return false
        if (record.state != AudioRecord.STATE_INITIALIZED ||
            runCatching { record.startRecording() }.isFailure
        ) {
            record.release()
            return false
        }
        if (preview) openSocket()
        running = true
        thread = Thread({ loop(record) }, "sesli-yazma").apply { start() }
        return true
    }

    private fun loop(record: AudioRecord) {
        val buffer = ByteArray(CHUNK)
        while (running) {
            val read = record.read(buffer, 0, buffer.size)
            if (read <= 0) continue
            synchronized(pcm) { pcm.write(buffer, 0, read) }
            var peak = 0
            var i = 0
            while (i + 1 < read) {
                val sample = ((buffer[i].toInt() and 0xFF) or (buffer[i + 1].toInt() shl 8)).toShort()
                val level = abs(sample.toInt())
                if (level > peak) peak = level
                i += 2
            }
            if (peak > loudest) loudest = peak
            onLevel((peak / 12000f).coerceIn(0f, 1f))
            flush()
        }
        runCatching { record.stop() }
        record.release()
    }

    /** Önizlemeye henüz gitmemiş sesi gönderir; oturum hazır değilse bekletir. */
    private fun flush() {
        val target = socket ?: return
        if (!ready) return
        val pending = synchronized(pcm) {
            if (pcm.size() <= sent) return
            pcm.from(sent).also { sent = pcm.size() }
        }
        // Büyük birikimi (bağlantı beklenirken toplanan) parçalayarak yolla.
        var offset = 0
        while (offset < pending.size) {
            val size = minOf(CHUNK * 10, pending.size - offset)
            target.send(
                JSONObject()
                    .put("type", "input_audio_buffer.append")
                    .put("audio", Base64.encodeToString(pending, offset, size, Base64.NO_WRAP))
                    .toString(),
            )
            offset += size
        }
    }

    /** Kaydı bitirir; ses WAV olarak [file]'a yazılır. Ses yoksa false. */
    fun stop(file: File): Boolean {
        running = false
        thread?.join(JOIN_MS)
        closeSocket()
        val bytes = synchronized(pcm) { pcm.toByteArray() }
        if (bytes.isEmpty()) return false
        return runCatching { writeWav(file, bytes) }.isSuccess
    }

    fun cancel() {
        running = false
        thread?.join(JOIN_MS)
        closeSocket()
    }

    private fun openSocket() {
        val request = Request.Builder()
            .url("wss://api.openai.com/v1/realtime?intent=transcription")
            .header("Authorization", "Bearer $key")
            .build()
        socket = http.newWebSocket(
            request,
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send(sessionUpdate())
                    // Mesajlar sırayla gidiyor: bundan sonraki ses ayarı
                    // görmüş bir oturuma varıyor.
                    ready = true
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    val event = runCatching { JSONObject(text) }.getOrNull() ?: return
                    val id = event.optString("item_id")
                    when (event.optString("type")) {
                        "conversation.item.input_audio_transcription.delta" -> {
                            items[id] = items[id].orEmpty() + event.optString("delta")
                        }

                        "conversation.item.input_audio_transcription.completed" -> {
                            items[id] = event.optString("transcript")
                        }

                        else -> return
                    }
                    onText(items.values.joinToString(" ").trim())
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    // Önizleme olmadan da kayıt sürüyor; sonuç etkilenmiyor.
                    socket = null
                }
            },
        )
    }

    private fun closeSocket() {
        socket?.close(1000, null)
        socket = null
    }

    private fun sessionUpdate(): String = JSONObject()
        .put("type", "session.update")
        .put(
            "session",
            JSONObject()
                .put("type", "transcription")
                .put(
                    "audio",
                    JSONObject().put(
                        "input",
                        JSONObject()
                            .put("format", JSONObject().put("type", "audio/pcm").put("rate", RATE))
                            .put(
                                "transcription",
                                JSONObject()
                                    .put("model", LIVE_MODEL)
                                    .put("languages", JSONArray().put("tr").put("en").put("ar")),
                            )
                            .put("turn_detection", JSONObject.NULL),
                    ),
                ),
        )
        .toString()

    private fun writeWav(file: File, data: ByteArray) {
        RandomAccessFile(file, "rw").use { out ->
            out.setLength(0)
            fun intLe(v: Int) {
                out.write(v and 0xFF); out.write(v shr 8 and 0xFF)
                out.write(v shr 16 and 0xFF); out.write(v shr 24 and 0xFF)
            }
            fun shortLe(v: Int) {
                out.write(v and 0xFF); out.write(v shr 8 and 0xFF)
            }
            out.writeBytes("RIFF"); intLe(36 + data.size); out.writeBytes("WAVE")
            out.writeBytes("fmt "); intLe(16); shortLe(1); shortLe(1)
            intLe(RATE); intLe(RATE * 2); shortLe(2); shortLe(16)
            out.writeBytes("data"); intLe(data.size)
            out.write(data)
        }
    }

    /** Yalnız yeni kısmı kopyalayabilen bellek; her parçada bütün kaydı kopyalamamak için. */
    private class Pcm : ByteArrayOutputStream() {
        fun from(start: Int): ByteArray = buf.copyOfRange(start, count)
    }

    private companion object {
        // Canlı yazıya çevirme 24 kHz istiyor; dosya için de aynısı yetiyor.
        const val RATE = 24_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 100 ms'lik parça: seviye göstergesi ve önizleme bu sıklıkta tazeleniyor. */
        const val CHUNK = RATE * 2 / 10
        const val JOIN_MS = 1_000L

        const val LIVE_MODEL = "gpt-live-transcribe"
    }
}
