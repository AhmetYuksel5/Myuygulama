package com.yesiltemizlik.ofis

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.widget.Toast

/**
 * Kurulum oturumunun sonucu. Sistem onay isterse (ilk güncelleme ya da MIUI
 * sorarsa): kullanıcı düğmeye bastıysa onay ekranı hemen açılır; arka plan
 * güncellemesiyse bildirim düşer, dokununca açılır.
 */
class KurulumAlicisi : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val onay = if (Build.VERSION.SDK_INT >= 33) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                onay.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.getBooleanExtra(EK_ARKAPLAN, false)) {
                    bildir(context, onay)
                } else {
                    try {
                        context.startActivity(onay)
                    } catch (_: Exception) {
                        bildir(context, onay)
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {}
            else -> if (!intent.getBooleanExtra(EK_ARKAPLAN, false)) {
                Toast.makeText(context, "Güncelleme kurulamadı", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun bildir(c: Context, onay: Intent) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        nm.createNotificationChannel(NotificationChannel(KANAL, "Güncelleme", NotificationManager.IMPORTANCE_DEFAULT))
        val pi = PendingIntent.getActivity(c, 0, onay, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = Notification.Builder(c, KANAL)
            .setSmallIcon(R.drawable.ic_bildirim)
            .setContentTitle("My Asistan güncellemesi hazır")
            .setContentText("Kurmak için dokunun")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        try {
            nm.notify(BILDIRIM_ID, n)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val EK_ARKAPLAN = "arkaplan"
        private const val KANAL = "guncelleme"
        private const val BILDIRIM_ID = 8
    }
}
