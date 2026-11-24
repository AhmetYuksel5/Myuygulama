package com.yesiltemizlik.ofis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * Kartı iki yoldan birden açar: doğrudan pencere (üstte gösterme izniyle) ve
 * bildirim. Pencere açılınca bildirimi kendisi siler; MIUI pencereyi
 * engellerse bildirim kalır, dokununca aynı kart açılır.
 */
object KartAcici {
    const val KANAL = "kart"
    const val BILDIRIM_ID = 7
    const val EK_BAS = "bas"
    const val EK_DENEME = "deneme"

    fun niyet(context: Context, bas: Long, deneme: Boolean = false): Intent =
        Intent(context, KartActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EK_BAS, bas)
            .putExtra(EK_DENEME, deneme)

    /** Ana ekrandaki listeden seçilen geçmiş bir aramanın kartı: bilgiler arama kaydından okunmaz, buradan gelir. */
    fun niyet(context: Context, a: Arama): Intent =
        niyet(context, a.zaman)
            .putExtra("tel", a.tel).putExtra("ad", a.ad).putExtra("sure", a.sure)
            .putExtra("yon", a.yon).putExtra("zaman", a.zaman)

    fun aramaOku(i: Intent): Arama? {
        if (!i.hasExtra("zaman")) return null
        return Arama(
            i.getStringExtra("tel") ?: "", i.getStringExtra("ad") ?: "", i.getLongExtra("sure", 0L),
            i.getStringExtra("yon") ?: "gelen", i.getLongExtra("zaman", 0L),
        )
    }

    fun ac(context: Context, bas: Long) {
        val i = niyet(context, bas)
        bildir(context, i)
        try {
            context.startActivity(i)
        } catch (_: Exception) {
        }
    }

    private fun bildir(context: Context, i: Intent) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(
            NotificationChannel(KANAL, "Görüşme kartı", NotificationManager.IMPORTANCE_HIGH)
        )
        val pi = PendingIntent.getActivity(
            context, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(context, KANAL)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("Görüşme bitti")
            .setContentText("İş görüşmesi mi, müşteri mi?")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(BILDIRIM_ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun bildirimiSil(context: Context) {
        context.getSystemService(NotificationManager::class.java)?.cancel(BILDIRIM_ID)
    }
}
