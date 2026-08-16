package com.ahmety.uygulama.feature.vocab

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.VocabWord
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VocabMode { ALL, LEARNING }

data class VocabUiState(
    val deck: List<VocabWord> = emptyList(),
    val mode: VocabMode = VocabMode.ALL,
    val knownCount: Int = 0,
    val learningCount: Int = 0,
    val loaded: Boolean = false,
) {
    val remaining: Int get() = deck.size
}

@HiltViewModel
class VocabViewModel @Inject constructor(
    private val repository: VocabRepository,
) : ViewModel() {

    private val mode = MutableStateFlow(VocabMode.ALL)
    private val allWords = MutableStateFlow<List<VocabWord>>(emptyList())

    init {
        viewModelScope.launch { allWords.value = repository.allWords() }
    }

    val uiState: StateFlow<VocabUiState> = combine(
        allWords,
        repository.observeProgress(),
        mode,
    ) { words, progress, currentMode ->
        val statusByWord = progress.associate { it.word to it.status }
        val known = statusByWord.values.count { it == VocabStatus.KNOWN.name }
        val learning = statusByWord.values.count { it == VocabStatus.LEARNING.name }

        // ALL modunda henüz karar verilmemiş kelimeler; LEARNING modunda
        // "bilmiyorum" dediklerin. Böylece önce hepsini eleyip sonra
        // bilmediklerini çalışabiliyorsun.
        val deck = when (currentMode) {
            VocabMode.ALL -> words.filter { statusByWord[it.word] == null }
            VocabMode.LEARNING -> words.filter { statusByWord[it.word] == VocabStatus.LEARNING.name }
        }
        VocabUiState(
            deck = deck,
            mode = currentMode,
            knownCount = known,
            learningCount = learning,
            loaded = words.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabUiState())

    /** Sola sürükleme: biliyorum. */
    fun markKnown(word: VocabWord) {
        viewModelScope.launch { repository.setStatus(word.word, VocabStatus.KNOWN) }
    }

    /** Sağa sürükleme: bilmiyorum, çalışılacak. */
    fun markLearning(word: VocabWord) {
        viewModelScope.launch { repository.setStatus(word.word, VocabStatus.LEARNING) }
    }

    fun setMode(newMode: VocabMode) {
        mode.value = newMode
    }
}
