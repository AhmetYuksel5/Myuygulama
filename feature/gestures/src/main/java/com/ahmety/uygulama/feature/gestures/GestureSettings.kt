package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/** Bir jeste atanabilecek eylemler. */
enum class GestureAction(val label: String) {
    NONE("Yok"),
    BACK("Geri"),
    HOME("Ana ekran"),
    RECENTS("Son uygulamalar"),
    NOTIFICATIONS("Bildirimler"),
    QUICK_SETTINGS("Hızlı ayarlar"),
    LOCK_SCREEN("Ekranı kilitle"),
    SCREENSHOT("Ekran görüntüsü"),
    POWER_DIALOG("Güç menüsü"),
    OPEN_APP("Uygulama aç"),
}

/**
 * Kenar hareketlerinin ayarları.
 *
 * Servis SharedPreferences'ı doğrudan okuyor (Hilt ile enjekte edilemez,
 * sistem oluşturuyor). Değişiklikler [android.content.SharedPreferences.registerOnSharedPreferenceChangeListener]
 * ile canlı yakalanabildiği için kalınlık/uzunluk/konum ayarları artık servisi
 * yeniden başlatmadan uygulanıyor.
 */
class GestureSettings(context: Context) {

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    var showLeft: Boolean
        get() = prefs.getBoolean(KEY_LEFT, false)
        set(value) = prefs.edit().putBoolean(KEY_LEFT, value).apply()

    var showRight: Boolean
        get() = prefs.getBoolean(KEY_RIGHT, true)
        set(value) = prefs.edit().putBoolean(KEY_RIGHT, value).apply()

    var widthDp: Int
        get() = prefs.getInt(KEY_WIDTH, 5)
        set(value) = prefs.edit().putInt(KEY_WIDTH, value).apply()

