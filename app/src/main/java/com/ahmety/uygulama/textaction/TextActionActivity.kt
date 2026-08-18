package com.ahmety.uygulama.textaction

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.designsystem.MerkezTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Başka bir uygulamada metin seçince açılan küçük kutu.
 *
 * İki yoldan geliyor:
 *  - metin seçim menüsündeki "Uygulama" (ACTION_PROCESS_TEXT),
 *  - paylaş menüsündeki "Uygulama" (ACTION_SEND) — bazı uygulamalar (X gibi)
 *    kendi seçim menüsünü koyduğu için seçim menüsü orada çıkmıyor.
 *
 * Metni yapay zekâya kendi ölçütlerimizle sorup sonucu gösteriyor; kullanıcı
 * isterse bilinmeyen kelimelere, isterse notlara ekliyor.
 */
@AndroidEntryPoint
class TextActionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val selected = selectedText(intent)
        if (selected.isNullOrBlank()) {
            finish()
            return
        }
        setContent {
            MerkezTheme {
                TextActionDialog(
                    selected = selected,
                    onClose = { finish() },
                )
            }
        }
    }

    private fun selectedText(intent: Intent?): String? {
        intent ?: return null
        return when (intent.action) {
            Intent.ACTION_PROCESS_TEXT ->
                intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()

            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            else -> null
        }
    }
}

@Composable
private fun TextActionDialog(
    selected: String,
    onClose: () -> Unit,
    viewModel: TextActionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(selected) { viewModel.start(selected) }

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 4.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            Text(
                text = "“${state.text}”",
                style = MaterialTheme.typography.titleMedium,
                fontStyle = FontStyle.Italic,
            )

            if (state.loading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Anlamı getiriliyor…",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(start = 12.dp),
                    )
                }
            }

            state.error?.let { error ->
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 14.dp),
                )
                if (state.aiConfigured) {
                    TextButton(onClick = viewModel::ask) { Text("Yeniden dene") }
                }
            }

            state.info?.let { info ->
                if (info.meaning.isNotBlank()) {
                    Text(
                        text = info.meaning,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 14.dp),
                    )
                }
                if (info.definition.isNotBlank()) {
                    Text(
                        text = info.definition,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                info.examples.forEachIndexed { index, example ->
                    LabeledLine(lead = "${index + 1}.", text = example)
                }
                if (info.related.isNotEmpty()) {
                    RelatedChips(info.related)
                }
                info.phrases.forEach { phrase ->
                    LabeledLine(lead = "•", text = phrase, dim = true)
                }
            }

            state.saved?.let { saved ->
                Text(
                    text = saved,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Button(onClick = viewModel::addToVocab) { Text("Kelimelere") }
                OutlinedButton(onClick = viewModel::addToNotes) { Text("Notlara") }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (!state.loading && state.info == null && state.aiConfigured) {
                    TextButton(onClick = viewModel::ask) { Text("Anlamını getir") }
                }
                TextButton(onClick = onClose) { Text("Kapat") }
            }
        }
    }
}

@Composable
private fun LabeledLine(lead: String, text: String, dim: Boolean = false) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = lead,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(22.dp),
        )
        Text(
            text = text,
            style = if (dim) {
                MaterialTheme.typography.bodySmall
            } else {
                MaterialTheme.typography.bodyMedium
            },
            color = if (dim) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RelatedChips(words: List<String>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        words.forEach { related ->
            Box(
                modifier = Modifier
                    .background(CHIP_BLUE, RoundedCornerShape(50))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(
                    text = related,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                )
            }
        }
    }
}

private val CHIP_BLUE = Color(0xFF1565C0)
