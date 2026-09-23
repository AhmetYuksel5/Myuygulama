package com.ahmety.uygulama.feature.gestures

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Kilit ekranında katmanları ortadan kaldırmak için ortak yardımcı.
 *
 * Erişilebilirlik katmanı kilit ekranının da üstünde çiziliyor: telefonu
 * cebinden çıkarınca saatin üstünde bir çubuk, kenarda bir şerit duruyordu.
 * Orada ikisinin de bir işi yok — kilitliyken ne jestle uygulama açılıyor
 * ne imleçle bir yere dokunuluyor.
 *
 * Yoklama yok, yayın dinleniyor: ekran kapandı, ekran açıldı, kilit açıldı.
 * Üçünde de [onChange] çağrılıyor ve servis katmanını baştan kuruyor;
 * [locked] o sırada gerçek durumu söylüyor.
 */
internal class LockWatcher(
    private val service: Service,
    private val onChange: () -> Unit,
) {

    /**
     * Gecikmeli: bu yardımcı servisin bir alanı olarak kuruluyor ve alanlar
     * servisin bağlamı bağlanmadan önce çalışıyor. Orada `getSystemService`
     * çağırmak süreci ilk açılışta çökertirdi.
     */
    private val keyguard by lazy {
        service.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = onChange()
    }

    /** Kilit ekranı şu anda önde mi. */
    val locked: Boolean get() = keyguard?.isKeyguardLocked == true

    fun start() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // Yalnız sistemin yayınları dinleniyor; dışarıya açık kayıt
        // Android 14'ten beri zaten reddediliyor.
        runCatching {
            ContextCompat.registerReceiver(
                service,
                receiver,
                filter,
                ContextCompat.RECEIVER_NOT_EXPORTED,
            )
        }
    }

    fun stop() {
        runCatching { service.unregisterReceiver(receiver) }
    }
}
