package com.ahmety.uygulama.ui.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Merkez'in bir özelliği kullanıldığında güncelleme denetimi (feature/gestures
 * modülündeki UpdatePing bu alıcıya sınıf adıyla yayın gönderiyor). Yeni sürüm
 * varsa ya sormadan kuruluyor ya da bildirim düşüyor.
 *
 * Adı önceki 6 saatlik alarmdan kalma: o alarm istenmedi ve kaldırıldı; adı
 * değişseydi eski kurulumlarda kalan alarm iptal edilemezdi.
 */
class UpdateAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UpdateChecker(context.applicationContext).updateInBackground()
    }

    companion object {
        /** 277'de kurulan 6 saatlik alarmı siler. */
        fun cancelOldAlarm(context: Context) {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return
            val pending = PendingIntent.getBroadcast(
                context,
                0,
                Intent(context, UpdateAlarmReceiver::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_NO_CREATE,
            ) ?: return
            runCatching { manager.cancel(pending) }
            pending.cancel()
        }
    }
}
