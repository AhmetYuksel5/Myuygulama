package com.ahmety.uygulama.feature.gestures

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Ekran ajanının çalışma günlüğü.
 *
 * Ajan bir yerde takıldığında ne olduğunu tahminle değil kayıttan konuşmak
 * için: her adımda model, uygulama, okunan öğe sayısı, görüntü gidip
 * gitmediği, süre, token ve maliyet, düşünce, hareket ve sonuç. Ekranın tam
 * metni yazılmıyor; banka ekranında ajan zaten durduğu için o ekranlar da
 * girmiyor. Son turlar telefonda bir dosyada duruyor; Yapay zekâ
 * ayarlarından kopyalanabiliyor.
 */
object AgentLog {

    const val FILE = "ajan_gunlugu.txt"
    private const val MAX_LINES = 300
    private const val MAX_FILE_CHARS = 60_000

    internal fun clock(): String = SimpleDateFormat("d MMM HH:mm:ss", Locale.forLanguageTag("tr")).format(Date())

    internal fun add(session: AgentSession, line: String) {
        synchronized(session.log) {
            session.log += line
            while (session.log.size > MAX_LINES) {
                session.log.removeAt(0)
                session.loggedUpTo = (session.loggedUpTo - 1).coerceAtLeast(0)
            }
        }
    }

    internal fun text(session: AgentSession): String = synchronized(session.log) {
        session.log.joinToString("\n")
    }

    /** Henüz yazılmamış satırları dosyaya ekler; dosya büyüyünce baştan kırpılıyor. */
    internal fun persist(context: Context, session: AgentSession) = runCatching {
        val fresh = synchronized(session.log) {
            session.log.drop(session.loggedUpTo).also { session.loggedUpTo = session.log.size }
        }
        if (fresh.isEmpty()) return@runCatching
        val chunk = fresh.joinToString("\n")
        val file = File(context.filesDir, FILE)
        val old = if (file.exists()) file.readText() else ""
        val combined = old + chunk + "\n"
        file.writeText(if (combined.length > MAX_FILE_CHARS) combined.takeLast(MAX_FILE_CHARS) else combined)
        // Drive köprüsü açıksa aynı log parçası arka planda MerkezAjan/Merkez_Ajan_Log.md'ye de gider.
        ScreenAgentStorage.uploadLogChunk(context, chunk)
    }

    /** Telefonda saklanan son turlar; yoksa boş. */
    fun saved(context: Context): String =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull().orEmpty()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
