package com.ahmety.uygulama.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val notes: List<Entry> = emptyList(),
    val articles: List<Entry> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class NotesViewModel @Inject constructor(
    private val repository: EntryRepository,
) : ViewModel() {

    val uiState: StateFlow<NotesUiState> = combine(
        repository.observeByType(EntryType.NOTE),
        repository.observeByType(EntryType.ARTICLE),
    ) { notes, articles ->
        NotesUiState(notes = notes, articles = articles, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    /** Yeni oluşturulan notun kimliği; ekran bunu görünce editöre geçer. */
    private val _createdNoteId = MutableStateFlow<Long?>(null)
    val createdNoteId: StateFlow<Long?> = _createdNoteId.asStateFlow()

    fun createNote() {
        viewModelScope.launch {
            _createdNoteId.value = repository.createEntry(
                type = EntryType.NOTE,
                title = "",
                body = "",
            )
        }
    }

    fun consumeCreatedNote() {
        _createdNoteId.value = null
    }

    fun archive(entry: Entry) {
        viewModelScope.launch { repository.setArchived(entry.id, !entry.archived) }
    }

    fun delete(entry: Entry) {
        viewModelScope.launch { repository.deleteEntry(entry.id) }
    }
}

data class NoteEditorUiState(
    val id: Long = 0L,
    val title: String = "",
    val body: String = "",
    val tags: List<String> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class NoteEditorViewModel @Inject constructor(
    private val repository: EntryRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(NoteEditorUiState())
    val uiState: StateFlow<NoteEditorUiState> = _uiState.asStateFlow()

    fun load(id: Long) {
        if (_uiState.value.id == id && _uiState.value.loaded) return
        viewModelScope.launch {
            val entry = repository.getById(id)
            _uiState.value = NoteEditorUiState(
                id = id,
                title = entry?.title.orEmpty(),
                body = entry?.body.orEmpty(),
                tags = entry?.tags?.map { it.name }.orEmpty(),
                loaded = true,
            )
        }
    }

    fun onTitleChange(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun onBodyChange(value: String) {
        _uiState.value = _uiState.value.copy(body = value)
    }

    /**
     * Not defterinde "kaydet" düğmesi olmaması gerektiği için ekrandan
     * çıkarken kaydediyoruz. Boş kalan not (başlık ve gövde boşsa) siliniyor —
     * yanlışlıkla açılan boş notlar arşivi kirletmesin.
     */
    fun save() {
        val state = _uiState.value
        if (!state.loaded || state.id == 0L) return
        viewModelScope.launch {
            if (state.title.isBlank() && state.body.isBlank()) {
                repository.deleteEntry(state.id)
            } else {
                repository.updateEntry(state.id, state.title, state.body)
            }
        }
    }

    fun addTag(name: String) {
        val state = _uiState.value
        if (name.isBlank() || state.id == 0L) return
        viewModelScope.launch {
            repository.addTag(state.id, name)
            _uiState.value = _uiState.value.copy(tags = _uiState.value.tags + name.trim())
        }
    }
}
