package com.ahmety.uygulama.feature.vocab

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import javax.inject.Inject

data class LevelTestUiState(
    val word: String = "",
    val rank: Int = 0,
    val total: Int = 0,
    val estimate: LevelEstimate = LevelEstimate(0, 0, 0, "—", 0),
    val finished: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class LevelTestViewModel @Inject constructor(
    private val store: LevelTestStore,
) : ViewModel() {

    private val _state = MutableStateFlow(LevelTestUiState())
    val state: StateFlow<LevelTestUiState> = _state.asStateFlow()

    private var words: List<String> = emptyList()

    init {
        viewModelScope.launch {
            words = withContext(Dispatchers.IO) { store.words() }
            refresh()
        }
    }

    fun answer(known: Boolean) {
        store.answer(known)
        refresh()
    }

    fun undo() {
        store.undo()
        refresh()
    }

    fun reset() {
        store.reset()
        refresh()
    }

    private fun refresh() {
        val answers = store.answers
        val index = answers.length
        _state.value = LevelTestUiState(
            word = words.getOrNull(index).orEmpty(),
            rank = index + 1,
            total = words.size,
            estimate = estimateLevel(answers, words.size.coerceAtLeast(1)),
            finished = words.isNotEmpty() && index >= words.size,
            loaded = words.isNotEmpty(),
        )
    }
}

/**
 * Seviye tespit sınavı.
 *
 * Kelimeler en sık kullanılandan başlayarak sırayla geliyor; her kelime için
 * tek soru var: biliyor musun. Anlam gösterilmiyor — sınavın amacı öğretmek
 * değil, nerede durduğunu ölçmek. İstediğin yerde bırakabilirsin, kaldığın
 * yerden devam eder.
 */
@Composable
fun LevelTestRoute(
    modifier: Modifier = Modifier,
    viewModel: LevelTestViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmReset by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Seviye tespiti",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = "${state.rank} / ${state.total}  ·  tahmini ${state.estimate.estimatedVocabulary} " +
                "kelime  ·  ${state.estimate.level}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )

        LinearProgressIndicator(
            progress = {
                if (state.total == 0) 0f else state.estimate.answered.toFloat() / state.total
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            when {
                !state.loaded -> Text("Yükleniyor…")

                state.finished -> Text(
                    text = "Liste bitti. ${state.estimate.known} kelime biliyorsun.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                )

                else -> Text(
                    text = state.word,
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        if (!state.finished) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = { viewModel.answer(false) },
                    modifier = Modifier.weight(1f),
                ) { Text("Bilmiyorum") }
                Button(
                    onClick = { viewModel.answer(true) },
                    modifier = Modifier.weight(1f),
                ) { Text("Biliyorum") }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 4.dp),
        ) {
            TextButton(
                enabled = state.estimate.answered > 0,
                onClick = viewModel::undo,
            ) { Text("Geri al") }
            TextButton(
                enabled = state.estimate.answered > 0,
                onClick = { confirmReset = true },
            ) { Text("Baştan başla") }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Sınavı sıfırla") },
            text = { Text("Verdiğin ${state.estimate.answered} cevap silinecek.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        viewModel.reset()
                    },
                ) { Text("Sıfırla") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Vazgeç") }
            },
        )
    }
}
