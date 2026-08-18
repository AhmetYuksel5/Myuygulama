package com.ahmety.uygulama.feature.ebook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BookShelfUiState(
    val books: List<Entry> = emptyList(),
    val importing: Boolean = false,
    val message: String? = null,
    val openBookId: Long? = null,
)

@HiltViewModel
class BookShelfViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(BookShelfUiState())
    val state: StateFlow<BookShelfUiState> = _state.asStateFlow()

    val books: StateFlow<List<Entry>> = repository.observeBooks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun import(uri: Uri) {
        if (_state.value.importing) return
        _state.value = _state.value.copy(importing = true, message = null)
        viewModelScope.launch {
            when (val result = repository.import(uri)) {
                is ImportResult.Imported -> _state.value = BookShelfUiState(
                    importing = false,
                    message = "Eklendi: ${result.title}",
                    openBookId = result.entryId,
                )

                is ImportResult.Failed -> _state.value = BookShelfUiState(
                    importing = false,
                    message = result.reason,
                )
            }
        }
    }

    fun consumeOpenRequest() {
        _state.value = _state.value.copy(openBookId = null)
    }

    fun clearMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun delete(entry: Entry) {
        viewModelScope.launch { repository.deleteBook(entry) }
    }
}

data class ReaderUiState(
    val book: EpubBook? = null,
    val chapterIndex: Int = 0,
    /** Kelime (küçük harf) -> renk. Paragrafları boyamak için. */
    val highlightColors: Map<String, HighlightColor> = emptyMap(),
    val loading: Boolean = true,
)

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var bookId: Long = 0L

    fun load(id: Long) {
        if (bookId == id && _state.value.book != null) return
        bookId = id
        viewModelScope.launch {
            val book = repository.loadBook(id)
            _state.value = ReaderUiState(book = book, loading = false)
            refreshHighlights()
        }
    }

    fun selectChapter(index: Int) {
        _state.value = _state.value.copy(chapterIndex = index)
    }

    fun highlight(word: String, contextSentence: String, color: HighlightColor) {
        viewModelScope.launch {
            repository.toggleHighlight(bookId, word, contextSentence, color)
            refreshHighlights()
        }
    }

    private suspend fun refreshHighlights() {
        val colors = repository.highlightsFor(bookId).mapNotNull { entry ->
            val color = HighlightRef.color(entry.source) ?: return@mapNotNull null
            entry.title.lowercase() to color
        }.toMap()
        _state.value = _state.value.copy(highlightColors = colors)
    }
}
