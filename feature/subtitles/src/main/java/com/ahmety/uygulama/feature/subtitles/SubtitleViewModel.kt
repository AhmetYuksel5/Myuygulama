package com.ahmety.uygulama.feature.subtitles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SubtitleUiState(
    val query: String = "",
    val year: String = "",
    val busy: Boolean = false,
    val step: String = "",
    val pair: SubtitlePair? = null,
    val picks: List<SubtitlePick> = emptyList(),
    /** Eklenecek olanlar. Baştan hepsi seçili; istemediğini çıkarıyorsun. */
    val chosen: Set<String> = emptySet(),
    /** Zorluk eşiği, 0-100. */
    val difficulty: Int = 60,
    /** Öğrenilen dil: altyazının hangi dilde indirileceği. */
    val language: SubtitleLanguage = SubtitleLanguage.ENGLISH,
    val message: String? = null,
    val failed: Boolean = false,
    val configured: Boolean = false,
    /** Film kitaplığa eklendiyse kaydın kimliği; ikinci kez eklenmesin diye. */
    val savedMovieId: Long? = null,
)

@HiltViewModel
class SubtitleViewModel @Inject constructor(
    private val repository: SubtitleRepository,
    private val settings: SubtitleSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SubtitleUiState(
            configured = settings.configured,
            difficulty = settings.difficulty,
            language = settings.language,
        ),
    )
    val state: StateFlow<SubtitleUiState> = _state.asStateFlow()

    /** Ayar kutusu kapanınca anahtarın girilip girilmediğini yeniden okuyoruz. */
    fun refreshConfigured() {
        _state.value = _state.value.copy(configured = settings.configured)
    }

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun onYearChange(value: String) {
        _state.value = _state.value.copy(year = value.filter { it.isDigit() }.take(4))
    }

    /**
     * Zorluk eşiği. Altyazı zaten indirilmişse yeniden indirmeden listeyi
     * yeni eşikle kuruyoruz — kaydırıcıyı deneyerek ayarlayabilesin.
     */
    fun onDifficultyChange(value: Int) {
        settings.difficulty = value
        _state.value = _state.value.copy(difficulty = value)
    }

    /** Dil değişince eldeki seçim geçersiz: yeni altyazı indirmek gerekiyor. */
    fun onLanguageChange(value: SubtitleLanguage) {
        if (_state.value.busy || _state.value.language == value) return
        settings.language = value
        _state.value = _state.value.copy(
            language = value,
            picks = emptyList(),
            chosen = emptySet(),
            pair = null,
            message = null,
            failed = false,
        )
    }

    fun applyDifficulty() {
        val pair = _state.value.pair ?: return
        if (_state.value.busy) return
        viewModelScope.launch { extract(pair) }
    }

    /** Listeden çıkar / geri al. */
    fun toggle(pick: SubtitlePick) {
        val current = _state.value.chosen
        _state.value = _state.value.copy(
            chosen = if (pick.text in current) current - pick.text else current + pick.text,
        )
    }

    fun prepare() {
        val current = _state.value
        if (current.busy || current.query.isBlank()) return
        _state.value = current.copy(
            busy = true,
            step = "Altyazılar aranıyor…",
            message = null,
            failed = false,
            picks = emptyList(),
            chosen = emptySet(),
            pair = null,
            savedMovieId = null,
        )
        viewModelScope.launch {
            // Beklenmedik bir hata ekranı kapatmasın: ne olduğunu yazıp
            // duruyoruz, kullanıcı yeniden deneyebiliyor.
            val outcome = runCatching {
                repository.prepare(current.query, current.year.toIntOrNull(), current.language)
            }.getOrElse { error ->
                SubtitleResult.Failed("Beklenmedik hata: ${describe(error)}")
            }
            when (val result = outcome) {
                is SubtitleResult.Failed -> _state.value = _state.value.copy(
                    busy = false,
                    step = "",
                    message = result.reason,
                    failed = true,
                )

                is SubtitleResult.Ok -> {
                    _state.value = _state.value.copy(pair = result.value)
                    extract(result.value)
                }
            }
        }
    }

    /**
     * Altyazıdan seçimi kurar. İndirmeden bağımsız: eşik değişince yeniden
     * çağrılıyor.
     */
    private suspend fun extract(pair: SubtitlePair) {
        _state.value = _state.value.copy(busy = true, step = "Zor kelime ve cümleler seçiliyor…")
        // Çıkarma patlarsa bunu "bir şey bulunamadı" diye göstermek
        // yanıltıyordu: sebebi olduğu gibi yazıyoruz.
        val extracted = runCatching {
            repository.extract(
                pair = pair,
                minDifficulty = _state.value.difficulty,
                wordLimit = WORD_LIMIT,
                sentenceLimit = SENTENCE_LIMIT,
            )
        }
        val picks = extracted.getOrDefault(emptyList())
        val failure = extracted.exceptionOrNull()
        _state.value = _state.value.copy(
            busy = false,
            step = "",
            picks = picks,
            chosen = picks.map { it.text }.toSet(),
            message = when {
                failure != null -> "Seçim yapılamadı: ${describe(failure)}"
                picks.isEmpty() ->
                    "Bu eşikte bir şey çıkmadı. Zorluğu düşürüp yeniden dene."
                else -> null
            },
            failed = failure != null,
        )
    }

    /**
     * Filmi yalnızca okumak için kitaplığa ekler.
     *
     * Kelime seçmeden altyazıyı okuyabilmek gerekiyordu; eskiden film ancak
     * en az bir madde eklendiğinde kaydediliyordu.
     */
    fun saveFilm() {
        val current = _state.value
        val pair = current.pair ?: return
        if (current.busy || current.savedMovieId != null) return
        _state.value = current.copy(busy = true, step = "Altyazı ekleniyor…")
        viewModelScope.launch {
            val id = runCatching { repository.saveFilm(pair) }.getOrNull()
            _state.value = _state.value.copy(
                busy = false,
                step = "",
                savedMovieId = id,
                message = if (id == null) {
                    "Altyazı eklenemedi."
                } else {
                    "Altyazı Kitaplık'a eklendi; okuyup kendin işaretleyebilirsin."
                },
                failed = id == null,
            )
        }
    }

    fun save() {
        val current = _state.value
        val pair = current.pair ?: return
        val picks = current.picks.filter { it.text in current.chosen }
        if (current.busy || picks.isEmpty()) return
        _state.value = current.copy(busy = true, step = "Ekleniyor…")
        viewModelScope.launch {
            val count = runCatching {
                repository.save(pair, picks, current.savedMovieId)
            }.getOrElse { 0 }
            _state.value = _state.value.copy(
                busy = false,
                step = "",
                picks = emptyList(),
                chosen = emptySet(),
                message = "$count madde eklendi. Film artık Kitaplık'ta da okunabilir.",
                failed = false,
            )
        }
    }

    private companion object {
        /**
         * Hatayı okunur hâle getirir.
         *
         * Yalnız `message` yazınca bazı hatalar tek başına anlamsız kalıyordu
         * (sınıf yüklenemediğinde ileti yalnızca sınıfın adı oluyor). Sebep
         * zincirini ve hatanın çıktığı satırı da yazıyoruz ki bir dahaki
         * seferde tahmin etmek zorunda kalmayalım.
         */
        fun describe(error: Throwable): String {
            val chain = generateSequence(error) { it.cause }
                .take(3)
                .joinToString(" ← ") {
                    "${it::class.java.simpleName}: ${it.message ?: "-"}"
                }
            val frame = error.stackTrace.firstOrNull()?.let {
                "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}"
            }
            return listOfNotNull(chain, frame).joinToString(" @ ")
        }

        /**
         * Bir filmden çıkarılan en fazla kelime. Sınırsız olsaydı tek filmden
         * üç yüz kelime düşerdi ve tekrar programı boğulurdu.
         */
        const val WORD_LIMIT = 40

        /** Bir filmden alınacak en fazla zor cümle. */
        const val SENTENCE_LIMIT = 15
    }
}
