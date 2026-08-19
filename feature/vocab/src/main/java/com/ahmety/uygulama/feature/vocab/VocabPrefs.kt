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
        // Okurken de kırpıyoruz: birim dp'ye çevrildi, eski/bozuk bir
        // değer kalmışsa eşik kullanılamayacak kadar yüksek başlamasın.
        get() = prefs.getInt(KEY_THRESHOLD, 40).coerceIn(20, 160)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value.coerceIn(20, 160)).apply()

    /**
     * Kalem süzgeci. Sekmeler arasında gidip gelince ekran sıfırdan
     * kuruluyor; "yalnız kırmızılar" seçimi her seferinde bozulmasın.
     */
    var pen: VocabPen
        get() = runCatching { VocabPen.valueOf(prefs.getString(KEY_PEN, null).orEmpty()) }
            .getOrDefault(VocabPen.BOTH)
        set(value) = prefs.edit().putString(KEY_PEN, value.name).apply()

    private companion object {
        const val PREFS_NAME = "merkez_kelime"
        const val KEY_THRESHOLD = "swipe_threshold"
        const val KEY_PEN = "pen"
    }
}
