package com.ahmety.uygulama.launcher

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ahmety.uygulama.core.designsystem.MerkezTheme

/**
 * Ana ekran (launcher) etkinliği. Sistemde varsayılan başlatıcı olarak
 * seçilirse, ev tuşu bu ekranı açar. Uygulamanın kendi paneli (Bugün,
 * Görevler…) ayrı [com.ahmety.uygulama.MainActivity]'de kalmaya devam eder;
 * launcher ondan bağımsız, hafif bir ana ekran.
 *
 * Pencere duvar kâğıdını gösterdiği için içerik doğrudan duvar kâğıdının
 * üstüne çizilir. Metinler okunur kalsın diye koyu tema zorlanıyor.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MerkezTheme(darkTheme = true) {
                LauncherRoot()
            }
        }
    }
}
