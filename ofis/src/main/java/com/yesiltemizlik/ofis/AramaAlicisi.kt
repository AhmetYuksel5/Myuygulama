package com.yesiltemizlik.ofis

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager

/**
 * Görüşme bitince kartı açar. Yalnız gerçekten konuşulan (açılan ya da
 * aranan) görüşmeden sonra: cevapsız çağrı OFFHOOK'a hiç geçmez.
 *
 * Android 10+ arama kaydı izni olan uygulamaya aynı durumu iki kez yollar
 * (biri numaralı, biri numarasız); bayrak ilk IDLE'da sıfırlandığı için
 * kart bir kez açılır.
 */
class AramaAlicisi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val durum = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val d = Depo(context)
        when (durum) {
            TelephonyManager.EXTRA_STATE_OFFHOOK -> if (!d.konusuldu) {
                d.aramaBas = System.currentTimeMillis()
                d.konusuldu = true
            }
            TelephonyManager.EXTRA_STATE_IDLE -> if (d.konusuldu) {
                d.konusuldu = false
                if (!d.kartAcik) return
                // Kart çıkmayan numara listesine bakabilmek için numara gerekiyor;
                // arama kaydı IDLE'dan biraz sonra yazılıyor, en çok 3 sn beklenir.
                // Bulunan arama karta hazır verilir, kart yeniden aramaz.
                val bas = d.aramaBas
                val bekle = goAsync()
                Thread {
                    try {
                        var a: Arama? = null
                        for (i in 0 until 6) {
                            a = bitenArama(context, bas)
                            if (a != null) break
                            Thread.sleep(500)
                        }
                        when {
                            a == null -> KartAcici.ac(context, bas)
                            d.engelliMi(a.tel) -> {}
                            else -> KartAcici.ac(context, a)
                        }
                    } finally {
                        bekle.finish()
                    }
                }.start()
            }
        }
    }
}
