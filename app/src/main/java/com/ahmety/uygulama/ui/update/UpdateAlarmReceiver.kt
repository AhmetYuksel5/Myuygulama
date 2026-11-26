package com.ahmety.uygulama.ui.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 6 saatte bir güncelleme denetimi. Merkez'in ekranı hiç açılmadan yalnız
 * imleç, kısayollar ya da ajan kullanıldığında da yeni sürüm fark edilsin.
 * Yeni sürüm varsa ya sormadan kuruluyor ya da bildirim düşüyor.
 */
class UpdateAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UpdateChecker(context.applicationContext).updateInBackground()
    }

    companion object {
        /** Uygulama süreci her başladığında; aynı alarm üst üste kurulmuyor, yenileniyor. */
        fun schedule(context: Context) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, UpdateAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            runCatching {
                manager.setInexactRepeating(
                    AlarmManager.RTC,
                    System.currentTimeMillis() + FIRST_DELAY_MS,
                    EVERY_MS,
                    pending,
                )
            }
        }

        private const val FIRST_DELAY_MS = 15 * 60_000L
        private const val EVERY_MS = 6 * 3_600_000L
    }
}