    var heightDp: Int
        get() = prefs.getInt(KEY_HEIGHT, 160)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value).apply()

    /** Merkeze göre dikey kayma; negatif yukarı, pozitif aşağı. */
    var verticalOffsetDp: Int
        get() = prefs.getInt(KEY_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_OFFSET, value).apply()

    var colorArgb: Int
        get() = prefs.getInt(KEY_COLOR, DEFAULT_COLOR)
        set(value) = prefs.edit().putInt(KEY_COLOR, value).apply()

    /**
     * Şeridin görünürlüğü: 0 = tamamen saydam (görünmez ama yine dokunulabilir),
     * 100 = tam opak. Rengin alfa kanalını bununla eziyoruz.
     */
    var opacityPercent: Int
        get() = prefs.getInt(KEY_OPACITY, DEFAULT_OPACITY)
        set(value) = prefs.edit().putInt(KEY_OPACITY, value.coerceIn(0, 100)).apply()

    /** Jest algılanınca hafif bir "bip" sesi çalınsın mı. */
    var soundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_SOUND, value).apply()

    /** Bip sesinin yüksekliği: 5–100 arası yüzde. */
    var soundVolume: Int
        get() = prefs.getInt(KEY_SOUND_VOLUME, 25)
        set(value) = prefs.edit().putInt(KEY_SOUND_VOLUME, value.coerceIn(5, 100)).apply()

    var vibrateEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIBRATE, true)
        set(value) = prefs.edit().putBoolean(KEY_VIBRATE, value).apply()

    /**
     * Geri (içeri) ile dikey kaydırmayı ayıran açı, derece.
     *
     * Parmağın yatayla yaptığı açı bu değerin altındaysa hareket "içeri"
     * sayılır (genelde geri), üstündeyse yukarı/aşağı sayılır. Küçük değer:
     * dikey hareketler baskın. Büyük değer: geri baskın.
     */
    var backAngleDegrees: Int
        get() = prefs.getInt(KEY_ANGLE, 45).coerceIn(15, 75)
        set(value) = prefs.edit().putInt(KEY_ANGLE, value.coerceIn(15, 75)).apply()

    /** 0 = hafif, 1 = orta, 2 = güçlü. */
    var vibrateStrength: Int
        get() = prefs.getInt(KEY_VIBRATE_STRENGTH, 1)
        set(value) = prefs.edit().putInt(KEY_VIBRATE_STRENGTH, value.coerceIn(0, 2)).apply()

    // Jest → eylem eşlemesi. Her jest ayrı ayrı atanabiliyor.
    var swipeUpAction: GestureAction
        get() = actionOf(KEY_ACTION_UP, GestureAction.RECENTS)
        set(value) = prefs.edit().putString(KEY_ACTION_UP, value.name).apply()

    var swipeDownAction: GestureAction
        get() = actionOf(KEY_ACTION_DOWN, GestureAction.NOTIFICATIONS)
        set(value) = prefs.edit().putString(KEY_ACTION_DOWN, value.name).apply()

    var swipeInAction: GestureAction
        get() = actionOf(KEY_ACTION_IN, GestureAction.BACK)
        set(value) = prefs.edit().putString(KEY_ACTION_IN, value.name).apply()

    var longPressAction: GestureAction
        get() = actionOf(KEY_ACTION_LONG, GestureAction.HOME)
        set(value) = prefs.edit().putString(KEY_ACTION_LONG, value.name).apply()

    /** Şeride arka arkaya iki dokunuş. Varsayılan olarak kapalı. */
    var doubleTapAction: GestureAction
        get() = actionOf(KEY_ACTION_DOUBLE, GestureAction.NONE)
        set(value) = prefs.edit().putString(KEY_ACTION_DOUBLE, value.name).apply()

    /**
     * [GestureAction.OPEN_APP] seçildiğinde açılacak uygulamanın paket adı.
     * Her jest kendi uygulamasını taşıyabilsin diye anahtar jest başına ayrı.
     */
    fun appFor(gesture: String): String =
        prefs.getString(KEY_APP_PREFIX + gesture, null).orEmpty()

    fun setAppFor(gesture: String, packageName: String) {
        prefs.edit().putString(KEY_APP_PREFIX + gesture, packageName).apply()
    }

    private fun actionOf(key: String, default: GestureAction): GestureAction =
        prefs.getString(key, null)?.let { name ->
            runCatching { GestureAction.valueOf(name) }.getOrNull()
        } ?: default

    companion object {
        const val PREFS_NAME = "merkez_kenar"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LEFT = "left_edge"
        private const val KEY_RIGHT = "right_edge"
        private const val KEY_WIDTH = "width_dp"
        private const val KEY_HEIGHT = "height_dp"
        private const val KEY_OFFSET = "offset_dp"
        private const val KEY_COLOR = "color"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_SOUND = "sound"
        private const val KEY_SOUND_VOLUME = "sound_volume"
        private const val KEY_ANGLE = "back_angle"
        private const val KEY_VIBRATE = "vibrate"
        private const val KEY_VIBRATE_STRENGTH = "vibrate_strength"
        private const val KEY_ACTION_UP = "action_up"
        private const val KEY_ACTION_DOWN = "action_down"
        private const val KEY_ACTION_IN = "action_in"
        private const val KEY_ACTION_LONG = "action_long"
        private const val KEY_ACTION_DOUBLE = "action_double"
        private const val KEY_APP_PREFIX = "app_for_"

        // Jest adları: hangi jeste hangi uygulamanın atandığını ayırmak için.
        const val GESTURE_UP = "up"
        const val GESTURE_DOWN = "down"
        const val GESTURE_IN = "in"
        const val GESTURE_LONG = "long"
        const val GESTURE_DOUBLE = "double"

        /** Yarı saydam beyaz: koyu ve açık arayüzlerde de seçilebiliyor. */
        private const val DEFAULT_COLOR = 0x66FFFFFF

        /** 0x66 ≈ %40 alfa; eski varsayılanla aynı görünürlük. */
        private const val DEFAULT_OPACITY = 40

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
