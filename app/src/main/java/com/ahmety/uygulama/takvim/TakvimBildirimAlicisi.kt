package com.ahmety.uygulama.takvim

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Takvim bildirimindeki okların ve yeniden başlatmanın karşılandığı yer.
 *
 * Bildirimdeki düğmeler etkinlik açamaz — açsalar her ay değişiminde
 * uygulama öne gelirdi. Yayın alıcısı sessizce çalışıp bildirimi
 * yeniden çiziyor.
 */
class TakvimBildirimAlicisi : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            TakvimBildirimi.ACTION_GERI -> TakvimBildirimi.kaydir(context, -1)
            TakvimBildirimi.ACTION_ILERI -> TakvimBildirimi.kaydir(context, 1)
            TakvimBildirimi.ACTION_BUGUN -> TakvimBildirimi.kaydir(context, 0)
            TakvimBildirimi.ACTION_GUN_DONDU -> TakvimBildirimi.gunDondu(context)
            // Yeniden başlatmada bildirim kayboluyor; açıksa geri koyuyoruz.
            Intent.ACTION_BOOT_COMPLETED ->
                if (TakvimBildirimi.acikMi(context)) TakvimBildirimi.goster(context)
        }
    }
}
