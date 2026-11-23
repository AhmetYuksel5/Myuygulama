package com.ahmety.uygulama.feature.gestures

import android.app.role.RoleManager
import android.content.Context
import android.content.SharedPreferences
import android.telecom.Call
import android.telecom.CallScreeningService

/** Engellenmiş bir arama: numara ve zaman. */
data class BlockedCall(val number: String, val at: Long)

/**
 * Arama süzgecinin ayarları: hangi başlangıçlar engelleniyor, ne kadar
 * engellendi. Başlangıçlar ulusal biçimde tutuluyor ("0850"); içinde kalıp
 * işaretleri olabilir: `*` istenen kadar rakam, `?` tek rakam ("02129*",
 * "0212???00").
 */
class CallFilterStore(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var isOn: Boolean
        get() = prefs.getBoolean(KEY_ON, true)
        set(value) = prefs.edit().putBoolean(KEY_ON, value).apply()

    fun prefixes(): List<String> =
        prefs.getString(KEY_PREFIXES, DEFAULT_PREFIXES).orEmpty()
            .split(',').map { it.trim() }.filter { it.isNotEmpty() }

    fun savePrefixes(list: List<String>) {
        prefs.edit().putString(KEY_PREFIXES, list.distinct().joinToString(",")).apply()
    }

    fun matches(number: String): Boolean {
        val national = national(number)
        if (national.isEmpty()) return false
        return prefixes().any { pattern(it).matches(national) }
    }

    var blockedCount: Int
        get() = prefs.getInt(KEY_COUNT, 0)
        private set(value) = prefs.edit().putInt(KEY_COUNT, value).apply()

    fun recent(): List<BlockedCall> =
        prefs.getString(KEY_RECENT, "").orEmpty().lines().mapNotNull { line ->
            val parts = line.split('|')
            val at = parts.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
            BlockedCall(parts[0], at)
        }

    fun record(number: String, at: Long) {
        val list = (listOf(BlockedCall(number, at)) + recent()).take(RECENT_LIMIT)
        prefs.edit()
            .putString(KEY_RECENT, list.joinToString("\n") { "${it.number}|${it.at}" })
            .putInt(KEY_COUNT, blockedCount + 1)
            .apply()
    }

    companion object {
        private const val PREFS = "merkez_arama_suzgeci"
        private const val KEY_ON = "on"
        private const val KEY_PREFIXES = "prefixes"
        private const val KEY_COUNT = "blocked_count"
        private const val KEY_RECENT = "recent"
        private const val DEFAULT_PREFIXES = "0850"
        private const val RECENT_LIMIT = 20

        /**
         * Numarayı ulusal biçime çevirir: "+90 850 123 45 67", "90850…" ve
         * "850…" hepsi "0850…" oluyor. Operatör numarayı bazen ülke koduyla,
         * bazen sıfırsız veriyor; başlangıç karşılaştırması tek biçimde yapılsın.
         */
        fun national(number: String): String {
            val digits = number.filter { it.isDigit() }
            return when {
                digits.isEmpty() -> ""
                digits.startsWith("0090") -> "0" + digits.drop(4)
                digits.startsWith("90") && digits.length == 12 -> "0" + digits.drop(2)
                digits.startsWith("0") -> digits
                else -> "0$digits"
            }
        }

        /**
         * Başlangıç kalıbını numaraya uyan düzenli ifadeye çevirir. Kalıp hep
         * baştan karşılaştırılıyor ve sonu açık: "0850" de "0850*" de 0850 ile
         * başlayan her numarayı tutuyor.
         */
        fun pattern(prefix: String): Regex = Regex(
            prefix.map { c ->
                when (c) {
                    '*' -> "\\d*"
                    '?' -> "\\d"
                    else -> Regex.escape(c.toString())
                }
            }.joinToString("") + ".*",
        )

        /** Kalıpta kaç gerçek rakam var; çok genel kalıp (ör. yalnız "0") eklenmesin. */
        fun digitCount(prefix: String): Int = prefix.count { it.isDigit() }

        /**
         * Kullanıcının yazdığı başlangıç ("850", "+90850", "0212 9*") ulusal
         * biçime: "0850", "02129*". Boşluk ve tire atılıyor; x de tek rakam
         * sayılıyor, telefon klavyesinde soru işareti zor bulunuyor.
         */
        fun normalizePrefix(text: String): String {
            val digits = text.trim()
                .map { if (it == 'x' || it == 'X') '?' else it }
                .filter { it.isDigit() || it == '*' || it == '?' }
                .joinToString("")
            return when {
                digits.isEmpty() -> ""
                digits.startsWith("0090") -> "0" + digits.drop(4)
                text.trim().startsWith("+90") || (digits.startsWith("90") && digitCount(digits) > 4) ->
                    "0" + digits.drop(2)
                digits.startsWith("0") -> digits
                else -> "0$digits"
            }
        }

        /** Merkez telefonun arama süzme uygulaması mı. */
        fun isEnabled(context: Context): Boolean = runCatching {
            context.getSystemService(RoleManager::class.java)
                ?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        }.getOrDefault(false)
    }
}

/**
 * Gelen aramayı çalmadan önce süzen servis.
 *
 * 0850'li numaralar çoğunlukla çağrı merkezi ve satış aramaları. Android
 * her gelen aramayı, telefonun "arama süzme uygulaması" seçilmiş
 * uygulamaya önce soruyor; numara listedeki bir başlangıçla eşleşirse
 * arama çalmadan reddediliyor. Arama kaydında kalıyor: önemli bir arama
 * yanlışlıkla engellenirse görülebilsin.
 */
class CallFilterService : CallScreeningService() {

    override fun onScreenCall(details: Call.Details) {
        val number = details.handle?.schemeSpecificPart.orEmpty()
        val store = CallFilterStore(this)
        val block = details.callDirection == Call.Details.DIRECTION_INCOMING &&
            store.isOn &&
            store.matches(number)
        val response = CallResponse.Builder()
        if (block) {
            response
                .setDisallowCall(true)
                .setRejectCall(true)
                .setSkipNotification(true)
                .setSkipCallLog(false)
        }
        respondToCall(details, response.build())
        if (block) store.record(CallFilterStore.national(number), System.currentTimeMillis())
    }
}
