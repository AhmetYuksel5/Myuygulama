package com.ahmety.uygulama.feature.gestures

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

/**
 * Sesli yazma kaydı sürerken açık duran ön plan hizmeti.
 *
 * Kaydı kendisi yapmıyor; yalnız varlığı yetiyor. Android, ekranda
 * görünmeyen bir uygulamanın mikrofonu dinlemesine ancak böyle bildirimli
 * bir hizmet varken izin veriyor. Yoksa kayıt hata vermeden sessiz çıkıyor.
 */
class VoiceRecordService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sesli yazma", NotificationManager.IMPORTANCE_LOW),
        )
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Dinliyor")
            .setContentText("Bitirmek için mikrofon düğmesine dokun.")
            .setOngoing(true)
            .build()
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
            } else {
                startForeground(ID, notification)
            }
        }.onFailure { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "sesli_yazma"
        private const val ID = 4107

        fun start(context: Context) {
            // Başlatılamazsa kayıt yine denenir; ses gelmezse kullanıcıya
            // söyleniyor.
            runCatching {
                context.startForegroundService(Intent(context, VoiceRecordService::class.java))
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, VoiceRecordService::class.java)) }
        }
    }
}
