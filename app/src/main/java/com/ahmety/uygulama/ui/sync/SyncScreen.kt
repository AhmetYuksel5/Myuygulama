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
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.DisposableEffect
import com.ahmety.uygulama.core.database.sync.SyncMode
import androidx.compose.material3.MaterialTheme
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import com.ahmety.uygulama.core.designsystem.longPressHint
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
 * İki yol var. **Aynı ağ**: telefonlar birbirini yerel ağda bulup dosyaları
 * doğrudan veriyor — bulut yok, hesap yok, üçüncü uygulama yok.
 * **Paylaşılan klasör**: bir klasör seçilir ve o klasörü iki telefon
 * arasında taşımak başka bir aracın işidir (Syncthing, FolderSync). Veri
 * biçimi ikisinde de aynı; yolu değiştirmek veriyi bozmuyor.
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

    // Duyuru ve sunucu yalnızca bu ekran açıkken çalışıyor.
    DisposableEffect(Unit) {
        viewModel.startPeering()
        onDispose { viewModel.stopPeering() }
    }

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

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "1. Yol",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.longPressHint(
                        title = "Senkronizasyon yolu",
                        hint = when (state.mode) {
                            SyncMode.LAN -> "İki telefon aynı Wi-Fi'de ve bu ekranda açıkken birbirini bulur; aynı kurtarma anahtarıyla şifreli aktarır."
                            SyncMode.GITHUB -> "Mobil veriyle de çalışır; giden tüm veriler kurtarma anahtarıyla şifrelenir."
                            SyncMode.FOLDER -> "Seçilen klasörü iki telefon arasında taşımak için Syncthing gibi bir araç gerekir."
                        },
                    ),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.mode == SyncMode.LAN,
                        onClick = { viewModel.setMode(SyncMode.LAN) },
                        label = { Text("Aynı ağ") },
                    )
                    FilterChip(
                        selected = state.mode == SyncMode.GITHUB,
                        onClick = { viewModel.setMode(SyncMode.GITHUB) },
                        label = { Text("GitHub") },
                    )
                    FilterChip(
                        selected = state.mode == SyncMode.FOLDER,
                        onClick = { viewModel.setMode(SyncMode.FOLDER) },
                        label = { Text("Klasör") },
                    )
                }
            }
        }

        if (state.mode == SyncMode.LAN) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("2. Cihazlar", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Bu cihaz: ${state.deviceName}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = when {
                            !state.listening -> "Ağ dinlenemiyor."
                            state.peers.isEmpty() -> "Cihaz bulunamadı."
                            else -> "Bulundu: " + state.peers.joinToString(", ")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (state.mode == SyncMode.GITHUB) {
            var repository by remember(state.repository) { mutableStateOf(state.repository) }
            var token by remember { mutableStateOf("") }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "2. GitHub deposu",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.longPressHint(
                            title = "GitHub deposu",
                            hint = "Özel bir GitHub deposu ve içerik yazma yetkili erişim anahtarı gerekir.",
                        ),
                    )
                    OutlinedTextField(
                        value = repository,
                        onValueChange = { repository = it },
                        singleLine = true,
                        label = { Text("kullanıcı/depo") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (state.maskedToken.isNotBlank()) {
                        Text(
                            text = "Kayıtlı anahtar: ${state.maskedToken}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        singleLine = true,
                        label = {
                            Text(
                                if (state.maskedToken.isBlank()) "Erişim anahtarı" else "Yeni anahtar",
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                viewModel.setGitHub(repository, token)
                                token = ""
                            },
                        ) { Text("Kaydet") }
                        if (state.maskedToken.isNotBlank()) {
                            OutlinedButton(onClick = viewModel::clearGitHub) { Text("Kaldır") }
                        }
                    }
                }
            }
        }

        if (state.mode == SyncMode.FOLDER) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("2. Paylaşılan klasör", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = state.folderLabel?.let { "Seçili: $it" } ?: "Seçilmedi.",
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
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "3. Kurtarma anahtarı",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.longPressHint(
                        title = "Kurtarma anahtarı",
                        hint = "Dışarı çıkan her şey bu anahtarla şifrelenir. Birinci telefonda oluştur, ikinci telefona aynısını gir.",
                    ),
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
