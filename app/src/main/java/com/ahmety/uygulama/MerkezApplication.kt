package com.ahmety.uygulama

import android.app.Application
import com.ahmety.uygulama.ui.update.UpdateAlarmReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MerkezApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Süreç erişilebilirlik servisiyle de başlıyor: uygulama ekranı hiç
        // açılmasa bile güncelleme denetimi kurulmuş oluyor.
        UpdateAlarmReceiver.schedule(this)
    }
}
