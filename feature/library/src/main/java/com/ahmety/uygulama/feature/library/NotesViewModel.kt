package com.ahmety.uygulama.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.Entry
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class NotesUiState(
    val notes: List<Entry> = emptyList(),
    val articles: List<Entry> = emptyList(),
    val archivedNotes: List<Entry> = emptyList(),
    val highlights: List<Entry> = emptyList(),
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
        repository.observeArchivedByType(EntryType.NOTE),
        repository.observeByType(EntryType.HIGHLIGHT),
    ) { notes, articles, archived, allHighlights ->
        // Kitaptaki kelime işaretlemeleri buraya düşmemeli: bir kitapta
        // yüzlerce kelime işaretlenebiliyor, liste kullanılmaz hale geliyordu.
        val highlights = allHighlights.filter {
            HighlightRef.kind(it.source) != HighlightRef.KIND_BOOK
        }
        // Sabitlenenler üstte; gerisi güncellenme sırasına göre geliyor.
        val sorted = notes.sortedByDescending { NoteStyle.decode(it.source).pinned }
        NotesUiState(
            notes = sorted,
            articles = articles,
            archivedNotes = archived,
            highlights = highlights,
            loaded = true,
        )
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

    /**
     * Karttan doğrudan liste maddesini işaretle/kaldır.
     *
     * Gövdeyi ekrandaki (bayat olabilen) kopyadan değil, yazmadan hemen önce
     * veritabanından okuyoruz: art arda iki maddeyi hızlıca işaretleyince
     * ikinci yazma birincisini geri alıyordu.
     */
    fun toggleChecklist(entry: Entry, index: Int) {
        viewModelScope.launch {
            val fresh = repository.getById(entry.id) ?: return@launch
            repository.updateEntry(fresh.id, fresh.title, toggleChecklistItem(fresh.body, index))
        }
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
    /** Liste ve fotoğraf satırları dışında kalan düz metin. */
    val plain: String = "",
    val items: List<ChecklistItem> = emptyList(),
    val images: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val colorIndex: Int = 0,
    val loaded: Boolean = false,
) {
    /**
     * Kaydedilecek gövde. Düzenleyicide liste maddeleri gerçek kutucuk olarak
     * duruyor; ham "[ ] madde" metnini kullanıcıya göstermiyoruz, yalnızca
     * saklarken bu biçime çeviriyoruz.
     */
    fun composeBody(): String = buildList {
        images.forEach { add(encodeImageLine(it)) }
        if (plain.isNotBlank()) add(plain.trimEnd())
        items.filter { it.text.isNotBlank() || it.checked }
            .forEach { add(encodeChecklistItem(it)) }
    }.joinToString("\n")

    val isEmpty: Boolean
        get() = title.isBlank() && plain.isBlank() && images.isEmpty() &&
            items.none { it.text.isNotBlank() }
}

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
            val body = entry?.body.orEmpty()
            _uiState.value = NoteEditorUiState(
                id = id,
                title = entry?.title.orEmpty(),
                plain = plainBody(body),
                items = parseChecklist(body),
                images = parseImagePaths(body),
                tags = entry?.tags?.map { it.name }.orEmpty(),
                colorIndex = NoteStyle.decode(entry?.source).colorIndex,
                loaded = true,
            )
        }
    }

    fun onTitleChange(value: String) {
        _uiState.value = _uiState.value.copy(title = value)
    }

    fun onPlainChange(value: String) {
        _uiState.value = _uiState.value.copy(plain = value)
    }

    fun addChecklistItem() {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items + ChecklistItem(text = "", checked = false),
        )
    }

    fun onItemTextChange(index: Int, text: String) {
        updateItem(index) { it.copy(text = text) }
    }

    fun toggleItem(index: Int) {
        updateItem(index) { it.copy(checked = !it.checked) }
    }

    fun removeItem(index: Int) {
        val items = _uiState.value.items.toMutableList()
        if (index !in items.indices) return
        items.removeAt(index)
        _uiState.value = _uiState.value.copy(items = items)
    }

    private fun updateItem(index: Int, transform: (ChecklistItem) -> ChecklistItem) {
        val items = _uiState.value.items.toMutableList()
        if (index !in items.indices) return
        items[index] = transform(items[index])
        _uiState.value = _uiState.value.copy(items = items)
    }

    fun addImage(path: String) {
        _uiState.value = _uiState.value.copy(images = _uiState.value.images + path)
    }

    fun removeImage(path: String) {
        _uiState.value = _uiState.value.copy(images = _uiState.value.images - path)
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

    /**
     * Ekrandan çıkarken kaydediyoruz. Tamamen boş kalan not siliniyor —
     * ama renk seçilmişse bu bir niyettir, silmiyoruz.
     */
    fun save() {
        val state = _uiState.value
        if (!state.loaded || state.id == 0L) return
        viewModelScope.launch {
            if (state.isEmpty && state.colorIndex == 0) {
                repository.deleteEntry(state.id)
            } else {
                repository.updateEntry(state.id, state.title, state.composeBody())
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
