package com.yesiltemizlik.ofis

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Acik = lightColorScheme(
    primary = Color(0xFF1F6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6EEDC),
    onPrimaryContainer = Color(0xFF0B2E17),
    secondary = Color(0xFF3D6B8C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDCEAF5),
    onSecondaryContainer = Color(0xFF0F2A3D),
    surface = Color(0xFFFBFCFA),
    surfaceVariant = Color(0xFFEEF2EE),
    background = Color(0xFFF4F6F3),
)

private val Koyu = darkColorScheme(
    primary = Color(0xFF7FD19A),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF1E4D2E),
    onPrimaryContainer = Color(0xFFD6EEDC),
    secondary = Color(0xFF9CC4E4),
    onSecondary = Color(0xFF0F2A3D),
    secondaryContainer = Color(0xFF23435C),
    onSecondaryContainer = Color(0xFFDCEAF5),
    surface = Color(0xFF1A1D1B),
    surfaceVariant = Color(0xFF2A2F2C),
    background = Color(0xFF121413),
)

@Composable
fun OfisTema(icerik: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Koyu else Acik, content = icerik)
}
