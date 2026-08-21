package com.ahmety.uygulama.feature.subtitles

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenSubtitles erişim bilgileri. Anahtar **yalnızca cihazda** duruyor;
 * depo herkese açık olduğu için kaynağa hiçbir şey yazılmıyor.
 *
 * API anahtarı opensubtitles.com'daki ücretsiz hesabın "Consumers"
 * sayfasından alınıyor. Kullanıcı adı/parola isteğe bağlı: girilirse günlük
 * indirme hakkı artıyor, girilmezse arama yine çalışıyor ama indirme
 * kotası çok düşük.
 */
/**
 * Altyazıdan öğrenilen dil.
 *
 * Türkçe tarafı sabit — karşılaştırmak için o duruyor. Değişen, filmi
 * hangi dilde izleyeceğin.
 */
enum class SubtitleLanguage(val code: String, val label: String) {
    ENGLISH("en", "İngilizce"),
    ARABIC("ar", "Arapça"),
}

@Singleton
class SubtitleSettings @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var apiKey: String
        get() = prefs.getString(KEY_API, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_API, value.trim()).apply()

    var username: String
        get() = prefs.getString(KEY_USER, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_USER, value.trim()).apply()

    var password: String
        get() = prefs.getString(KEY_PASS, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_PASS, value).apply()

    /** Oturum belirteci; indirme kotası için. Süresi dolarsa yeniden alınıyor. */
    var token: String
        get() = prefs.getString(KEY_TOKEN, null).orEmpty()
        set(value) = prefs.edit().putString(KEY_TOKEN, value).apply()

    /**
     * Hesabın kullanacağı sunucu. Giriş yanıtı farklı bir adres verirse
     * (VIP hesaplarda vip-api'ye dönüyor) oraya yazılıyor.
     */
    var baseUrl: String
        get() = prefs.getString(KEY_BASE, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_BASE
        set(value) = prefs.edit().putString(KEY_BASE, value.trim()).apply()

    /**
     * Zorluk eşiği, 0-100. Bu puanın üstündeki kelime ve cümleler
     * çıkarılıyor. Varsayılan 60: orta seviyenin biraz üstü.
     */
    var difficulty: Int
        get() = prefs.getInt(KEY_DIFFICULTY, 60).coerceIn(0, 100)
        set(value) = prefs.edit().putInt(KEY_DIFFICULTY, value.coerceIn(0, 100)).apply()

    /** Son seçilen dil; ekran açıldığında onunla başlıyor. */
    var language: SubtitleLanguage
        get() = runCatching { SubtitleLanguage.valueOf(prefs.getString(KEY_LANGUAGE, null).orEmpty()) }
            .getOrDefault(SubtitleLanguage.ENGLISH)
        set(value) = prefs.edit().putString(KEY_LANGUAGE, value.name).apply()

    val configured: Boolean get() = apiKey.isNotBlank()

    fun maskedKey(): String {
        val key = apiKey
        if (key.isBlank()) return ""
        return if (key.length <= 8) "•".repeat(key.length) else key.take(4) + "…" + key.takeLast(4)
    }

    fun clear() {
        // Zorluk eşiği hesapla ilgili değil; anahtarı silmek onu sıfırlamasın.
        val keepDifficulty = difficulty
        val keepLanguage = language
        prefs.edit().clear().apply()
        difficulty = keepDifficulty
        language = keepLanguage
    }

    private companion object {
        const val PREFS_NAME = "merkez_altyazi"
        const val KEY_API = "api_key"
        const val KEY_USER = "username"
        const val KEY_PASS = "password"
        const val KEY_TOKEN = "token"
        const val KEY_BASE = "base_url"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_LANGUAGE = "language"
        const val DEFAULT_BASE = "https://api.opensubtitles.com"
    }
}
