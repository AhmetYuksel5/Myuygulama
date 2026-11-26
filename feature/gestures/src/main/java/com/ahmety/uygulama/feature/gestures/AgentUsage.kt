package com.ahmety.uygulama.feature.gestures

import android.content.Context
import org.json.JSONObject

/**
 * Ekran ajanının harcama sayacı.
 *
 * Claude kredisi sınırlı; ne kadarının gittiği tahminle değil, sağlayıcının
 * her cevapta bildirdiği token sayısından hesaplanıyor. Sayaç Yapay zekâ
 * ayarlarında model başına gösteriliyor (core:ai aynı tercih dosyasını
 * okuyor). Dolar karşılığı yaklaşık: liste fiyatıyla hesaplanıyor.
 */
internal object AgentUsage {

    private const val PREFS = "merkez_yapay_zeka"

    /** 1 milyon token başına dolar: girdi, çıktı, önbellekten okuma, önbelleğe yazma. */
    private class Price(val input: Double, val output: Double, val cacheRead: Double, val cacheWrite: Double)

    private val HAIKU = Price(0.10, 0.50, 0.01, 0.125)
    private val SONNET = Price(2.0, 10.0, 0.10, 2.5)
    private val OPUS = Price(4.0, 20.0, 0.20, 5.0)

    // Bilinmeyen Claude modelinde en pahalısı varsayılıyor: sayaç az göstermesin.
    private fun priceOf(model: String): Price = when {
        model.contains("haiku") -> HAIKU
        model.contains("sonnet") -> SONNET
        else -> OPUS
    }

    /** Düşünme seviyesi (effort) alanını tanıyan modeller; eskileri 400 veriyor. */
    fun supportsEffort(model: String): Boolean =
        Regex("claude-(opus|sonnet|haiku|fable|mythos)-(5|4-6|4-7|4-8)").containsMatchIn(model)

    fun label(model: String): String = when {
        model.contains("haiku") -> "Haiku"
        model.contains("sonnet") -> "Sonnet"
        model.contains("opus") -> "Opus"
        model.contains("gemini") -> "Gemini"
        else -> "Claude"
    }

    fun recordClaude(context: Context, session: AgentSession, model: String, body: String) = runCatching {
        val usage = JSONObject(body).optJSONObject("usage") ?: return@runCatching
        val input = usage.optLong("input_tokens")
        val cacheRead = usage.optLong("cache_read_input_tokens")
        val cacheWrite = usage.optLong("cache_creation_input_tokens")
        val output = usage.optLong("output_tokens")
        val p = priceOf(model)
        val usd = (input * p.input + cacheRead * p.cacheRead + cacheWrite * p.cacheWrite + output * p.output) / 1_000_000.0
        session.lastCall = "${input + cacheRead + cacheWrite}+$output tok" +
            (if (cacheRead > 0) " (önbellek $cacheRead)" else "") +
            " ~" + String.format(java.util.Locale.ROOT, "%.4f", usd) + " $"
        add(context, session, label(model), input + cacheRead + cacheWrite, output, usd)
    }

    fun recordGemini(context: Context, session: AgentSession, model: String, body: String) = runCatching {
        val meta = JSONObject(body).optJSONObject("usageMetadata") ?: return@runCatching
        val input = meta.optLong("promptTokenCount")
        val output = meta.optLong("candidatesTokenCount") + meta.optLong("thoughtsTokenCount")
        // Gemini fiyatı burada tutulmuyor: yalnız token sayılıyor.
        session.lastCall = "$input+$output tok"
        add(context, session, label(model), input, output, 0.0)
    }

    private fun add(context: Context, session: AgentSession, label: String, input: Long, output: Long, usd: Double) {
        synchronized(this) {
            session.modelSteps[label] = (session.modelSteps[label] ?: 0) + 1
            session.costUsd += usd
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val key = "usage.$label"
            prefs.edit()
                .putLong("$key.steps", prefs.getLong("$key.steps", 0) + 1)
                .putLong("$key.in", prefs.getLong("$key.in", 0) + input)
                .putLong("$key.out", prefs.getLong("$key.out", 0) + output)
                .putLong("$key.usd_micros", prefs.getLong("$key.usd_micros", 0) + (usd * 1_000_000).toLong())
                .apply {
                    if (!prefs.contains("usage.since")) putLong("usage.since", System.currentTimeMillis())
                }
                .apply()
        }
    }

    /** Kartta görünen tek satır: "Haiku 9 adım · Opus 2 adım · ~0,05 $". */
    fun summary(session: AgentSession): String {
        if (session.modelSteps.isEmpty()) return ""
        val steps = session.modelSteps.entries.joinToString(" · ") { "${it.key} ${it.value} adım" }
        return if (session.costUsd > 0) {
            steps + " · ~" + String.format(java.util.Locale.forLanguageTag("tr"), "%.3f", session.costUsd) + " $"
        } else {
            steps
        }
    }
}
