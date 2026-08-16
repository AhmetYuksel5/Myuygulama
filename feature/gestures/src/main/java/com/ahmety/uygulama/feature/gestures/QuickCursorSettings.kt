package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Quick Cursor benzeri "tek elle erişim" tutamağının ayarları.
 *
 * Ekranın kenarında küçük bir top durur; ona parmağını basıp gezdirince
 * ekranda bir sanal imleç (trackpad gibi, görece hareketle) dolaşır, parmağını
 * kaldırınca imlecin olduğu yere dokunma gönderilir. Sol üst köşe gibi tek elle
 * ulaşılamayan yerlere basmak için.
 */
class QuickCursorSettings(context: Context) {

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var onRight: Boolean
        get() = prefs.getBoolean(KEY_RIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_RIGHT, value).apply()

    /** Topun ekranın altından yukarı doğru konumu (dp). */
    var bottomOffsetDp: Int
        get() = prefs.getInt(KEY_OFFSET, 180)
        set(value) = prefs.edit().putInt(KEY_OFFSET, value).apply()

    var handleSizeDp: Int
        get() = prefs.getInt(KEY_SIZE, 56)
        set(value) = prefs.edit().putInt(KEY_SIZE, value.coerceIn(36, 96)).apply()

    /** Topun saydamlığı: 20–100 arası yüzde. */
    var opacityPercent: Int
        get() = prefs.getInt(KEY_OPACITY, 55)
        set(value) = prefs.edit().putInt(KEY_OPACITY, value.coerceIn(20, 100)).apply()

    /** İmleç hassasiyeti: parmak hareketinin kaç katı imleç hareketi. */
    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 2.2f)
        set(value) = prefs.edit().putFloat(KEY_SENSITIVITY, value.coerceIn(1f, 4f)).apply()

    companion object {
        const val PREFS_NAME = "merkez_quick_cursor"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_RIGHT = "right"
        private const val KEY_OFFSET = "bottom_offset"
        private const val KEY_SIZE = "handle_size"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_SENSITIVITY = "sensitivity"

        fun isServiceEnabled(context: Context): Boolean {
            val expected = "${context.packageName}/${QuickCursorService::class.java.name}"
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

        fun accessibilitySettingsIntent(): Intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
    }
}
