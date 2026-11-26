package com.ahmety.uygulama

import android.app.Application
import com.ahmety.uygulama.ui.update.UpdateAlarmReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class MerkezApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Güncelleme artık zamanla değil, uygulama kullanılırken denetleniyor.
        UpdateAlarmReceiver.cancelOldAlarm(this)
    }
}
