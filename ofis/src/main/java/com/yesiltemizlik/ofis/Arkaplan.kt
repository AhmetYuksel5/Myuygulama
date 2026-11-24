package com.yesiltemizlik.ofis

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.util.concurrent.Executors

/**
 * Ağ işleri tek sıralı iş parçacığında. Kayıt önce telefona yazılır, sonra
 * gönderilir: bağlantı yoksa kaybolmaz, uygulama ya da sonraki kart açılınca
 * yeniden denenir. Kimlik telefonda üretildiği için iki kez gitse de sunucuda
 * tek kayıt kalır.
 */
object Arkaplan {
    private val yurutucu = Executors.newSingleThreadExecutor()
    private val ana = Handler(Looper.getMainLooper())

    /** Bitince ana iş parçacığında: kalan kayıt sayısı ve varsa hata. */
    fun kuyruguGonder(context: Context, bitti: ((Int, String?) -> Unit)? = null) {
        val app = context.applicationContext
        yurutucu.execute {
            val d = Depo(app)
            var hata: String? = null
            val giden = mutableSetOf<String>()
            if (d.jeton.isNotEmpty()) {
                for (k in d.kuyruk()) {
                    try {
                        Sunucu.kaydet(d.jeton, k)
                        giden += k.optString("id")
                    } catch (e: SunucuHatasi) {
                        hata = e.message
                        break
                    }
                }
            } else {
                hata = "Eşleşme yok"
            }
            if (giden.isNotEmpty()) d.kuyruktanCikar(giden)
            val kalan = d.kuyruk().size
            ana.post { bitti?.invoke(kalan, hata) }
        }
    }

    fun isleriYenile(context: Context, bitti: ((String?) -> Unit)? = null) {
        val app = context.applicationContext
        yurutucu.execute {
            val d = Depo(app)
            var hata: String? = null
            if (d.jeton.isEmpty()) {
                hata = "Eşleşme yok"
            } else {
                try {
                    d.islerYaz(Sunucu.isler(d.jeton))
                } catch (e: SunucuHatasi) {
                    hata = e.message
                }
            }
            ana.post { bitti?.invoke(hata) }
        }
    }

    fun eslestir(context: Context, kod: String, cihaz: String, bitti: (String?) -> Unit) {
        val app = context.applicationContext
        yurutucu.execute {
            var hata: String? = null
            try {
                val j = Sunucu.eslestir(kod, cihaz)
                val d = Depo(app)
                d.jeton = j
                try {
                    d.islerYaz(Sunucu.isler(j))
                } catch (_: SunucuHatasi) {
                }
            } catch (e: SunucuHatasi) {
                hata = e.message
            }
            ana.post { bitti(hata) }
        }
    }
}
