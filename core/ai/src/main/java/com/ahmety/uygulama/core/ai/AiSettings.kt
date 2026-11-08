package com.ahmety.uygulama.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI anahtarı **yalnızca cihazda** durur.
 *
 * Depo herkese açık olduğu için anahtar kaynağa, ayarlara ya da derleme
 * betiklerine hiçbir şekilde yazılmıyor; kullanıcı uygulamadan giriyor ve
 * uygulamanın kendi özel alanında saklanıyor (başka uygulamalar okuyamaz).
 * Yedeklerde de taşınmasın diye `allowBackup=false`.
 */
@Singleton
class AiSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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
    fun maskedKey(): String {
        val key = apiKey
        if (key.isBlank()) return ""
        return if (key.length <= 10) "•".repeat(key.length) else key.take(6) + "…" + key.takeLast(4)
    }

    fun clear() {
        prefs.edit().remove(KEY_API).apply()
    }

    companion object {
        /**
         * OpenAI'nin ucuz metin modeli. Önceki varsayılan `gpt-4o-mini`
         * 14 Ocak 2027'de kapanıyor; bu hem daha ucuz hem onun yerine geçen.
         */
        const val DEFAULT_MODEL = "gpt-6-luna"

        /** Kapanan model → yerine geçen (OpenAI'nin kaldırma listesinden). */
        private val RETIRED = mapOf(
            "gpt-4o-mini" to "gpt-6-luna",
            "gpt-4.1-mini" to "gpt-6-luna",
            "gpt-4o" to "gpt-6.1-terra",
            "gpt-4.1" to "gpt-6.1-terra",
        )


        /**
         * Hazır seçenekler. Liste kapalı değil: OpenAI yeni bir model
         * çıkardığında adını elle yazabilesin diye kutu da düzenlenebilir.
         */
        val MODELS = listOf("gpt-6-luna", "gpt-6.1-terra")

        private const val PREFS_NAME = "merkez_yapay_zeka"
        private const val KEY_API = "openai_key"
        private const val KEY_MODEL = "model"
    }
}
