package com.ahmety.uygulama.feature.vocab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.OpenAiClient
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
    private val openAi: OpenAiClient,
    private val aiSettings: AiSettings,
    @ApplicationContext context: Context,
) : ViewModel() {

    /** Anlamı getirilen kelime (yükleniyor göstergesi için). */
    private val _enriching = MutableStateFlow<String?>(null)
    val enriching: StateFlow<String?> = _enriching

    private val _aiMessage = MutableStateFlow<String?>(null)
    val aiMessage: StateFlow<String?> = _aiMessage

    val aiConfigured: Boolean get() = aiSettings.configured

    /**
     * Kitaptan gelip sözlükte karşılığı olmayan kelimenin anlamını, tanımını,
     * örneklerini ve öbeklerini yapay zekâyla doldurur. Bağlam cümlesi de
     * gönderiliyor: kelimenin hangi anlamda kullanıldığı oradan anlaşılıyor.
     */
    fun enrich(word: VocabWord) {
        if (_enriching.value != null) return
        _enriching.value = word.word
        viewModelScope.launch {
            when (val result = openAi.describeWord(word.word, word.context)) {
                is AiResult.Ok -> {
                    repository.saveEnrichment(result.value)
                    _aiMessage.value = null
                    // Deste yeniden hesaplansın: zenginleştirme veritabanında
                    // değil kendi deposunda durduğu için akış kendiliğinden
                    // tetiklenmiyor.
                    session.value = session.value.copy(refresh = session.value.refresh + 1)
                }
                is AiResult.Failed -> _aiMessage.value = result.reason
            }
            _enriching.value = null
        }
    }

    fun clearAiMessage() {
        _aiMessage.value = null
    }

    /**
     * Oturumluk durum. `refresh` yapay zekâ yazımından sonra desteyi yeniden
     * hesaplatıyor; `skipped` ise "geç" denen kelimeleri tutuyor — bunlar
     * kalıcı bir karar değil, yalnızca destenin sonuna atılıyor.
     */
    private data class VocabSession(
        val refresh: Int = 0,
        val skipped: Set<String> = emptySet(),
    )

    private val session = MutableStateFlow(VocabSession())

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
        session,
    ) { asset, progress, currentMode, bookWords, sessionState ->
        val words = repository.mergeWords(asset, repository.applyEnrichment(bookWords))
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
            // Karar verilen kelime destede kalırsa üstteki kart değişmiyor
            // ve deste kilitleniyor; karar verilmemişleri (ve "emin değilim")
            // gösteriyoruz.
            VocabMode.BOOK -> words.filter {
                val status = statusByWord[it.word]
                it.fromBook && (status == null || status == VocabStatus.UNSURE.name)
            }
        }

        // Sona atılanlar: önce "emin değilim", en sonda "geç" dedikleri.
        // İkisi de kalıcı karar değil; deste bitmeden yine karşına çıkıyorlar.
        val ordered = deck.shuffledStably(shuffleSeed)
            .sortedBy { word ->
                when {
                    word.word in sessionState.skipped -> 2
                    statusByWord[word.word] == VocabStatus.UNSURE.name -> 1
                    else -> 0
                }
            }

        VocabUiState(
            deck = ordered,
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

    /**
     * Yukarı sürükleme: yalnızca geç. Hiçbir karar kaydedilmiyor; kelime
     * destenin sonuna gidiyor ve bu oturumda yeniden karşına çıkıyor.
     */
    fun skip(word: VocabWord) {
        session.value = session.value.copy(skipped = session.value.skipped + word.word)
    }

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
