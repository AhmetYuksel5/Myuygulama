package com.ahmety.uygulama

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ahmety.uygulama.core.designsystem.MerkezTheme
import com.ahmety.uygulama.ui.MerkezApp
import com.ahmety.uygulama.ui.NavRequestBus
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        setContent {
            MerkezTheme {
                MerkezApp()
            }
        }
    }

    // launchMode=singleTask: widget'tan gelen intent uygulama açıkken buraya düşer.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent?.getStringExtra("merkez_hedef")?.let(NavRequestBus::request)
    }
}
