package com.ahmety.uygulama.feature.ebook

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.ai.WorkBriefStore
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

/** Künye kutusunun durumu. */
data class BriefUiState(
    val work: String,
    val text: String,
    val busy: Boolean = false,
    val error: String? = null,
)

data class BookShelfUiState(
    val books: List<Entry> = emptyList(),
    val importing: Boolean = false,
    val message: String? = null,
    val openBookId: Long? = null,
)

@HiltViewModel
class BookShelfViewModel @Inject constructor(
    private val repository: BookRepository,
    private val openAi: OpenAiClient,
    private val briefs: WorkBriefStore,
) : ViewModel() {

    /**
     * Açık künye kutusu. Künye, kelime sorgularına eklenen kısa eser
     * tanıtımı; ne dediğini görebilmek gerekiyor, çünkü model eseri yanlış
     * tanırsa bütün kartlar ondan etkileniyor.
     */
    private val _brief = MutableStateFlow<BriefUiState?>(null)
    val brief: StateFlow<BriefUiState?> = _brief.asStateFlow()

    fun openBrief(entry: Entry) {
        _brief.value = BriefUiState(
            work = entry.title,
            text = briefs.get(entry.title).orEmpty(),
        )
    }

    fun closeBrief() {
        _brief.value = null
    }

    fun generateBrief() {
        val current = _brief.value ?: return
        if (current.busy) return
        _brief.value = current.copy(busy = true, error = null)
        viewModelScope.launch {
            val sample = runCatching { repository.sampleFor(current.work) }.getOrDefault("")
            if (sample.isBlank()) {
                _brief.value = current.copy(busy = false, error = "Eserin metni bulunamadı.")
                return@launch
            }
            when (val result = openAi.describeWork(current.work, sample)) {
                is AiResult.Ok -> {
                    briefs.put(current.work, result.value)
                    _brief.value = current.copy(text = result.value, busy = false)
                }

                is AiResult.Failed -> _brief.value = current.copy(
                    busy = false,
                    error = result.reason,
                )
            }
        }
    }

    private val _state = MutableStateFlow(BookShelfUiState())
    val state: StateFlow<BookShelfUiState> = _state.asStateFlow()

    /** Kayıt bir film altyazısı mı: kitaplıkta ayrı etiket ve süzgeç için. */
    fun isFilm(entry: Entry): Boolean = repository.isFilm(entry)

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

    fun delete(entry: Entry, withWords: Boolean = false) {
        viewModelScope.launch { repository.deleteBook(entry, withWords) }
    }

    /**
     * Esere ait işaret sayısı. Silme kutusu açılırken sorulup gösteriliyor:
     * "kelimeleriyle sil" derken kaç kelimeden söz ettiğimiz belli olsun.
     */
    suspend fun highlightCount(entry: Entry): Int = repository.highlightCount(entry.id)

    /** Kitaplık satırındaki ilerleme çizgisi için. */
    fun progressOf(bookId: Long): Int = repository.readingPercent(bookId)

    /** Kitabın kapağı; yoksa null ve satır renkli sırta düşüyor. */
    fun coverOf(bookId: Long) = repository.coverFile(bookId)
}

data class ReaderUiState(
    val book: EpubBook? = null,
    val chapterIndex: Int = 0,
    /** Kelime/öbek (küçük harf) -> renk. Paragrafları boyamak için. */
    val highlightColors: Map<String, HighlightColor> = emptyMap(),
    val loading: Boolean = true,
    /** Kitabın başından bu bölüme kadarki karakter sayısı. */
    val charsBefore: Int = 0,
    val totalChars: Int = 1,
    val chapterChars: Int = 1,
)

@HiltViewModel
class BookReaderViewModel @Inject constructor(
    private val repository: BookRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ReaderUiState())
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    private var bookId: Long = 0L

    /** Bölüm başlangıçlarının karakter toplamı; ilerleme yüzdesi için. */
    private var chapterOffsets: List<Int> = emptyList()

    fun load(id: Long) {
        if (bookId == id && _state.value.book != null) return
        bookId = id
        viewModelScope.launch {
            val book = repository.loadBook(id)
            val chapters = book?.chapters.orEmpty()
            val lengths = chapters.map { chapter ->
                chapter.paragraphs.sumOf { it.length + 1 }.coerceAtLeast(1)
            }
            chapterOffsets = lengths.runningFold(0) { acc, length -> acc + length }

            val chapter = repository.lastChapter(id)
                .coerceIn(0, (chapters.lastIndex).coerceAtLeast(0))
            _state.value = ReaderUiState(
                book = book,
                chapterIndex = chapter,
                loading = false,
                charsBefore = chapterOffsets.getOrElse(chapter) { 0 },
                totalChars = chapterOffsets.lastOrNull()?.coerceAtLeast(1) ?: 1,
                chapterChars = lengths.getOrElse(chapter) { 1 },
            )
            refreshHighlights()
        }
    }

    /**
     * Kaldığın paragraf. "Bir kez tüketilen" bir değer değil, her seferinde
     * depodan okunuyor: telefonu çevirince ya da tema değişince ekran
     * yeniden kuruluyor ama görünüm modeli yaşamaya devam ettiği için
     * tüketilmiş bir değerle bölümün başına dönülüyordu.
     *
     * Bölüm değiştirmek zaten 0 yazdığı için ileri geçişte metnin başından
     * başlanıyor.
     */
    fun lastParagraph(): Int = repository.lastParagraph(bookId)

    fun selectChapter(index: Int) {
        val chapters = _state.value.book?.chapters.orEmpty()
        val safe = index.coerceIn(0, chapters.lastIndex.coerceAtLeast(0))
        _state.value = _state.value.copy(
            chapterIndex = safe,
            charsBefore = chapterOffsets.getOrElse(safe) { 0 },
            chapterChars = (chapterOffsets.getOrElse(safe + 1) { 0 } -
                chapterOffsets.getOrElse(safe) { 0 }).coerceAtLeast(1),
        )
        repository.saveLastChapter(bookId, safe)
        repository.saveLastParagraph(bookId, 0)
    }

    /** Okurken kaldığın paragrafı kaydeder. */
    fun savePosition(paragraphIndex: Int) {
        repository.saveLastParagraph(bookId, paragraphIndex)
    }

    /** Okurken hesaplanan yüzdeyi kitaplığın kullanması için saklar. */
    fun saveProgress(percent: Int) {
        if (bookId != 0L) repository.saveReadingPercent(bookId, percent)
    }

    fun highlight(word: String, contextSentence: String, color: HighlightColor) {
        viewModelScope.launch {
            repository.setHighlight(bookId, word, contextSentence, color)
            refreshHighlights()
        }
    }

    fun removeHighlight(word: String) {
        viewModelScope.launch {
            repository.removeHighlight(bookId, word)
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
