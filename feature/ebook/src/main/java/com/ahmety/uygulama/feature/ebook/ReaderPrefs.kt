package com.ahmety.uygulama.feature.ebook

import android.content.Context
import androidx.compose.ui.graphics.Color

/** Okuma zemini. Uzun okumada göz yormasın diye üç seçenek. */
enum class ReaderTheme(val label: String, val background: Color, val text: Color) {
    CREAM("Krem", Color(0xFFF5EEDC), Color(0xFF2B2620)),
    WHITE("Beyaz", Color(0xFFFFFFFF), Color(0xFF1A1A1A)),
    BLACK("Siyah", Color(0xFF0E0E0E), Color(0xFFD8D5D0)),
}

class ReaderPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var theme: ReaderTheme
        get() = runCatching { ReaderTheme.valueOf(prefs.getString(KEY_THEME, null) ?: "") }
            .getOrDefault(ReaderTheme.CREAM)
        set(value) = prefs.edit().putString(KEY_THEME, value.name).apply()

    var fontSizeSp: Int
        get() = prefs.getInt(KEY_FONT, 18).coerceIn(14, 28)
        set(value) = prefs.edit().putInt(KEY_FONT, value.coerceIn(14, 28)).apply()

    private companion object {
        const val PREFS_NAME = "merkez_okuyucu"
        const val KEY_THEME = "theme"
        const val KEY_FONT = "font_sp"
    }
}
