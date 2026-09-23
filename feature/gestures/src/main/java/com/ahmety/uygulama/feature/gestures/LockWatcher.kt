package com.ahmety.uygulama.feature.gestures

import android.app.KeyguardManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
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
 *
 * **Kilit açıldı yayını sisteme sorulandan daha doğru.** İlk sürümde
 * durumu yalnız `isKeyguardLocked` söylüyordu ve kilit açılma yayını
 * geldiğinde bu değer hâlâ "kilitli" dönebiliyordu (kilit ekranı kayarak
 * kapanırken). Katman o an kurulmuyor, kuracak başka bir olay da
 * gelmediği için telefon kullanılırken çubuk bir daha görünmüyordu.
 * Onun için kilit açılma yayını kendi başına yeterli sayılıyor ve
 * ayrıca olaydan kısa süre sonra bir kez daha bakılıyor.
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

    private val handler = Handler(Looper.getMainLooper())

    /** Kilidin açıldığını yayından biliyoruz; sisteme sormaya gerek yok. */
    private var unlockedByBroadcast = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_USER_PRESENT -> unlockedByBroadcast = true
                // Ekran kapandı: bir sonraki açılışta yeniden kilitli
                // sayılıyor, karar yine sisteme bırakılıyor.
                Intent.ACTION_SCREEN_OFF -> unlockedByBroadcast = false
            }
            onChange()
            // Kilit ekranı kapanırken durum bir süre "kilitli" görünebiliyor;
            // biraz sonra bir daha bakılıyor. Katmanı kurmak ucuz, bir daha
            // kurulması da zararsız.
            RECHECK_MS.forEach { delay ->
                handler.postDelayed({ onChange() }, delay)
            }
        }
    }

    /** Kilit ekranı şu anda önde mi. */
    val locked: Boolean
        get() = !unlockedByBroadcast && keyguard?.isKeyguardLocked == true

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
        handler.removeCallbacksAndMessages(null)
        runCatching { service.unregisterReceiver(receiver) }
    }

    private companion object {
        val RECHECK_MS = longArrayOf(400L, 1_500L)
    }
}
