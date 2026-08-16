package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Kenar şeridinin ayarları. Servis sistem tarafından başlatıldığı için
 * Hilt yerine doğrudan SharedPreferences okuyoruz — servis oluşturulurken
 * bir bileşen enjeksiyonu beklemek gereksiz karmaşa olurdu.
 */
class GestureSettings(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var onRightEdge: Boolean
        get() = prefs.getBoolean(KEY_RIGHT_EDGE, true)
        set(value) = prefs.edit().putBoolean(KEY_RIGHT_EDGE, value).apply()

    var widthDp: Int
        get() = prefs.getInt(KEY_WIDTH, 5)
        set(value) = prefs.edit().putInt(KEY_WIDTH, value).apply()

    var heightDp: Int
        get() = prefs.getInt(KEY_HEIGHT, 160)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    var verticalOffsetDp: Int
        get() = prefs.getInt(KEY_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_OFFSET, value).apply()

    var colorArgb: Int
        get() = prefs.getInt(KEY_COLOR, DEFAULT_COLOR)
        set(value) = prefs.edit().putInt(KEY_COLOR, value).apply()

    companion object {
        private const val PREFS_NAME = "merkez_kenar"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_RIGHT_EDGE = "right_edge"
        private const val KEY_WIDTH = "width_dp"
        private const val KEY_HEIGHT = "height_dp"
        private const val KEY_OFFSET = "offset_dp"
        private const val KEY_COLOR = "color"

        /** Yarı saydam beyaz: koyu ve açık arayüzlerde de seçilebiliyor. */
        private const val DEFAULT_COLOR = 0x66FFFFFF

        /**
         * Servis kullanıcı tarafından açılmış mı? Erişilebilirlik servisleri
         * yalnızca sistem ayarlarından etkinleştirilebilir; uygulama kendi
         * kendini açamaz (bu bilinçli bir Android kısıtı).
         */
        fun isServiceEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${EdgeGestureService::class.java.name}"
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
            ) ?: return false

            val splitter = TextUtils.SimpleStringSplitter(':')
            splitter.setString(enabledServices)
            while (splitter.hasNext()) {
                if (splitter.next().equals(expected, ignoreCase = true)) return true
            }
            return false
        }

        fun accessibilitySettingsIntent(): Intent =
            Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}
