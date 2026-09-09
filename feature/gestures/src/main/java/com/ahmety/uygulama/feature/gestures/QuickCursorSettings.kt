package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils

/**
 * Quick Cursor benzeri "tek elle erişim" tutamağının ayarları.
 *
 * Ekranın dibinde yatay bir çubuk durur; ona parmağını basıp gezdirince
 * ekranda bir sanal imleç (trackpad gibi, görece hareketle) dolaşır, parmağını
 * kaldırınca imlecin olduğu yere dokunma gönderilir. Sol üst köşe gibi tek elle
 * ulaşılamayan yerlere basmak için.
 *
 * Tutamak önce kenarda duran bir toptu ve hangi kenarda olacağı seçiliyordu.
 * Çubuk dibe yatay oturunca kenar seçiminin karşılığı kalmadı: çubuk zaten
 * enine uzanıyor, yatay yeri merkeze göre kaydırılıyor.
 */
class QuickCursorSettings(context: Context) {

    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Top çubuğa dönüşünce eski konum anlamını yitirdi: kenarda 180 dp
        // yukarıda duran bir topun değeri, dipte enine uzanan bir çubuğu
        // ekranın ortasına asıyordu. Eski kurulumlarda konumu bir kez
        // sıfırlıyoruz; kullanıcı sonra istediği yere taşıyor.
        if (prefs.contains(KEY_LEGACY_SIZE) && !prefs.contains(KEY_MIGRATED)) {
            prefs.edit()
                .remove(KEY_LEGACY_SIZE)
                .remove(KEY_LEGACY_SIDE_OFFSET)
                .remove(KEY_LEGACY_RIGHT)
                .putInt(KEY_OFFSET, 0)
                .putBoolean(KEY_MIGRATED, true)
                .apply()
        }
    }

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    /** Çubuğun ekranın altından yukarı doğru konumu (dp). 0 = en dipte. */
    var bottomOffsetDp: Int
        get() = prefs.getInt(KEY_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_OFFSET, value.coerceAtLeast(0)).apply()

    /**
     * Çubuğun yatay kayması (dp), ekranın ortasına göre. Eksi değer sola,
     * artı değer sağa. 0 = tam ortada.
     */
    var centerOffsetDp: Int
        get() = prefs.getInt(KEY_CENTER_OFFSET, 0)
        set(value) = prefs.edit().putInt(KEY_CENTER_OFFSET, value).apply()

    /** Çubuğun eni (dp). */
    var handleWidthDp: Int
        get() = prefs.getInt(KEY_WIDTH, 140)
        set(value) = prefs.edit().putInt(KEY_WIDTH, value.coerceIn(60, 320)).apply()

    /** Çubuğun kalınlığı (dp). */
    var handleHeightDp: Int
        get() = prefs.getInt(KEY_HEIGHT, 18)
        set(value) = prefs.edit().putInt(KEY_HEIGHT, value.coerceIn(8, 56)).apply()

    /** Çubuğun saydamlığı: 20–100 arası yüzde. */
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
        private const val KEY_OFFSET = "bottom_offset"
        // Yeni anahtarlar: eski "side_offset" kenardan içeri ölçülüyordu,
        // yenisi merkezden. Aynı adı kullanmak eski değeri yanlış yerde
        // gösterirdi.
        private const val KEY_CENTER_OFFSET = "center_offset"
        private const val KEY_WIDTH = "handle_width"
        private const val KEY_HEIGHT = "handle_height"
        private const val KEY_OPACITY = "opacity"
        private const val KEY_SENSITIVITY = "sensitivity"

        // Topun bıraktıkları; yalnız bir kez temizlemek için duruyorlar.
        private const val KEY_LEGACY_SIZE = "handle_size"
        private const val KEY_LEGACY_SIDE_OFFSET = "side_offset"
        private const val KEY_LEGACY_RIGHT = "right"
        private const val KEY_MIGRATED = "bar_migrated"

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
