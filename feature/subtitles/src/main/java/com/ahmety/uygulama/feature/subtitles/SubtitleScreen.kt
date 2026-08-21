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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontStyle
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

        // Öğrenilen dil: altyazının hangi dilde indirileceği. Türkçe tarafı
        // sabit, karşılaştırmak için o duruyor.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SubtitleLanguage.entries.forEach { option ->
                FilterChip(
                    selected = state.language == option,
                    onClick = { viewModel.onLanguageChange(option) },
                    enabled = !state.busy,
                    label = { Text(option.label) },
                )
            }
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

        // Zorluk eşiği. Altyazı indirildikten sonra da oynatılabiliyor:
        // kaydırıp bırakınca liste yeni eşikle yeniden kuruluyor, yeniden
        // indirme yok.
        Column {
            Text(
                text = "Zorluk eşiği: ${state.difficulty}",
                style = MaterialTheme.typography.bodyMedium,
            )
            Slider(
                value = state.difficulty.toFloat(),
                onValueChange = { viewModel.onDifficultyChange(it.toInt()) },
                onValueChangeFinished = viewModel::applyDifficulty,
                valueRange = 0f..100f,
                enabled = !state.busy,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = state.configured && state.query.isNotBlank() && !state.busy,
                onClick = viewModel::prepare,
            ) { Text(if (state.busy) "Hazırlanıyor…" else "Altyazıları getir") }
            val chosen = state.picks.count { it.text in state.chosen }
            if (chosen > 0) {
                TextButton(enabled = !state.busy, onClick = viewModel::save) {
                    Text("$chosen maddeyi ekle")
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
                        text = "${state.language.label}: ${pair.english.release}",
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
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            items(state.picks, key = { it.text }) { pick ->
                PickRow(
                    pick = pick,
                    checked = pick.text in state.chosen,
                    onToggle = { viewModel.toggle(pick) },
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
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

/**
 * Listedeki bir madde: kelime ya da zor cümle.
 *
 * İşaret kutusu şart: "hepsini ekle"den başka seçenek olmayınca istemediğin
 * kelimeyi çıkarmanın yolu kalmıyordu. Hepsi seçili başlıyor, çıkarmak sana
 * kalıyor.
 */
@Composable
private fun PickRow(
    pick: SubtitlePick,
    checked: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Checkbox(checked = checked, onCheckedChange = { onToggle() })
        Column(modifier = Modifier.padding(top = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pick.text,
                    style = if (pick.sentence) {
                        MaterialTheme.typography.bodyMedium
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
                    fontWeight = if (pick.sentence) FontWeight.Normal else FontWeight.SemiBold,
                    fontStyle = if (pick.sentence) FontStyle.Italic else FontStyle.Normal,
                    modifier = Modifier.weight(1f, fill = false).padding(end = 8.dp),
                )
                // Zorluk puanı görünsün: eşiği neye göre kaydıracağını
                // ancak sayıyı görerek anlarsın.
                Text(
                    text = "${pick.difficulty}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                if (!pick.sentence && pick.count > 1) {
                    Text(
                        text = "  ${pick.count}×",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!pick.sentence && pick.context.isNotBlank()) {
                Text(
                    text = pick.context,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
