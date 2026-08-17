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
    private val samples: NoteSampleSeeder,
) : ViewModel() {

    init {
        // İlk açılışta not defteri bomboş açılmasın: nasıl kullanıldığını
        // gösteren iki örnek not (biri işaretlenebilir liste) bir kez eklenir.
        viewModelScope.launch { samples.seedIfNeeded() }
    }

    val uiState: StateFlow<NotesUiState> = combine(
        repository.observeByType(EntryType.NOTE),
        repository.observeByType(EntryType.ARTICLE),
    ) { notes, articles ->
        // Sabitlenenler üstte; gerisi güncellenme sırasına göre geliyor.
        val sorted = notes.sortedByDescending { NoteStyle.decode(it.source).pinned }
        NotesUiState(notes = sorted, articles = articles, loaded = true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), NotesUiState())

    fun setColor(entry: Entry, colorIndex: Int) {
        val style = NoteStyle.decode(entry.source).copy(colorIndex = colorIndex)
        viewModelScope.launch { repository.updateSource(entry.id, style.encode()) }
    }

    fun togglePinned(entry: Entry) {
        val style = NoteStyle.decode(entry.source)
        viewModelScope.launch {
            repository.updateSource(entry.id, style.copy(pinned = !style.pinned).encode())
        }
    }

    /** Karttan doğrudan liste maddesini işaretle/kaldır. */
    fun toggleChecklist(entry: Entry, index: Int) {
        val updatedBody = toggleChecklistItem(entry.body, index)
        viewModelScope.launch { repository.updateEntry(entry.id, entry.title, updatedBody) }
    }

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
    val colorIndex: Int = 0,
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
                colorIndex = NoteStyle.decode(entry?.source).colorIndex,
                loaded = true,
            )
        }
    }

    /** Kart rengi anında kaydediliyor; "kaydet" düğmesi yok. */
    fun setColor(index: Int) {
        val state = _uiState.value
        _uiState.value = state.copy(colorIndex = index)
        if (state.id == 0L) return
        viewModelScope.launch {
            val existing = repository.getById(state.id)
            val style = NoteStyle.decode(existing?.source).copy(colorIndex = index)
            repository.updateSource(state.id, style.encode())
        }
    }

    /** Gövdenin sonuna boş bir liste maddesi ekler. */
    fun addChecklistItem() {
        val body = _uiState.value.body
        val prefix = if (body.isBlank() || body.endsWith("\n")) "" else "\n"
        _uiState.value = _uiState.value.copy(body = body + prefix + "[ ] ")
    }

    fun addImage(path: String) {
        val body = _uiState.value.body
        val prefix = if (body.isBlank() || body.endsWith("\n")) "" else "\n"
        _uiState.value = _uiState.value.copy(body = body + prefix + encodeImageLine(path) + "\n")
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
