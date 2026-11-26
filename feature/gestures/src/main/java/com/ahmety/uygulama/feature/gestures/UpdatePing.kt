package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.content.Intent

/**
 * Merkez'in bir özelliği kullanıldığında güncelleme denetimini dürter.
 *
 * Kullanıcı Merkez'in ekranını açmadan yalnız imleç, kısayollar, ekrana sor
 * ya da ajan kullanabiliyor; yeni sürüm o zaman da fark edilsin. Zamanlı
 * (6 saatte bir) denetim istenmedi: yalnız uygulama kullanılırken bakılıyor.
 * Denetimin kendisi app modülünde; bu modül onu bilmediği için sınıf adıyla
 * yayın gönderiliyor. Saatte en çok bir kez.
 */
internal object UpdatePing {
    private const val PREFS = "merkez_guncelleme_durt"
    private const val KEY_LAST = "son"
    private const val EVERY_MS = 3_600_000L
    private const val RECEIVER = "com.ahmety.uygulama.ui.update.UpdateAlarmReceiver"

    fun kick(context: Context) = runCatching {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        if (now - prefs.getLong(KEY_LAST, 0L) < EVERY_MS) return@runCatching
        prefs.edit().putLong(KEY_LAST, now).apply()
        context.sendBroadcast(Intent().setClassName(context.packageName, RECEIVER))
    }
}
