package com.ahmety.uygulama.feature.vocab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.model.Collocation
import com.ahmety.uygulama.core.model.VocabDecision
import com.ahmety.uygulama.core.model.VocabSource
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.VocabWord
import com.ahmety.uygulama.core.model.startOfDay
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
    /**
     * Varsayılan: öğrenmediğin her kelime. Yeni bir kitap ya da film
     * eklendiğinde ilk görmek istediğin şey bu.
     */
    ALL("Tümü"),

    /** Vadesi gelen tekrarlar. Program çalışmaya başladıkça dolar. */
    TODAY("Tekrar"),

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
    val selectionCount: Int = 0,
    /** Bugün vadesi gelen kelime sayısı (kuyruk sınırından önce). */
    val dueToday: Int = 0,
    /** Bugün eklenen yeni kelime sayısı ve günlük sınır. */
    val newToday: Int = 0,
    val newLimit: Int = DAILY_NEW_LIMIT,
    /** Vadesi bir günden fazla geçmiş kelime sayısı. */
    val backlog: Int = 0,
    /** Sonraki tekrarın kaç gün sonra olduğu; deste bitince gösteriliyor. */
    val nextDueInDays: Int? = null,
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
            when (val result = openAi.describeWord(word.word, word.context, word.isPassage)) {
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

    private val _swipeThreshold = MutableStateFlow(prefs.swipeThreshold)
    val swipeThreshold: StateFlow<Int> = _swipeThreshold


    val uiState: StateFlow<VocabUiState> = combine(
        repository.observeProgress(),
        mode,
        // Kitapta yeni işaretlenen mavi kelime, uygulamayı yeniden başlatmadan
        // burada belirmeli; bu yüzden akış olarak dinliyoruz.
        repository.observeBookWords(),
        session,
    ) { progress, currentMode, bookWords, sessionState ->
        val words = repository.applyEnrichment(bookWords)
        val known = progress.count { it.status == VocabStatus.KNOWN.name }
        val learning = progress.count {
            it.status == VocabStatus.LEARNING.name || it.status == VocabStatus.UNSURE.name
        }
        val ignored = progress.count { it.status == VocabStatus.IGNORED.name }

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

        val schedules = progress.associateBy({ it.word }, { it.toSchedule() })
        val nowMillis = repository.nowMillis()
        val dayStart = startOfDay(nowMillis, repository.zoneOffsetMillis(nowMillis))
        val endOfToday = dayStart + DAY_MILLIS
        val introducedToday = progress.count { (it.introducedAt ?: 0L) >= dayStart }

        // Programdaki kelimeler: vadesi gelmiş olanlar en çok bekleyenden
        // başlayarak, sonra kademesi düşük olanlar. Karıştırma yok —
        // "rastgele olmasın" istenen tam olarak buydu.
        fun scheduled(word: VocabWord) = schedules[word.word]
        val due = filtered
            .filter { word ->
                val plan = scheduled(word)
                plan != null &&
                    plan.status == VocabStatus.LEARNING &&
                    plan.dueAt != null &&
                    plan.dueAt!! <= endOfToday
            }
            .sortedWith(
                compareBy(
                    { scheduled(it)?.dueAt ?: Long.MAX_VALUE },
                    { scheduled(it)?.box ?: 0 },
                    { it.word.lowercase() },
                ),
            )

        val backlog = due.count { (scheduled(it)?.dueAt ?: 0L) < dayStart }

        // Henüz programa girmemiş kelimeler. Sıra sabit, rastgele değil.
        val fresh = filtered
            .filter { scheduled(it) == null }
            .sortedBy { it.word.lowercase() }

        // Birikme varken yeni kelime vermiyoruz; yığın erimeden üstüne
        // eklemek kullanıcıyı kaçırıyor.
        val newAllowance = if (backlog > BACKLOG_PAUSE) {
            0
        } else {
            (DAILY_NEW_LIMIT - introducedToday).coerceAtLeast(0)
        }

        val deck = when (currentMode) {
            VocabMode.TODAY -> interleave(due, fresh.take(newAllowance))
            // Tümü: öğrendiğin ve önemsize attığın dışındaki her kelime.
            // Vadesi gelenler başa geliyor ki tekrar aksamasın.
            VocabMode.ALL -> {
                val rest = filtered
                    .filter { it !in due }
                    .filter {
                        val status = scheduled(it)?.status
                        status != VocabStatus.KNOWN && status != VocabStatus.IGNORED
                    }
                    .sortedBy { it.word.lowercase() }
                due + rest
            }
            VocabMode.IGNORED -> filtered.filter {
                scheduled(it)?.status == VocabStatus.IGNORED
            }.sortedBy { it.word.lowercase() }
            VocabMode.KNOWN -> filtered.filter {
                scheduled(it)?.status == VocabStatus.KNOWN
            }.sortedBy { it.word.lowercase() }
        }

        // Bu oturumda "geç" denenler en sona; program sırası bozulmuyor.
        val ordered = deck.sortedBy { if (it.word in sessionState.skipped) 1 else 0 }

        val nextDue = schedules.values
            .mapNotNull { it.dueAt }
            .filter { it > endOfToday }
            .minOrNull()
            ?.let { ((it - dayStart) / DAY_MILLIS).toInt() }

        VocabUiState(
            deck = ordered,
            mode = currentMode,
            knownCount = known,
            learningCount = learning,
            unsureCount = ignored,
            bookCount = bookWords.count { it.source == VocabSource.BOOK },
            subtitleCount = bookWords.count { it.source == VocabSource.SUBTITLE },
            selectionCount = bookWords.count { it.source == VocabSource.SELECTION },
            sources = bookWords
                .filter { it.sourceName.isNotBlank() }
                .map { it.source to it.sourceName }
                .distinct()
                .sortedBy { it.second.lowercase() },
            filter = sessionState.filter,
            dueToday = due.size,
            newToday = introducedToday,
            backlog = backlog,
            nextDueInDays = nextDue,
            loaded = true,
            turn = sessionState.turn,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VocabUiState())

    /** Yukarı: öğrendim, bir daha gösterme. */
    fun markKnown(word: VocabWord, revealed: Boolean) =
        decide(word, VocabDecision.LEARNED, revealed)

    /** Sol: çalıştım, tekrar çalışacağım — sıradaki gelsin. */
    fun markLearning(word: VocabWord, revealed: Boolean) =
        decide(word, VocabDecision.STUDIED, revealed)

    /** Aşağı: önemsiz kelimeler arasına at; silme. */
    fun markIgnored(word: VocabWord, revealed: Boolean) =
        decide(word, VocabDecision.IGNORE, revealed)

    /** Sağ: şimdilik geç. Kelime yarına atılıyor, kademesi tükenmiyor. */
    fun skip(word: VocabWord, revealed: Boolean) =
        decide(word, VocabDecision.POSTPONE, revealed)

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
    /**
     * Kelime bilgisini sıfırdan yeniden getirir.
     *
     * Eski kayıtlarda kelime ailesi, eş/zıt anlamlılar ve karıştırma
     * bölümleri yok — o alanlar sonradan eklendi. Birleştirmek yerine
     * baştan yazıyoruz ki eski, eksik veri kalmasın.
     */
    fun refresh(word: VocabWord) {
        if (_enriching.value != null) return
        _enriching.value = word.word
        viewModelScope.launch {
            when (val result = openAi.describeWord(word.word, word.context, word.isPassage)) {
                is AiResult.Ok -> {
                    repository.saveEnrichment(result.value)
                    _aiMessage.value = null
                    session.value = session.value.copy(refresh = session.value.refresh + 1)
                }

                is AiResult.Failed -> _aiMessage.value = result.reason
            }
            _enriching.value = null
        }
    }

    fun addMoreExamples(word: VocabWord) {
        if (_enriching.value != null) return
        _enriching.value = word.word
        viewModelScope.launch {
            when (val result = openAi.describeWord(word.word, word.context, word.isPassage)) {
                is AiResult.Ok -> {
                    val fresh = result.value
                    repository.saveEdit(
                        word.copy(
                            meaning = word.meaning.ifBlank { fresh.meaning },
                            definition = word.definition.ifBlank { fresh.definition },
                            examples = (word.examples + fresh.examples).distinct(),
                            related = (word.related + fresh.related).distinct(),
                            family = (word.family + fresh.family).distinct(),
                            confusions = (word.confusions + fresh.confusions).distinct(),
                            collocations = mergeCollocations(word.collocations, fresh.collocations),
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

    private fun decide(word: VocabWord, decision: VocabDecision, revealed: Boolean) {
        advance(word)
        viewModelScope.launch { repository.applyDecision(word.word, decision, revealed) }
    }

    fun setMode(newMode: VocabMode) {
        mode.value = newMode
    }

    fun setSwipeThreshold(value: Int) {
        prefs.swipeThreshold = value
        _swipeThreshold.value = prefs.swipeThreshold
    }
}

/** Günlük yeni kelime sınırı. Sınırsız yeni kelime, günler sonra taşıyamayacağın bir tekrar yükü demek. */
/**
 * Aynı kalıptaki collocation'ları tek grupta topluyor: "örnek çoğalt" ikinci
 * kez çağrıldığında "fiil +" iki ayrı blok olarak görünmemeli.
 */
private fun mergeCollocations(
    current: List<Collocation>,
    fresh: List<Collocation>,
): List<Collocation> {
    val byPattern = LinkedHashMap<String, MutableList<String>>()
    (current + fresh).forEach { group ->
        val words = byPattern.getOrPut(group.pattern) { mutableListOf() }
        group.words.forEach { word -> if (word !in words) words += word }
    }
    return byPattern.map { (pattern, words) -> Collocation(pattern, words) }
}

private const val DAILY_NEW_LIMIT = 8

/** Bu kadar kelime birikmişse yeni kelime verilmiyor; önce yığın erisin. */
private const val BACKLOG_PAUSE = 40

private const val DAY_MILLIS = 86_400_000L

/**
 * Tekrarların arasına yeni kelimeleri serpiştirir: üst üste sekiz yeni kelime
 * yorucu, hepsini sona atmak da sıkıcı.
 */
private fun interleave(due: List<VocabWord>, fresh: List<VocabWord>): List<VocabWord> {
    if (fresh.isEmpty()) return due
    if (due.isEmpty()) return fresh
    val step = (due.size / (fresh.size + 1)).coerceAtLeast(1)
    val result = mutableListOf<VocabWord>()
    var nextFresh = 0
    due.forEachIndexed { index, word ->
        result += word
        if (nextFresh < fresh.size && (index + 1) % step == 0) {
            result += fresh[nextFresh++]
        }
    }
    while (nextFresh < fresh.size) result += fresh[nextFresh++]
    return result
}

