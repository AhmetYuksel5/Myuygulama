package com.ahmety.uygulama.feature.subtitles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

/**
 * OpenSubtitles erişim bilgileri.
 *
 * Anahtar opensubtitles.com'da ücretsiz hesap açıp "Consumers" sayfasından
 * alınıyor. Kullanıcı adı/parola isteğe bağlı ama girilmesi iyi: girilmezse
 * günlük indirme hakkı çok düşük kalıyor.
 */
@Composable
fun SubtitleSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { SubtitleSettings(context) }

    var apiKey by remember { mutableStateOf("") }
    var username by remember { mutableStateOf(settings.username) }
    var password by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(settings.maskedKey()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Altyazı (OpenSubtitles)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "opensubtitles.com'da ücretsiz hesap aç, Consumers sayfasından bir API " +
                "anahtarı üret ve buraya yapıştır. Anahtar yalnızca bu telefonda durur.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        if (saved.isNotBlank()) {
            Text(
                text = "Kayıtlı anahtar: $saved",
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("API anahtarı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = username,
            onValueChange = { username = it },
            label = { Text("Kullanıcı adı (isteğe bağlı)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Parola (isteğe bağlı)") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (apiKey.isNotBlank()) settings.apiKey = apiKey
                    settings.username = username
                    if (password.isNotBlank()) settings.password = password
                    apiKey = ""
                    password = ""
                    saved = settings.maskedKey()
                },
            ) { Text("Kaydet") }
            TextButton(
                onClick = {
                    settings.clear()
                    username = ""
                    saved = ""
                },
            ) { Text("Sil") }
        }
    }
}
