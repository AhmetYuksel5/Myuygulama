package com.ahmety.uygulama.ui.sync

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * İki telefon arasındaki senkronun kurulum ekranı.
 *
 * Taşıyıcı şimdilik "paylaşılan klasör": Syncthing, FolderSync ya da benzeri
 * bir araçla eşlediğin herhangi bir klasör olabilir. Google Drive'ı uygulama
 * içinden kullanan taşıyıcı bunun üstüne eklenecek; veri biçimi aynı kalacak.
 */
@Composable
fun SyncScreen(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var keyInput by remember { mutableStateOf("") }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri -> uri?.let(viewModel::setFolder) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerkezTopBar(title = "Senkronizasyon", onBack = onBack)
        Text(
            text = "Her cihaz kendi değişiklik günlüğünü paylaşılan klasöre yazar, " +
                "karşı tarafınkini okur. Veritabanı dosyası kopyalanmaz — böylece " +
                "iki telefonda da çalışsan hiçbir girdi kaybolmaz.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("1. Paylaşılan klasör", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.folderLabel?.let { "Seçili: $it" } ?: "Henüz seçilmedi.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { folderPicker.launch(null) }) {
                        Text(if (state.folderLabel == null) "Klasör seç" else "Değiştir")
                    }
                    if (state.folderLabel != null) {
                        OutlinedButton(onClick = viewModel::clearFolder) { Text("Kaldır") }
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("2. Kurtarma anahtarı", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Klasöre yazılan her şey bu anahtarla şifrelenir. " +
                        "Birinci telefonda oluştur, ikinci telefona aynısını gir.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                val key = state.recoveryKey
                if (key == null) {
                    Button(onClick = viewModel::generateKey) { Text("Anahtar oluştur") }
                } else {
                    Text(text = key, style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { context.copyToClipboard(key) }) {
                        Text("Kopyala")
                    }
                }

                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("Diğer cihazın anahtarını yapıştır") },
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    enabled = keyInput.isNotBlank(),
                    onClick = {
                        viewModel.importKey(keyInput)
                        keyInput = ""
                    },
                ) {
                    Text("Anahtarı kullan")
                }
            }
        }

        Button(
            onClick = viewModel::syncNow,
            enabled = !state.running,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.running) "Senkronlanıyor…" else "Şimdi senkronla")
        }

        state.message?.let { message ->
            Text(text = message, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Context.copyToClipboard(value: String) {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    manager.setPrimaryClip(ClipData.newPlainText("Merkez kurtarma anahtarı", value))
}
