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
                Text("1. Yol", style = MaterialTheme.typography.titleMedium)
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
                Text(
                    text = when (state.mode) {
                        SyncMode.LAN ->
                            "İki telefon da aynı Wi-Fi'de ve bu ekranda açık olsun; " +
                                "birbirlerini kendileri buluyor. Eşleştirme yok — aynı " +
                                "kurtarma anahtarını taşımak eşleşmek demek. En hızlısı " +
                                "ve internete hiç çıkmıyor."

                        SyncMode.GITHUB ->
                            "Mobil veriyle de çalışır ve telefonların aynı anda açık " +
                                "olması gerekmez; her telefon canı istediğinde " +
                                "senkronlanır. Giden her şey şifreli, GitHub da " +
                                "anlamsız baytlar görüyor."

                        SyncMode.FOLDER ->
                            "Bir klasör seç. Dikkat: uygulama o klasörü iki telefon " +
                                "arasında taşımıyor, bunu Syncthing gibi bir araç " +
                                "yapmalı. Google Drive Android'in klasör seçicisinde " +
                                "çıkmadığı için Drive klasörü seçilemiyor."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
                            state.peers.isEmpty() -> "Henüz başka cihaz görünmüyor."
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
                    Text("2. GitHub deposu", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "GitHub'da özel bir depo aç (örneğin merkez-senkron) ve " +
                            "içerik yazma yetkisi olan bir erişim anahtarı üret. " +
                            "Deponun özel olması şart.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                if (state.maskedToken.isBlank()) {
                                    "Erişim anahtarı"
                                } else {
                                    "Yeni anahtar (boş bırakırsan aynısı kalır)"
                                },
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
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("3. Kurtarma anahtarı", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Dışarı çıkan her şey bu anahtarla şifrelenir. " +
                        "Birinci telefonda oluştur, ikinci telefona aynısını gir. " +
                        "Ağ yolunda eşleştirme de bununla oluyor: aynı anahtarı " +
                        "taşımayan cihaz görünmüyor bile.",
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
