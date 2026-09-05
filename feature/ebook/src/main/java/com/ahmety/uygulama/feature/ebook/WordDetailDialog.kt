package com.ahmety.uygulama.feature.ebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.core.ai.WordInfo

/** Okurken açılan ayrıntı kutusunun durumu. */
data class WordDetail(
    val word: String,
    /** Kelimenin geçtiği cümle; örnek çoğaltırken aynı bağlam gidiyor. */
    val context: String = "",
    val info: WordInfo? = null,
    val busy: Boolean = false,
    val error: String = "",
)

/**
 * Kelimenin tamamı: karşılık, tanım, örnekler, kök, aile, eş ve karşıt
 * anlamlılar.
 *
 * Destedeki kartın okurken açılan hâli. Kartın kendisine gitmek okumayı
 * bölüyor ve kelime henüz işaretlenmemişse destede bir kart da yok;
 * buradaki kutu ikisini de gerektirmiyor.
 *
 * Deste kartının aynısı değil — orada tekrar programı, görsel ve soru
 * sorma da var. Burada yalnız okurken lazım olan kısmı duruyor.
 */
@Composable
fun WordDetailDialog(
    detail: WordDetail,
    onMoreExamples: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.word) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val info = detail.info
                when {
                    detail.busy && info == null -> {
                        CircularProgressIndicator()
                        Text("Kelime kartı hazırlanıyor…")
                    }

                    detail.error.isNotBlank() && info == null -> Text(
                        text = detail.error,
                        color = MaterialTheme.colorScheme.error,
                    )

                    info != null -> {
                        if (info.meaning.isNotBlank()) {
                            Text(
                                text = info.meaning,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (info.definition.isNotBlank()) {
                            Text(
                                text = info.definition,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (info.reading.isNotBlank()) {
                            Section("Okunuş", info.reading)
                        }

                        if (info.examples.isNotEmpty()) {
                            HorizontalDivider()
                            info.examples.forEachIndexed { index, example ->
                                Text(
                                    text = "${index + 1}. $example",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            // Destedeki kartla aynı: üç örnek çoğu zaman
                            // yetiyor, yetmediğinde üç tane daha geliyor.
                            TextButton(
                                enabled = !detail.busy,
                                onClick = onMoreExamples,
                            ) {
                                Text(if (detail.busy) "Getiriliyor…" else "+ örnek")
                            }
                        }

                        if (info.root.isNotBlank()) Section("Kök", info.root)
                        if (info.family.isNotEmpty()) {
                            Section("Aile", info.family.joinToString(" · "))
                        }
                        if (info.synonyms.isNotEmpty()) {
                            Section("Eş anlamlı", info.synonyms.joinToString(" · "))
                        }
                        if (info.antonyms.isNotEmpty()) {
                            Section("Karşıt", info.antonyms.joinToString(" · "))
                        }
                        if (info.related.isNotEmpty()) {
                            Section("İlgili", info.related.joinToString(" · "))
                        }
                        if (info.confusions.isNotEmpty()) {
                            Section("Karıştırılan", info.confusions.joinToString(" · "))
                        }
                        info.collocations.forEach { group ->
                            Section(group.pattern, group.words.joinToString(" · "))
                        }

                        if (detail.error.isNotBlank()) {
                            Text(
                                text = detail.error,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun Section(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
