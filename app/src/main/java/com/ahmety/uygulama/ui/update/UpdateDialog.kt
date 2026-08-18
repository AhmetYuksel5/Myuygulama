package com.ahmety.uygulama.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Güncelleme penceresi: açılır açılmaz kontrol eder.
 *
 * Ayrı bir sayfaya gidip düğmeye basmak gereksiz bir adımdı; burada
 * açılışta kontrol başlıyor ve sonuç aynı pencerede görünüyor.
 */
@Composable
fun UpdateDialog(
    onDismiss: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.check() }

    val downloading = state.downloadProgress != null
    val available = state.available

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Güncelleme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Yüklü sürüm: ${state.currentVersion.ifBlank { "bilinmiyor" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when {
                    state.checking -> {
                        Text("Kontrol ediliyor…", style = MaterialTheme.typography.bodyMedium)
                        CircularProgressIndicator()
                    }

                    downloading -> {
                        Text("İndiriliyor…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { state.downloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    available != null -> {
                        Text(
                            text = "Yeni sürüm var: ${available.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (available.notes.isNotBlank()) {
                            Text(
                                text = available.notes.take(400),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    else -> Text(
                        text = state.message ?: "En güncel sürümü kullanıyorsun.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!state.canInstall) {
                    Text(
                        text = "Kurulum için \"bilinmeyen kaynaklara izin ver\" gerekiyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when {
                available != null && !downloading -> TextButton(
                    enabled = state.canInstall,
                    onClick = { viewModel.downloadAndInstall() },
                ) {
                    Text("İndir ve kur")
                }

                !state.checking && !downloading -> TextButton(onClick = { viewModel.check() }) {
                    Text("Yeniden kontrol et")
                }

                else -> Unit
            }
        },
        dismissButton = {
            if (!state.canInstall) {
                TextButton(
                    onClick = {
                        viewModel.openInstallPermissionSettings { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    },
                ) {
                    Text("İzin ver")
                }
            } else if (!downloading) {
                TextButton(onClick = onDismiss) { Text("Kapat") }
            }
        },
    )
}
