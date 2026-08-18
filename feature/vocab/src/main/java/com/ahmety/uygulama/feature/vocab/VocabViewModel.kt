package com.ahmety.uygulama.feature.vocab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.model.VocabSource
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

/**
 * Destenin hangi kelimeleri göstereceği.
 *
 * Kaynak süzgeci ayrı: [VocabFilter.sourceName] belirli bir kitabı ya da
 * filmi seçmeye yarıyor, kip ise öğrenme durumunu süzüyor.
 */
enum class VocabMode(val label: String) {
    /** Öğrenilmemiş her şey. */
    ALL("Tümü"),

    /** Çalışılmakta olanlar. */
    LEARNING("Çalıştıklarım"),

    /** Aşağı atılan, şimdilik gerekmeyenler. */
    IGNORED("Önemsiz"),

    /** Öğrenildi denilenler; gözden geçirmek için. */
    KNOWN("Öğrendiklerim"),
}

/** Kaynak süzgeci: tümü, yalnız kitaplar, yalnız filmler ya da tek bir başlık. */
data class VocabFilter(
    val source: VocabSource? = null,
    val sourceName: String = "",
)

data class VocabUiState(
    val deck: List<VocabWord> = emptyList(),
    val mode: VocabMode = VocabMode.ALL,
    val knownCount: Int = 0,
    val learningCount: Int = 0,
    val unsureCount: Int = 0,
    val bookCount: Int = 0,
    val subtitleCount: Int = 0,
    /** Süzgeç listesi için: elindeki kitap ve film adları. */
    val sources: List<Pair<VocabSource, String>> = emptyList(),
    val filter: VocabFilter = VocabFilter(),
    val loaded: Boolean = false,
    /**
     * Verilen karar sayısı. Karttaki animasyon durumunun anahtarına giriyor:
     * aynı kelime üstte kalsa bile (deste tek kartlıysa ya da "geç" denen
     * kelime yine öne geldiyse) kart yeniden kuruluyor. Bu olmadan kart
     * ekran dışında donup kalıyordu.
     */
    val turn: Int = 0,
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
        /** Her kararda artıyor; aynı değerle yeniden yayın yapılmasını da önlüyor. */
        val turn: Int = 0,
        val skipped: Set<String> = emptySet(),
        val filter: VocabFilter = VocabFilter(),
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
        val learning = statusByWord.values.count {
            it == VocabStatus.LEARNING.name || it == VocabStatus.UNSURE.name
        }
        val ignored = statusByWord.values.count { it == VocabStatus.IGNORED.name }

        // Kaynak süzgeci önce: "şu kitaptan" ya da "şu filmden" çalışmak
        // istediğinde deste ona iniyor.
        val filtered = words.filter { word ->
            val filter = sessionState.filter
            when {
                filter.sourceName.isNotBlank() -> word.sourceName == filter.sourceName
                filter.source != null -> word.source == filter.source
                else -> true
            }
        }

        val deck = when (currentMode) {
            // Karar verilmemişler ve çalışılmakta olanlar birlikte: kelime
            // zaten bilinmediği için listede, "öğrendim" diyene kadar çıkıyor.
            VocabMode.ALL -> filtered.filter {
                val status = statusOf(statusByWord, it.word)
                status == null || status == VocabStatus.LEARNING
            }
            VocabMode.LEARNING -> filtered.filter {
                statusOf(statusByWord, it.word) == VocabStatus.LEARNING
            }
            VocabMode.IGNORED -> filtered.filter {
                statusOf(statusByWord, it.word) == VocabStatus.IGNORED
            }
            VocabMode.KNOWN -> filtered.filter {
                statusOf(statusByWord, it.word) == VocabStatus.KNOWN
            }
        }

        // Bu oturumda karar verdiklerin en sona gidiyor. Hiçbiri desteden
        // düşmüyor: karar kalıcı değilse deste bitmeden yine karşına çıkıyor.
        val ordered = deck.shuffledStably(shuffleSeed)
            .sortedBy { word -> if (word.word in sessionState.skipped) 1 else 0 }

        VocabUiState(
            deck = ordered,
            mode = currentMode,
            knownCount = known,
            learningCount = learning,
            unsureCount = ignored,
            bookCount = bookWords.count { it.source == VocabSource.BOOK },
            subtitleCount = bookWords.count { it.source == VocabSource.SUBTITLE },
            sources = bookWords
                .filter { it.sourceName.isNotBlank() }
                .map { it.source to it.sourceName }
                .distinct()
                .sortedBy { it.second.lowercase() },
            filter = sessionState.filter,
            loaded = asset.isNotEmpty() || bookWords.isNotEmpty(),
            turn = sessionState.turn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabUiState())

    /** Yukarı: öğrendim, bir daha gösterme. */
    fun markKnown(word: VocabWord) = setStatus(word, VocabStatus.KNOWN)

    /** Sol: çalıştım, tekrar çalışacağım — sıradaki gelsin. */
    fun markLearning(word: VocabWord) = setStatus(word, VocabStatus.LEARNING)

    /** Aşağı: önemsiz kelimeler arasına at; silme. */
    fun markIgnored(word: VocabWord) = setStatus(word, VocabStatus.IGNORED)

    /**
     * Sağ: şimdilik geç. Hiçbir karar kaydedilmiyor; kelime destenin sonuna
     * gidiyor ve bu oturumda yeniden karşına çıkıyor.
     */
    fun skip(word: VocabWord) = advance(word)

    /** Listeden tamamen kaldır. */
    fun delete(word: VocabWord) {
        advance(word)
        viewModelScope.launch { repository.deleteWord(word) }
    }

    /** Elle düzenlenen kelime bilgisini kaydeder. */
    fun saveEdit(word: VocabWord) {
        repository.saveEdit(word)
        session.value = session.value.copy(refresh = session.value.refresh + 1)
    }

    /**
     * Var olan bilgiye dokunmadan yapay zekâdan yeni örnek ve öbek ister.
     * "Tam anlayamadım" dediğin kelimede aynı anlamı başka cümlelerde
     * görmek işe yarıyor.
     */
    fun addMoreExamples(word: VocabWord) {
        if (_enriching.value != null) return
        _enriching.value = word.word
        viewModelScope.launch {
            when (val result = openAi.describeWord(word.word, word.context)) {
                is AiResult.Ok -> {
                    val fresh = result.value
                    repository.saveEdit(
                        word.copy(
                            meaning = word.meaning.ifBlank { fresh.meaning },
                            definition = word.definition.ifBlank { fresh.definition },
                            examples = (word.examples + fresh.examples).distinct(),
                            related = (word.related + fresh.related).distinct(),
                            phrases = (word.phrases + fresh.phrases).distinct(),
                        ),
                    )
                    _aiMessage.value = null
                    session.value = session.value.copy(refresh = session.value.refresh + 1)
                }

                is AiResult.Failed -> _aiMessage.value = result.reason
            }
            _enriching.value = null
        }
    }

    fun setFilter(filter: VocabFilter) {
        session.value = session.value.copy(filter = filter, turn = session.value.turn + 1)
    }

    /**
     * Karar verilen kelimeyi destenin sonuna atar ve karar sayacını artırır.
     *
     * İkisi de şart. Durum yazması desteyi her zaman değiştirmiyor; kart
     * anahtarı değişmeyince kart ekran dışında donup kalıyordu.
     */
    private fun advance(word: VocabWord) {
        session.value = session.value.copy(
            turn = session.value.turn + 1,
            skipped = session.value.skipped + word.word,
        )
    }

    private fun setStatus(word: VocabWord, status: VocabStatus) {
        advance(word)
        viewModelScope.launch { repository.setStatus(word.word, status) }
    }

    /** Eski "emin değilim" kayıtlarını bugünkü anlamına indirger. */
    private fun statusOf(statusByWord: Map<String, String>, word: String): VocabStatus? =
        statusByWord[word]
            ?.let { runCatching { VocabStatus.valueOf(it) }.getOrNull() }
            ?.normalized()

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
