package com.ahmety.uygulama.feature.vocab

import android.content.Context

/** Kart destesinin kullanıcıya göre ayarlanan davranışı. */
class VocabPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Kartın "fırlaması" için gereken sürükleme mesafesi (**dp**).
     * Piksel olsaydı aynı değer farklı yoğunluktaki telefonlarda çok farklı
     * hissettirirdi. Küçük değer = az hareketle fırlar.
     */
    var swipeThreshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, 40)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(20, 160)).apply()

    private companion object {
        const val PREFS_NAME = "merkez_kelime"
        const val KEY_THRESHOLD = "swipe_threshold"
    }
}
