package com.ahmety.uygulama

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ahmety.uygulama.core.designsystem.MerkezTheme
import com.ahmety.uygulama.ui.MerkezApp
import com.ahmety.uygulama.ui.update.UpdateChecker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var updateChecker: UpdateChecker

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MerkezTheme {
                MerkezApp()
            }
        }
    }

    // Uygulamadan çıkılınca, sormadan kurulabiliyorsa yeni sürüm arka planda
    // iner ve kurulur. Kullanırken değil: kurulum süreci kapatıyor.
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) updateChecker.updateInBackground()
    }
}
