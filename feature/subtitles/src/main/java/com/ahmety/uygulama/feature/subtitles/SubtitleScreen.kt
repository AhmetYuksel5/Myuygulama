package com.ahmety.uygulama.feature.subtitles

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Film izlemeden önce hazırlık.
 *
 * Filmin adı yazılıyor; aynı sürüme ait İngilizce ve Türkçe altyazı
 * indiriliyor, İngilizceden seviyene göre bilmediğin kelimeler çıkarılıyor.
 * Listeyi onaylayınca kelimeler destene giriyor ve filmi izlemeden önce
 * çalışabiliyorsun.
 */
@Composable
fun SubtitleRoute(
    modifier: Modifier = Modifier,
    viewModel: SubtitleViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showSettings by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Film hazırlığı",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { showSettings = true }) { Text("Ayar") }
        }

        if (!state.configured) {
            Text(
                text = "Önce sağ üstteki Ayar'dan OpenSubtitles anahtarını gir.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("Film adı") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.year,
            onValueChange = viewModel::onYearChange,
            label = { Text("Yıl (isteğe bağlı)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state.configured && state.query.isNotBlank() && !state.busy,
                onClick = viewModel::prepare,
            ) { Text(if (state.busy) "Hazırlanıyor…" else "Altyazıları getir") }
            if (state.words.isNotEmpty()) {
                TextButton(enabled = !state.busy, onClick = viewModel::save) {
                    Text("${state.words.size} kelimeyi ekle")
                }
            }
        }

        if (state.busy) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.padding(end = 10.dp))
                Text(state.step, style = MaterialTheme.typography.bodyMedium)
            }
        }

        state.message?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (state.failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.primary
                },
            )
        }

        state.pair?.let { pair ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = pair.movie + if (pair.year > 0) " (${pair.year})" else "",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = "İngilizce: ${pair.english.release}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = pair.turkish
                            ?.let { "Türkçe: ${it.release}" }
                            ?: "Türkçe altyazı bulunamadı.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (pair.turkish != null) {
                        Text(
                            text = if (ReleaseMatch.groupOf(pair.english.release) != null &&
                                ReleaseMatch.groupOf(pair.english.release) ==
                                ReleaseMatch.groupOf(pair.turkish.release)
                            ) {
                                "Aynı sürüm — zamanlama tutar."
                            } else {
                                "Farklı sürüm; zamanlama kayabilir."
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.words, key = { it.word }) { word ->
                Column {
                    Row {
                        Text(
                            text = word.word,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        Text(
                            text = "${word.count}×",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (word.context.isNotBlank()) {
                        Text(
                            text = word.context,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Altyazı ayarları") },
            text = { SubtitleSettingsScreen(modifier = Modifier.heightIn(max = 440.dp)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSettings = false
                        viewModel.refreshConfigured()
                    },
                ) { Text("Kapat") }
            },
        )
    }
}
