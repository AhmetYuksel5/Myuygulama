package com.ahmety.uygulama.feature.reader

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.designsystem.ColorPickerDialog
import com.ahmety.uygulama.core.designsystem.HighlightableParagraph
import com.ahmety.uygulama.core.designsystem.PendingHighlight
import com.ahmety.uygulama.core.designsystem.SelectionPreview
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
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

    /** Bu sayfada işaretli kelime/öbek (küçük harf) -> renk. */
    private val _colors = MutableStateFlow<Map<String, HighlightColor>>(emptyMap())
    val colors: StateFlow<Map<String, HighlightColor>> = _colors.asStateFlow()

    private var articleId: Long = 0L

    fun load(id: Long) {
        articleId = id
        viewModelScope.launch {
            _entry.value = entryRepository.getById(id)
            refresh()
        }
    }

    /**
     * Kelimeyi işaretler ya da rengini değiştirir.
     *
     * Kitaptaki [setHighlight] ile aynı davranış: aynı kelime başka bir
     * cümlede de işaretlenirse o cümle de saklanıyor, çünkü bir kelimenin
     * birden çok anlamı olabiliyor.
     */
    fun highlight(word: String, contextSentence: String, color: HighlightColor) {
        val trimmed = word.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            val marks = marks()
            val existing = marks.firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
            val source = HighlightRef.encode(HighlightRef.KIND_ARTICLE, articleId, color)
            val sentence = contextSentence.trim()

            if (existing == null) {
                entryRepository.createEntry(
                    type = EntryType.HIGHLIGHT,
                    title = trimmed,
                    body = sentence,
                    source = source,
                )
            } else {
                if (HighlightRef.color(existing.source) != color) {
                    entryRepository.updateSource(existing.id, source)
                }
                val known = existing.body.lineSequence().map { it.trim() }.toSet()
                if (sentence.isNotBlank() && sentence !in known) {
                    val merged = listOf(existing.body, sentence)
                        .filter { it.isNotBlank() }
                        .joinToString("\n")
                    entryRepository.updateEntry(existing.id, existing.title, merged)
                }
            }
            refresh()
        }
    }

    fun removeHighlight(word: String) {
        val trimmed = word.trim()
        viewModelScope.launch {
            marks()
                .firstOrNull { it.title.equals(trimmed, ignoreCase = true) }
                ?.let { entryRepository.deleteEntry(it.id) }
            refresh()
        }
    }

    /** Bu sayfaya ait işaretleme kayıtları. */
    private suspend fun marks(): List<Entry> = entryRepository
        .listByType(EntryType.HIGHLIGHT)
        .filter {
            HighlightRef.kind(it.source) == HighlightRef.KIND_ARTICLE &&
                HighlightRef.sourceId(it.source) == articleId
        }

    private suspend fun refresh() {
        _colors.value = marks()
            .mapNotNull { entry ->
                val color = HighlightRef.color(entry.source) ?: return@mapNotNull null
                entry.title.lowercase() to color
            }
            .toMap()
    }
}

/**
 * Kaydedilmiş sayfanın okuma ekranı: sakin tipografi, dikkat dağıtan yok.
 *
 * İşaretleme kitaptakiyle aynı: çift dokunuş kelimeyi, üçüncü dokunuş
 * cümleyi, uzun basıp sürüklemek araya giren her şeyi seçiyor. Eskiden
 * paragrafa uzun basınca paragrafın tamamı alıntılanıyordu; iki okuma
 * ekranının birbirine benzememesi için bir sebep yok.
 */
@Composable
fun ArticleRoute(
    entryId: Long,
    modifier: Modifier = Modifier,
    viewModel: ArticleViewModel = hiltViewModel(),
) {
    val entry by viewModel.entry.collectAsStateWithLifecycle()
    val colors by viewModel.colors.collectAsStateWithLifecycle()
    LaunchedEffect(entryId) { viewModel.load(entryId) }

    var pending by remember { mutableStateOf<PendingHighlight?>(null) }
    var preview by remember { mutableStateOf<String?>(null) }

    val article = entry ?: return

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
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
            Text(
                text = if (colors.isEmpty()) {
                    "Kelimeye çift dokun → işaretle."
                } else {
                    "${colors.size} işaret · işarete dokununca kutu açılır"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            article.body.split("\n\n").forEach { paragraph ->
                HighlightableParagraph(
                    raw = paragraph,
                    colors = colors,
                    textColor = MaterialTheme.colorScheme.onSurface,
                    fontSizeSp = 17,
                    onSelection = { text, sentence ->
                        pending = PendingHighlight(text, sentence)
                    },
                    onPreview = { preview = it },
                )
            }
        }

        preview?.let { text ->
            SelectionPreview(
                text = text,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }

    pending?.let { request ->
        ColorPickerDialog(
            request = request,
            current = colors[request.word.lowercase()],
            onDismiss = { pending = null },
            onPick = { color, keepContext ->
                viewModel.highlight(
                    word = request.word,
                    contextSentence = if (keepContext) request.sentence else "",
                    color = color,
                )
                pending = null
            },
            onRemove = {
                viewModel.removeHighlight(request.word)
                pending = null
            },
        )
    }
}
