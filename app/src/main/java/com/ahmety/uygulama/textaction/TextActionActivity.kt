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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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

    /** Ekranda duran metin. Yeni bir seçim gelirse burası değişiyor. */
    private var selected by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = selectedText(intent)
        if (text.isNullOrBlank()) {
            finish()
            return
        }
        selected = text
        setContent {
            MerkezTheme {
                // Kapatma düğmesi yok: kutu kayan pencere, dışına dokununca
                // sistem zaten kapatıyor.
                TextActionDialog(selected = selected)
            }
        }
    }

    /**
     * Etkinlik `singleTask`; kutu açıkken başka bir uygulamadan ikinci bir
     * metin gönderilirse `onCreate` çalışmaz. Bunu karşılamazsak kutuda eski
     * metin durur ve kaydedilen de o olurdu.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val text = selectedText(intent)
        if (!text.isNullOrBlank()) selected = text
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
                if (info.root.isNotBlank()) {
                    LabeledLine(lead = "Kök", text = info.root, dim = true)
                }
                if (info.family.isNotEmpty()) {
                    LabeledLine(lead = "Aile", text = info.family.joinToString(" · "), dim = true)
                }
                if (info.synonyms.isNotEmpty() ||
                    info.antonyms.isNotEmpty() ||
                    info.related.isNotEmpty()
                ) {
                    WordChips(
                        synonyms = info.synonyms,
                        antonyms = info.antonyms,
                        related = info.related,
                    )
                }
                info.confusions.forEach { line ->
                    LabeledLine(lead = "Karıştırma", text = line, dim = true)
                }
                info.collocations.forEach { group ->
                    LabeledLine(lead = group.pattern, text = group.words.joinToString(" · "), dim = true)
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

            // Sorulan sorunun cevabı: kutuda duruyor ve "Kelimelere"
            // basıldığında kartın altına da yazılıyor.
            if (state.answer.isNotBlank()) {
                Text(
                    text = state.answer,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            if (state.asking) {
                OutlinedTextField(
                    value = state.question,
                    onValueChange = viewModel::setQuestion,
                    label = { Text("Sorun") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    Button(
                        enabled = !state.answering && state.question.isNotBlank(),
                        onClick = viewModel::sendQuestion,
                    ) { Text("Gönder") }
                    if (state.answering) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp))
                    }
                }
            }

            // Tek satır, iki düğme. "Kapat" yok: kutunun dışına dokunmak
            // zaten kapatıyor, ikinci bir yol gereksiz yer kaplıyordu.
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 18.dp),
            ) {
                Button(onClick = viewModel::addToVocab) { Text("Kelimelere") }
                if (state.aiConfigured) {
                    TextButton(onClick = viewModel::toggleQuestion) {
                        Text(if (state.asking) "Soruyu kapat" else "Soru sor")
                    }
                }
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
            // Eşdizim kalıbının adı ("fiil +") sayıdan geniş.
            modifier = Modifier.width(if (dim) 74.dp else 22.dp),
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
private fun WordChips(
    synonyms: List<String>,
    antonyms: List<String>,
    related: List<String>,
) {
    val neutral = MaterialTheme.colorScheme.surfaceVariant
    val onNeutral = MaterialTheme.colorScheme.onSurfaceVariant
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        synonyms.forEach { Chip(it, CHIP_BLUE, Color.White) }
        antonyms.forEach { Chip(it, CHIP_RED, Color.White) }
        related.forEach { Chip(it, neutral, onNeutral) }
    }
}

@Composable
private fun Chip(text: String, background: Color, content: Color) {
    Box(
        modifier = Modifier
            .background(background, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium, color = content)
    }
}

private val CHIP_BLUE = Color(0xFF1565C0)
private val CHIP_RED = Color(0xFFB3261E)
