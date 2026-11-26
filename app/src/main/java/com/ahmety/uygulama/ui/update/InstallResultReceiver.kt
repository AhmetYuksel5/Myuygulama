package com.ahmety.uygulama.ui.update

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
 * Kurulum oturumunun sonucu. Android onay isterse (ilk güncelleme ya da
 * telefon sorarsa): düğmeye basılarak başladıysa onay ekranı hemen açılıyor;
 * arka plan güncellemesiyse bildirim düşüyor, dokununca açılıyor.
 */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val background = intent.getBooleanExtra(EXTRA_BACKGROUND, false)
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                } ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (background) {
                    notify(context, confirm)
                } else {
                    runCatching { context.startActivity(confirm) }.onFailure { notify(context, confirm) }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            else -> if (!background) {
                Toast.makeText(context, "Güncelleme kurulamadı", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun notify(context: Context, confirm: Intent) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Güncelleme", NotificationManager.IMPORTANCE_DEFAULT),
        )
        val open = PendingIntent.getActivity(
            context,
            0,
            confirm,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Merkez güncellemesi hazır")
            .setContentText("Kurmak için dokun")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        /**
         * Kurulum oturumu açılamadığında ya da kurma izni yokken: Merkez'i açan
         * bildirim; açılınca güncelleme penceresi çıkıyor.
         */
        fun notifyAvailable(context: Context, versionName: String) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Güncelleme", NotificationManager.IMPORTANCE_DEFAULT),
            )
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
            val open = PendingIntent.getActivity(
                context,
                1,
                launch,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val notification = Notification.Builder(context, CHANNEL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("Merkez güncellemesi var: $versionName")
                .setContentText("Kurmak için dokun")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            runCatching { manager.notify(NOTIFICATION_ID, notification) }
        }

        const val EXTRA_BACKGROUND = "arkaplan"
        private const val CHANNEL = "guncelleme"
        private const val NOTIFICATION_ID = 8
    }
}
