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
    val words: List<SubtitleWord> = emptyList(),
    val message: String? = null,
    val failed: Boolean = false,
    val configured: Boolean = false,
)

@HiltViewModel
class SubtitleViewModel @Inject constructor(
    private val repository: SubtitleRepository,
    private val settings: SubtitleSettings,
) : ViewModel() {

    private val _state = MutableStateFlow(SubtitleUiState(configured = settings.configured))
    val state: StateFlow<SubtitleUiState> = _state.asStateFlow()

    fun onQueryChange(value: String) {
        _state.value = _state.value.copy(query = value)
    }

    fun onYearChange(value: String) {
        _state.value = _state.value.copy(year = value.filter { it.isDigit() }.take(4))
    }

    fun prepare() {
        val current = _state.value
        if (current.busy || current.query.isBlank()) return
        _state.value = current.copy(
            busy = true,
            step = "Altyazılar aranıyor…",
            message = null,
            failed = false,
            words = emptyList(),
            pair = null,
        )
        viewModelScope.launch {
            when (val result = repository.prepare(current.query, current.year.toIntOrNull())) {
                is SubtitleResult.Failed -> _state.value = _state.value.copy(
                    busy = false,
                    step = "",
                    message = result.reason,
                    failed = true,
                )

                is SubtitleResult.Ok -> {
                    _state.value = _state.value.copy(
                        pair = result.value,
                        step = "Bilmediğin kelimeler çıkarılıyor…",
                    )
                    val words = repository.extractWords(result.value, WORD_LIMIT)
                    _state.value = _state.value.copy(
                        busy = false,
                        step = "",
                        words = words,
                        message = if (words.isEmpty()) {
                            "Seviyenin üstünde kelime bulunamadı — bu filmi rahat izlersin."
                        } else {
                            null
                        },
                        failed = false,
                    )
                }
            }
        }
    }

    fun save() {
        val current = _state.value
        val pair = current.pair ?: return
        if (current.busy || current.words.isEmpty()) return
        _state.value = current.copy(busy = true, step = "Kelimeler ekleniyor…")
        viewModelScope.launch {
            val count = repository.save(pair, current.words)
            _state.value = _state.value.copy(
                busy = false,
                step = "",
                words = emptyList(),
                message = "$count kelime eklendi. Kelimeler sekmesinde bu filmden çalışabilirsin.",
                failed = false,
            )
        }
    }

    private companion object {
        /**
         * Bir filmden çıkarılan en fazla kelime. Sınırsız olsaydı tek filmden
         * üç yüz kelime düşerdi ve tekrar programı boğulurdu.
         */
        const val WORD_LIMIT = 40
    }
}
