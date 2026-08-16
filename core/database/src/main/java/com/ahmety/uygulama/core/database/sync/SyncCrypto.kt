package com.ahmety.uygulama.core.database.sync

import android.content.Context
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Paylaşılan alana (Drive / klasör) çıkan her şey burada şifrelenir.
 *
 * Anahtar cihazda üretilir ve **kurtarma anahtarı** olarak kullanıcıya gösterilir;
 * ikinci telefona aynı anahtar girilir. Taşıyıcı yalnızca anlamsız baytlar görür.
 * Anahtarı kaybedersen buluttaki veri okunamaz — bu bilinçli bir tercih:
 * kurtarılabilir olması, taşıyıcının okuyabilmesi demekti.
 */
@Singleton
class SyncCrypto @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun hasKey(): Boolean = prefs.getString(KEY_NAME, null) != null

    /** Anahtar yoksa üretir; kullanıcıya gösterilecek metin biçimini döndürür. */
    fun ensureKey(): String {
        prefs.getString(KEY_NAME, null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        prefs.edit().putString(KEY_NAME, encoded).apply()
        return encoded
    }

    fun currentKey(): String? = prefs.getString(KEY_NAME, null)

    /**
     * İkinci cihazda kurtarma anahtarını içeri alır.
     * @return anahtar geçerliyse true
     */
    fun importKey(encoded: String): Boolean {
        val trimmed = encoded.trim()
        val decoded = runCatching { Base64.decode(trimmed, Base64.DEFAULT) }.getOrNull()
        if (decoded == null || decoded.size != 32) return false
        prefs.edit().putString(KEY_NAME, Base64.encodeToString(decoded, Base64.NO_WRAP)).apply()
        return true
    }

    fun encrypt(plain: ByteArray): ByteArray {
        val key = secretKey() ?: error("Senkron anahtarı yok")
        val iv = ByteArray(IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        // IV'yi şifreli metnin başına koyuyoruz; ayrı bir dosyada tutmak
        // senkron sırasında ikisinin ayrı düşmesi riskini getirirdi.
        return iv + cipher.doFinal(plain)
    }

    fun decrypt(payload: ByteArray): ByteArray? {
        val key = secretKey() ?: return null
        if (payload.size <= IV_LENGTH) return null
        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                key,
                GCMParameterSpec(TAG_BITS, payload.copyOfRange(0, IV_LENGTH)),
            )
            cipher.doFinal(payload.copyOfRange(IV_LENGTH, payload.size))
        }.getOrNull()
    }

    private fun secretKey(): SecretKeySpec? =
        prefs.getString(KEY_NAME, null)
            ?.let { SecretKeySpec(Base64.decode(it, Base64.DEFAULT), "AES") }

    private companion object {
        const val PREFS_NAME = "merkez_senkron"
        const val KEY_NAME = "sync_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_LENGTH = 12
        const val TAG_BITS = 128
    }
}
