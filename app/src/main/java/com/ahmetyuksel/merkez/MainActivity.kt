package com.ahmetyuksel.merkez

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ahmetyuksel.merkez.core.designsystem.MerkezTheme
import com.ahmetyuksel.merkez.ui.MerkezApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            MerkezTheme {
                MerkezApp()
            }
        }
    }
}
