package com.ahmety.uygulama.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Ekran ajanının hangi sağlayıcıyla çalışacağı. */
enum class AgentProvider(val id: String, val label: String) {
    GEMINI("gemini", "Gemini Flash"),
    CLAUDE("claude", "Claude"),
    ;

    companion object {
        fun fromId(id: String?): AgentProvider =
            entries.firstOrNull { it.id == id } ?: GEMINI
    }
}

/**
 * Yapay zekâ anahtarları **yalnızca cihazda** durur.
 *
 * Depo herkese açık olduğu için anahtarlar kaynağa, ayarlara ya da derleme
 * betiklerine hiçbir şekilde yazılmıyor; kullanıcı uygulamadan giriyor ve
 * uygulamanın kendi özel alanında saklanıyor (başka uygulamalar okuyamaz).
 * Yedeklerde de taşınmasın diye `allowBackup=false`.
 */
@Singleton
class AiSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Ekran ajanının varsayılanı Opus'tan Haiku'ya geçti; önceden kaydedilmiş
        // Opus da bir kez çevriliyor (ajan aynı işi kendi tarafında da yapıyor,
        // hangisi önce açılırsa).
        if (!prefs.getBoolean(KEY_HAIKU_MIGRATED, false)) {
            val edit = prefs.edit().putBoolean(KEY_HAIKU_MIGRATED, true)
            if (prefs.getString(KEY_CLAUDE_MODEL, null)?.trim() == "claude-opus-5-5") {
                edit.putString(KEY_CLAUDE_MODEL, DEFAULT_CLAUDE_MODEL)
            }
            edit.apply()
        }
    }

    var apiKey: String
        get() = prefs.getString(KEY_API, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    /**
     * Kullanılacak model. Ucuz olanı çoğu kelime için yetiyor ama zor
     * cümlelerde büyük model gözle görülür biçimde daha iyi çözümlüyor;
     * hangisini kullanacağına sen karar ver.
     */
    var model: String
        get() {
            val saved = prefs.getString(KEY_MODEL, null)?.takeIf { it.isNotBlank() } ?: return DEFAULT_MODEL
            // Kapanan bir model seçiliyse yerine geçeni kullanılıyor; yoksa
            // kapanış günü bütün kartlar bir anda hata verirdi.
            return RETIRED[saved] ?: saved
        }
        set(value) = prefs.edit().putString(KEY_MODEL, value.trim()).apply()

    val configured: Boolean get() = apiKey.isNotBlank()

    /** Ekranda gösterirken anahtarın tamamını asla yazmıyoruz. */
    fun maskedKey(): String = mask(apiKey)

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    // ---- Ekran ajanı sağlayıcıları (Gemini / Claude) ----
    // Aynı SharedPreferences dosyasında tutuluyor çünkü feature/gestures modülü
    // Hilt ve core:ai bağımlılığı olmadan doğrudan bu dosyayı okuyor.

    var geminiApiKey: String
        get() = prefs.getString(KEY_GEMINI_API, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_GEMINI_API, value.trim()).apply()

    var geminiModel: String
        get() = prefs.getString(KEY_GEMINI_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_GEMINI_MODEL
        set(value) = prefs.edit().putString(KEY_GEMINI_MODEL, value.trim()).apply()

    val geminiConfigured: Boolean get() = geminiApiKey.isNotBlank()

    fun maskedGeminiKey(): String = mask(geminiApiKey)

    fun clearGemini() {
        prefs.edit().remove(KEY_GEMINI_API).apply()
    }

    var claudeApiKey: String
        get() = prefs.getString(KEY_CLAUDE_API, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_CLAUDE_API, value.trim()).apply()

    var claudeModel: String
        get() = prefs.getString(KEY_CLAUDE_MODEL, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_CLAUDE_MODEL
        set(value) = prefs.edit().putString(KEY_CLAUDE_MODEL, value.trim()).apply()

    val claudeConfigured: Boolean get() = claudeApiKey.isNotBlank()

    fun maskedClaudeKey(): String = mask(claudeApiKey)

    fun clearClaude() {
        prefs.edit().remove(KEY_CLAUDE_API).apply()
    }

    /** Ekran ajanında turun başında Opus adım planı yazsın mı; uygulayan seçili model. */
    var claudePlanner: Boolean
        get() = prefs.getBoolean(KEY_CLAUDE_PLANNER, true)
        set(value) = prefs.edit().putBoolean(KEY_CLAUDE_PLANNER, value).apply()

    /** Ekran ajanında Haiku takılınca Sonnet, o da takılınca Opus devralsın mı. */
    var claudeEscalate: Boolean
        get() = prefs.getBoolean(KEY_CLAUDE_ESCALATE, true)
        set(value) = prefs.edit().putBoolean(KEY_CLAUDE_ESCALATE, value).apply()

    /**
     * Ekran ajanının harcaması, model başına. Ajan her cevaptaki token
     * sayısını aynı dosyaya yazıyor (feature/gestures, AgentUsage).
     */
    fun agentUsage(): List<AgentUsageRow> = USAGE_LABELS.mapNotNull { label ->
        val key = "usage.$label"
        val steps = prefs.getLong("$key.steps", 0)
        if (steps == 0L) return@mapNotNull null
        AgentUsageRow(
            label = label,
            steps = steps,
            inputTokens = prefs.getLong("$key.in", 0),
            outputTokens = prefs.getLong("$key.out", 0),
            usd = prefs.getLong("$key.usd_micros", 0) / 1_000_000.0,
        )
    }

    fun agentUsageSince(): Long = prefs.getLong("usage.since", 0)

    fun resetAgentUsage() {
        val edit = prefs.edit()
        prefs.all.keys.filter { it.startsWith("usage.") }.forEach { edit.remove(it) }
        edit.apply()
    }

    var agentProvider: AgentProvider
        get() = AgentProvider.fromId(prefs.getString(KEY_AGENT_PROVIDER, null))
        set(value) = prefs.edit().putString(KEY_AGENT_PROVIDER, value.id).apply()

    private fun mask(key: String): String {
        if (key.isBlank()) return ""
        return if (key.length <= 10) "•".repeat(key.length) else key.take(6) + "…" + key.takeLast(4)
    }

    companion object {
        /**
         * OpenAI'nin ucuz metin modeli. Önceki varsayılan `gpt-4o-mini`
         * 14 Ocak 2027'de kapanıyor; bu hem daha ucuz hem onun yerine geçen.
         */
        const val DEFAULT_MODEL = "gpt-6-luna"

        const val DEFAULT_GEMINI_MODEL = "gemini-3.8-flash"
        // Ekran ajanı için Haiku: Opus'tan 40 kat ucuz ve düğme bulmak için
        // yeterli. Takılınca Opus birkaç adım devralıyor.
        const val DEFAULT_CLAUDE_MODEL = "claude-haiku-5-5"

        /** Kapanan model → yerine geçen (OpenAI'nin kaldırma listesinden). */
        private val RETIRED = mapOf(
            "gpt-4o-mini" to "gpt-6-luna",
            "gpt-4.1-mini" to "gpt-6-luna",
            "gpt-4o" to "gpt-6.1-terra",
            "gpt-4.1" to "gpt-6.1-terra",
        )

        /**
         * Hazır seçenekler. Liste kapalı değil: sağlayıcı yeni bir model
         * çıkardığında adını elle yazabilesin diye kutu da düzenlenebilir.
         */
        val MODELS = listOf("gpt-6-luna", "gpt-6.1-terra")
        val GEMINI_MODELS = listOf("gemini-3.8-flash", "gemini-3.6-flash", "gemini-3.5-flash-lite")
        val CLAUDE_MODELS = listOf("claude-haiku-5-5", "claude-sonnet-5-5", "claude-opus-5-5")
        private val USAGE_LABELS = listOf("Haiku", "Sonnet", "Opus", "Claude", "Gemini")

        const val PREFS_NAME = "merkez_yapay_zeka"
        private const val KEY_API = "openai_key"
        private const val KEY_MODEL = "model"
        const val KEY_GEMINI_API = "gemini_key"
        const val KEY_GEMINI_MODEL = "gemini_model"
        const val KEY_CLAUDE_API = "claude_key"
        const val KEY_CLAUDE_MODEL = "claude_model"
        const val KEY_AGENT_PROVIDER = "agent_provider"
        const val KEY_CLAUDE_ESCALATE = "claude_escalate"
        const val KEY_CLAUDE_PLANNER = "claude_planner"
        private const val KEY_HAIKU_MIGRATED = "claude_haiku_migrated"
    }
}

/** Ekran ajanının bir modeldeki toplam harcaması. Dolar yaklaşık, liste fiyatıyla. */
data class AgentUsageRow(
    val label: String,
    val steps: Long,
    val inputTokens: Long,
    val outputTokens: Long,
    val usd: Double,
)
