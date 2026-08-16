package com.ahmety.uygulama.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.Entry
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SaveArticleUiState(
    val saving: Boolean = false,
    val message: String? = null,
    val savedEntryId: Long? = null,
)

@HiltViewModel
class SaveArticleViewModel @Inject constructor(
    private val readerRepository: ReaderRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaveArticleUiState())
    val uiState: StateFlow<SaveArticleUiState> = _uiState.asStateFlow()

    fun save(url: String) {
        if (_uiState.value.saving) return
        _uiState.value = SaveArticleUiState(saving = true)
        viewModelScope.launch {
            when (val result = readerRepository.saveFromUrl(url)) {
                is SaveArticleResult.Saved -> _uiState.value = SaveArticleUiState(
                    message = "Kaydedildi: ${result.title}",
                    savedEntryId = result.entryId,
                )

                is SaveArticleResult.Failed -> _uiState.value = SaveArticleUiState(
                    message = result.reason,
                )
            }
        }
    }

    fun reset() {
        _uiState.value = SaveArticleUiState()
    }
}

/** URL yapıştırıp makale kaydetme kutusu. */
@Composable
fun SaveArticleDialog(
    onDismiss: () -> Unit,
    onSaved: (Long) -> Unit,
    viewModel: SaveArticleViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var url by remember { mutableStateOf("") }

    LaunchedEffect(state.savedEntryId) {
        state.savedEntryId?.let {
            viewModel.reset()
            onSaved(it)
        }
    }

    AlertDialog(
        onDismissRequest = {
            if (!state.saving) {
                viewModel.reset()
                onDismiss()
            }
        },
        title = { Text("Makale kaydet") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Adresi yapıştır; sayfa indirilir, okunabilir hâle " +
                        "getirilir ve çevrimdışı saklanır.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("https://…") },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.saving) {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 4.dp))
                }
                state.message?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = url.isNotBlank() && !state.saving,
                onClick = { viewModel.save(url) },
            ) {
                Text("Kaydet")
            }
        },
        dismissButton = {
            TextButton(enabled = !state.saving, onClick = {
                viewModel.reset()
                onDismiss()
            }) {
                Text("Kapat")
            }
        },
    )
}

@HiltViewModel
class ArticleViewModel @Inject constructor(
    private val entryRepository: EntryRepository,
) : ViewModel() {

    private val _entry = MutableStateFlow<Entry?>(null)
    val entry: StateFlow<Entry?> = _entry.asStateFlow()

    fun load(id: Long) {
        viewModelScope.launch { _entry.value = entryRepository.getById(id) }
    }
}

/** Kaydedilmiş makalenin okuma ekranı: sakin tipografi, dikkat dağıtan yok. */
@Composable
fun ArticleRoute(
    entryId: Long,
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel = hiltViewModel(),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    LaunchedEffect(entryId) { viewModel.load(entryId) }

    val article = entry ?: return

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = article.title,
            style = MaterialTheme.typography.headlineMedium,
        )
        article.source?.let { source ->
            Text(
                text = source.substringAfter("://").substringBefore('/'),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        article.body.split("\n\n").forEach { paragraph ->
            Text(
                text = paragraph,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    lineHeight = 27.sp,
                ),
            )
        }
    }
}
