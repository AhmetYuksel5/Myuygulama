package com.ahmety.uygulama.feature.vocab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.VocabWord
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class VocabMode { ALL, LEARNING, UNSURE, BOOK }

data class VocabUiState(
    val deck: List<VocabWord> = emptyList(),
    val mode: VocabMode = VocabMode.ALL,
    val knownCount: Int = 0,
    val learningCount: Int = 0,
    val unsureCount: Int = 0,
    val bookCount: Int = 0,
    val loaded: Boolean = false,
) {
    val remaining: Int get() = deck.size
}

@HiltViewModel
class VocabViewModel @Inject constructor(
    private val repository: VocabRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = VocabPrefs(context)

    private val mode = MutableStateFlow(VocabMode.ALL)
    private val assetWords = MutableStateFlow<List<VocabWord>>(emptyList())

    private val _swipeThreshold = MutableStateFlow(prefs.swipeThreshold)
    val swipeThreshold: StateFlow<Int> = _swipeThreshold

    /**
     * Karıştırma anahtarı. Deste her yeniden hesaplandığında sıranın
     * zıplamaması için oturum boyunca sabit; kelimeye göre türetilen bir
     * sözde-rastgele sıra veriyor.
     */
    private val shuffleSeed = System.nanoTime()

    init {
        viewModelScope.launch { assetWords.value = repository.assetWords() }
    }

    val uiState: StateFlow<VocabUiState> = combine(
        assetWords,
        repository.observeProgress(),
        mode,
        // Kitapta yeni işaretlenen mavi kelime, uygulamayı yeniden başlatmadan
        // burada belirmeli; bu yüzden akış olarak dinliyoruz.
        repository.observeBookWords(),
    ) { asset, progress, currentMode, bookWords ->
        val words = repository.mergeWords(asset, bookWords)
        val statusByWord = progress.associate { it.word to it.status }
        val known = statusByWord.values.count { it == VocabStatus.KNOWN.name }
        val learning = statusByWord.values.count { it == VocabStatus.LEARNING.name }
        val unsure = statusByWord.values.count { it == VocabStatus.UNSURE.name }

        val deck = when (currentMode) {
            // Karar verilmemişler ve "emin olamadım" dedikleri birlikte:
            // emin olamadığın kelime elenmiş sayılmamalı.
            VocabMode.ALL -> words.filter {
                val status = statusByWord[it.word]
                status == null || status == VocabStatus.UNSURE.name
            }
            VocabMode.LEARNING -> words.filter {
                statusByWord[it.word] == VocabStatus.LEARNING.name
            }
            VocabMode.UNSURE -> words.filter {
                statusByWord[it.word] == VocabStatus.UNSURE.name
            }
            VocabMode.BOOK -> words.filter { it.fromBook }
        }

        VocabUiState(
            deck = deck.shuffledStably(shuffleSeed),
            mode = currentMode,
            knownCount = known,
            learningCount = learning,
            unsureCount = unsure,
            bookCount = bookWords.size,
            loaded = asset.isNotEmpty() || bookWords.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabUiState())

    /** Sola sürükleme: biliyorum. */
    fun markKnown(word: VocabWord) = setStatus(word, VocabStatus.KNOWN)

    /** Sağa sürükleme: bilmiyorum, çalışılacak. */
    fun markLearning(word: VocabWord) = setStatus(word, VocabStatus.LEARNING)

    /** Aşağı sürükleme: emin olamadım, şimdilik dursun. */
    fun markUnsure(word: VocabWord) = setStatus(word, VocabStatus.UNSURE)

    private fun setStatus(word: VocabWord, status: VocabStatus) {
        viewModelScope.launch { repository.setStatus(word.word, status) }
    }

    fun setMode(newMode: VocabMode) {
        mode.value = newMode
    }

    fun setSwipeThreshold(value: Int) {
        prefs.swipeThreshold = value
        _swipeThreshold.value = prefs.swipeThreshold
    }
}

/**
 * Rastgele ama kararlı sıralama: aynı oturumda deste yeniden hesaplansa da
 * kartlar yer değiştirmiyor, ama sıra alfabetik olmaktan çıkıyor.
 */
private fun List<VocabWord>.shuffledStably(seed: Long): List<VocabWord> =
    sortedBy { word ->
        var hash = seed xor word.word.hashCode().toLong()
        hash = hash xor (hash shl 13)
        hash = hash xor (hash ushr 7)
        hash
    }
