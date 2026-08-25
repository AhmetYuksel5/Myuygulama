package com.ahmety.uygulama.takvim

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ahmety.uygulama.MainActivity
import com.ahmety.uygulama.R
import java.time.LocalDate
import java.time.YearMonth

/**
 * Bildirim çubuğunda duran ay takvimi.
 *
 * Sürekli açık kalıyor ve uygulamayı açmadan bakılabiliyor. Görünüm
 * Compose değil `RemoteViews`: bildirimi sistem kendi sürecinde çiziyor,
 * oraya yalnızca XML düzenler ve sınırlı bir görünüm kümesi geçebiliyor.
 *
 * Gösterilen ay bir kayma olarak saklanıyor (bugünün ayına göre kaç ay
 * ileri/geri). Mutlak tarih saklasaydık ay dönünce takvim geçmişte kalırdı.
 */
object TakvimBildirimi {

    private const val CHANNEL_ID = "takvim"
    private const val NOTIFICATION_ID = 4201
    private const val PREFS = "merkez_takvim"
    private const val KEY_KAYMA = "ay_kaymasi"
    private const val KEY_ACIK = "acik"

    const val ACTION_GERI = "com.ahmety.uygulama.TAKVIM_GERI"
    const val ACTION_ILERI = "com.ahmety.uygulama.TAKVIM_ILERI"
    const val ACTION_BUGUN = "com.ahmety.uygulama.TAKVIM_BUGUN"

    private val GUN_ADLARI = listOf("Pt", "Sa", "Ça", "Pe", "Cu", "Ct", "Pz")

    private val AYLAR = listOf(
        "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
        "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
    )

    fun acikMi(context: Context): Boolean = prefs(context).getBoolean(KEY_ACIK, false)

    fun ayarla(context: Context, acik: Boolean) {
        prefs(context).edit().putBoolean(KEY_ACIK, acik).apply()
        if (acik) goster(context) else kaldir(context)
    }

    /** Gösterilen ayı [delta] kadar kaydırır; sıfır verilirse bugüne döner. */
    fun kaydir(context: Context, delta: Int) {
        val store = prefs(context)
        val yeni = if (delta == 0) 0 else store.getInt(KEY_KAYMA, 0) + delta
        store.edit().putInt(KEY_KAYMA, yeni).apply()
        goster(context)
    }

    fun kaldir(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    fun goster(context: Context) {
        kanalKur(context)

        val bugun = LocalDate.now()
        val kayma = prefs(context).getInt(KEY_KAYMA, 0)
        val ay = YearMonth.from(bugun).plusMonths(kayma.toLong())

        val govde = RemoteViews(context.packageName, R.layout.takvim_bildirim)
        govde.setTextViewText(
            R.id.takvim_baslik,
            "${AYLAR[ay.monthValue - 1]} ${ay.year}",
        )
        govde.setOnClickPendingIntent(R.id.takvim_geri, yayin(context, ACTION_GERI))
        govde.setOnClickPendingIntent(R.id.takvim_ileri, yayin(context, ACTION_ILERI))
        govde.setOnClickPendingIntent(R.id.takvim_bugun_dugme, yayin(context, ACTION_BUGUN))

        // Gün adları satırı.
        govde.removeAllViews(R.id.takvim_gun_adlari)
        GUN_ADLARI.forEach { ad ->
            val hucre = RemoteViews(context.packageName, R.layout.takvim_hucre)
            hucre.setTextViewText(R.id.takvim_hucre_yazi, ad)
            govde.addView(R.id.takvim_gun_adlari, hucre)
        }

        // Günler. Ay pazartesiyle başlamıyorsa baştaki hücreler boş kalıyor.
        govde.removeAllViews(R.id.takvim_gunler)
        val ilkGunSutunu = ay.atDay(1).dayOfWeek.value - 1
        val gunSayisi = ay.lengthOfMonth()
        val toplamHucre = ilkGunSutunu + gunSayisi
        val satirSayisi = (toplamHucre + 6) / 7
        val buAy = ay == YearMonth.from(bugun)

        for (satir in 0 until satirSayisi) {
            val satirGorunum = RemoteViews(context.packageName, R.layout.takvim_satir)
            for (sutun in 0..6) {
                val sira = satir * 7 + sutun
                val gun = sira - ilkGunSutunu + 1
                val hucre = RemoteViews(context.packageName, R.layout.takvim_hucre)
                if (gun in 1..gunSayisi) {
                    hucre.setTextViewText(R.id.takvim_hucre_yazi, gun.toString())
                    if (buAy && gun == bugun.dayOfMonth) {
                        hucre.setInt(
                            R.id.takvim_hucre_yazi,
                            "setBackgroundResource",
                            R.drawable.takvim_bugun,
                        )
                        hucre.setTextColor(R.id.takvim_hucre_yazi, 0xFFFFFFFF.toInt())
                    }
                } else {
                    hucre.setTextViewText(R.id.takvim_hucre_yazi, "")
                }
                satirGorunum.addView(R.id.takvim_satir, hucre)
            }
            govde.addView(R.id.takvim_gunler, satirGorunum)
        }

        val ac = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val bildirim = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setContentTitle("${AYLAR[ay.monthValue - 1]} ${ay.year}")
            .setContentText("${bugun.dayOfMonth} ${AYLAR[bugun.monthValue - 1]}")
            // Kapalı hâlde başlık, açılınca takvim. Kapalı hâl için ayrı bir
            // düzen çizmiyoruz: sistemin kendi satırı zaten yeterli.
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setCustomBigContentView(govde)
            .setContentIntent(ac)
            .setOngoing(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, bildirim)
        }
    }

    private fun yayin(context: Context, action: String): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, TakvimBildirimAlicisi::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun kanalKur(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        // Düşük önem: ses yok, titreşim yok, ekranda belirme yok. Takvim
        // bir bildirim değil, orada duran bir şey.
        val kanal = NotificationChannel(
            CHANNEL_ID,
            "Takvim",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Bildirim çubuğunda duran ay takvimi"
            setShowBadge(false)
        }
        manager.createNotificationChannel(kanal)
    }

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
