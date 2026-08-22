package com.ahmety.uygulama.feature.vocab

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.ai.WorkBriefStore
import com.ahmety.uygulama.feature.ebook.BookRepository
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

    /** Hiç karar vermediklerin. */
    NEW("Yeni"),

    /** Vadesi gelen tekrarlar. Program çalışmaya başladıkça dolar. */
    TODAY("Tekrar"),

    /** Aşağı atılan, şimdilik gerekmeyenler. */
    IGNORED("Önemsiz"),

    /** Öğrenildi denilenler; gözden geçirmek için. */
    KNOWN("Öğrendiklerim"),
}

/**
 * Kalem süzgeci.
 *
 * Kitapta mavi işaretlediklerin kelime, kırmızı işaretlediklerin ise
 * anlamadığın cümle/cümlecik. İkisi farklı iş: kelime ezberlerken cümle
 * çözümlemesi araya girmesin diye ayrı ayrı çalışılabiliyor.
 */
enum class VocabPen(val label: String) {
    BOTH("Kırmızı + mavi"),
    RED("Kırmızı (cümleler)"),
    BLUE("Mavi (kelimeler)"),
    ;

    /** Düğmeye her basışta sıradaki: her ikisi → kırmızı → mavi → her ikisi. */
    fun next(): VocabPen = when (this) {
        BOTH -> RED
        RED -> BLUE
        BLUE -> BOTH
    }
}

/** Kaynak süzgeci: tümü, yalnız kitaplar, yalnız filmler ya da tek bir başlık. */
data class VocabFilter(
    val source: VocabSource? = null,
    val sourceName: String = "",
    val pen: VocabPen = VocabPen.BOTH,
    /**
     * Yalnız kaynağı silinmiş kelimeler.
     *
     * Kitap ya da film silindiğinde işaretlemeleri kalıyordu; o kelimeler
     * listede adsız duruyor ve hiçbir kaynak çipine düşmüyordu. Toplu
     * silinebilmeleri için önce görülebilmeleri gerekiyor.
     */
    val orphan: Boolean = false,
)

/** Kart hakkında sorulan sorunun durumu. */
data class QuestionUiState(
    val word: VocabWord,
    val asked: String = "",
    val answer: String = "",
    val busy: Boolean = false,
    val error: String? = null,
)

/** Tam listedeki bir satır: kelime, kaynağı ve nerede olduğu. */
data class VocabListItem(
    val word: VocabWord,
    val status: VocabStatus?,
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
    val listCount: Int = 0,
    /** Kaynağı silinmiş kelime sayısı; sıfırsa çip hiç görünmüyor. */
    val orphanCount: Int = 0,
    /** Seçili kaynakta hiç karar verilmemiş kelime sayısı. */
    val newCount: Int = 0,
    /** Seçili kaynaktaki toplam kelime sayısı. */
    val totalCount: Int = 0,
    /** Kırmızı ve mavi kalemli kelime sayıları (kalem süzgecinden önce). */
    val redCount: Int = 0,
    val blueCount: Int = 0,
    /** Seçili kapsamdaki her kelime, alfabetik: "hepsini gör" listesi için. */
    val list: List<VocabListItem> = emptyList(),
    /** Bugün vadesi gelen kelime sayısı (kuyruk sınırından önce). */
    val dueToday: Int = 0,
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
    private val briefs: WorkBriefStore,
    private val books: BookRepository,
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
            val brief = briefFor(word)
            when (
                val result = openAi.describeWord(
                    word = word.word,
                    context = word.context,
                    passage = word.isPassage,
                    sourceName = word.sourceName,
                    brief = brief,
                )
            ) {
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

    /**
     * Eserin künyesi. İlk kelimede bir kez üretilip saklanıyor; sonraki
     * sorgular hazır künyeyi kullanıyor.
     *
     * Ağ hatası ya da metnin bulunamaması akışı durdurmuyor: künyesiz de
     * çalışıyoruz, sadece bağlam biraz zayıf oluyor.
     */
    private suspend fun briefFor(word: VocabWord): String {
        val work = word.sourceName.trim()
        if (work.isBlank()) return ""
        briefs.get(work)?.let { return it }
        val sample = runCatching { books.sampleFor(work) }.getOrDefault("")
        if (sample.isBlank()) return ""
        return when (val result = openAi.describeWork(work, sample)) {
            is AiResult.Ok -> result.value.also { briefs.put(work, it) }
            is AiResult.Failed -> ""
        }
    }

    /**
     * Kart hakkında sorulan serbest sorunun durumu.
     *
     * Hazır açıklama her zaman yetmiyor; "peki neden böyle deniyor" diye
     * sorabilmek gerekiyor.
     */
    private val _question = MutableStateFlow<QuestionUiState?>(null)
    val question: StateFlow<QuestionUiState?> = _question

    fun openQuestion(word: VocabWord) {
        _question.value = QuestionUiState(word = word)
    }

    fun closeQuestion() {
        _question.value = null
    }

    fun ask(text: String) {
        val current = _question.value ?: return
        if (current.busy || text.isBlank()) return
        _question.value = current.copy(busy = true, asked = text.trim(), answer = "", error = null)
        viewModelScope.launch {
            val word = current.word
            val brief = briefFor(word)
            when (
                val result = openAi.askAbout(
                    word = word.word,
                    question = text,
                    context = word.context,
                    sourceName = word.sourceName,
                    brief = brief,
                    card = cardSummary(word),
                )
            ) {
                is AiResult.Ok -> _question.value = _question.value?.copy(
                    busy = false,
                    answer = result.value,
                )

                is AiResult.Failed -> _question.value = _question.value?.copy(
                    busy = false,
                    error = result.reason,
                )
            }
        }
    }

    /**
     * Kartta hâlihazırda yazan bilgi.
     *
     * Soruyla birlikte gidiyor: model daha önce ne dediğini görmeden
     * cevaplayınca kartın tam tersini söyleyebiliyor ve ortada iki karşıt
     * yanıt kalıyordu. Artık ya kartı doğruluyor ya da açıkça düzeltiyor.
     */
    private fun cardSummary(word: VocabWord): String = buildString {
        if (word.meaning.isNotBlank()) append("Anlam: ").append(word.meaning).append("\n")
        if (word.definition.isNotBlank()) {
            append("Basit İngilizce: ").append(word.definition).append("\n")
        }
        word.examples.forEach { append("Not: ").append(it).append("\n") }
        word.related.forEach { append("Kalıp: ").append(it).append("\n") }
    }.trim()

    /**
     * Soruyu ve yanıtı kelimenin kartına yazar.
     *
     * Sorup öğrendiğin şey kartta kalmalı; ikinci kez aynı soruyu sormak
     * gerekmesin. Bilgi yenilenirken de silinmiyor, çünkü o senin notun.
     */
    fun saveAnswer() {
        val current = _question.value ?: return
        if (current.answer.isBlank()) return
        val note = buildString {
            if (current.asked.isNotBlank()) append(current.asked.trim()).append("\n")
            append(current.answer.trim())
        }
        repository.saveAnswer(current.word, note)
        session.value = session.value.copy(refresh = session.value.refresh + 1)
        _question.value = null
    }

    /** Eseri yanlış okuduysa künyeyi attır; sonraki sorgu yenisini üretir. */
    fun forgetBrief(word: VocabWord) {
        val work = word.sourceName.trim()
        if (work.isNotBlank()) briefs.forget(work)
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
        /**
         * Düzenlediğin kelime. Deste alfabetik sıralı olduğu için adı değişen
         * kelime bambaşka bir yere düşüyordu ve karta dönünce onu bulamıyordun;
         * bu kelime bir sonraki karara kadar destenin başında duruyor.
         */
        val pinned: String? = null,
        val filter: VocabFilter = VocabFilter(),
    )

    private val prefs = VocabPrefs(context)

    private val session = MutableStateFlow(VocabSession(filter = VocabFilter(pen = prefs.pen)))

    private val mode = MutableStateFlow(VocabMode.ALL)

    private val _swipeThreshold = MutableStateFlow(prefs.swipeThreshold)
    val swipeThreshold: StateFlow<Int> = _swipeThreshold

    private val _fontScale = MutableStateFlow(prefs.fontScale)
    val fontScale: StateFlow<Int> = _fontScale


    val uiState: StateFlow<VocabUiState> = combine(
        repository.observeProgress(),
        mode,
        // Kitapta yeni işaretlenen mavi kelime, uygulamayı yeniden başlatmadan
        // burada belirmeli; bu yüzden akış olarak dinliyoruz.
        repository.observeBookWords(),
        session,
    ) { progress, currentMode, bookWords, sessionState ->
        val words = repository.applyEnrichment(bookWords)

        // Kaynak asıl kapsam: bir kitap seçildiğinde bütün sayaçlar ve
        // bölmeler o kitabın içinde çalışıyor. Kaynak seçilmemişse her şey.
        val scoped = words.filter { word ->
            val filter = sessionState.filter
            when {
                // Kaynağı silinmişlerde başlık boş kalıyor; kelimenin
                // kendisiyle seçtiklerim karışmasın diye tür de bakılıyor.
                filter.orphan -> word.sourceName.isBlank() &&
                    word.source != VocabSource.SELECTION
                filter.sourceName.isNotBlank() -> word.sourceName == filter.sourceName
                filter.source != null -> word.source == filter.source
                else -> true
            }
        }

        // Kalem süzgeci kaynağın içinde çalışıyor: "şu kitabın kırmızıları".
        fun byPen(list: List<VocabWord>) = when (sessionState.filter.pen) {
            VocabPen.BOTH -> list
            VocabPen.RED -> list.filter { it.isPassage }
            VocabPen.BLUE -> list.filter { !it.isPassage }
        }
        val filtered = byPen(scoped)
        // Kaynak çipleri de kaleme uyuyor: kalem kırmızıyken "Filmden (212)"
        // yazıp basınca boş ekran vermek yanıltıcıydı — filmden gelen her
        // kelime mavi.
        val penWords = byPen(words)

        val schedules = progress.associateBy({ it.word }, { it.toSchedule() })

        // Sayaçlar seçili kaynağın içinden: kitap seçiliyken "öğrendiklerim"
        // bütün kelimelerin toplamını gösterirse seçim işe yaramıyor gibi
        // duruyor.
        fun statusOf(word: VocabWord) = schedules[word.word]?.status

        // "Yeni" = hiç çalışılmamış. Sağa atıp geçtiğin kelime de burada
        // kalıyor: geçmek bir çalışma değil, sadece "şimdi değil".
        fun untouched(word: VocabWord): Boolean {
            val plan = schedules[word.word] ?: return true
            return plan.lastReviewedAt == null &&
                plan.status != VocabStatus.KNOWN &&
                plan.status != VocabStatus.IGNORED
        }

        val known = filtered.count { statusOf(it) == VocabStatus.KNOWN }
        val learning = filtered.count { statusOf(it) == VocabStatus.LEARNING }
        val ignored = filtered.count { statusOf(it) == VocabStatus.IGNORED }
        val untouchedCount = filtered.count { untouched(it) }
        val nowMillis = repository.nowMillis()
        val dayStart = startOfDay(nowMillis, repository.zoneOffsetMillis(nowMillis))
        val endOfToday = dayStart + DAY_MILLIS

        // Programdaki kelimeler: vadesi gelmiş olanlar en çok bekleyenden
        // başlayarak, sonra kademesi düşük olanlar. Karıştırma yok —
        // "rastgele olmasın" istenen tam olarak buydu.
        fun scheduled(word: VocabWord) = schedules[word.word]
        val due = filtered
            .filter { word ->
                val plan = scheduled(word)
                plan != null &&
                    plan.status == VocabStatus.LEARNING &&
                    // Çalışılmamış kelimenin tekrarı olmaz.
                    plan.lastReviewedAt != null &&
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

        // Henüz çalışılmamış kelimeler. Sıra sabit, rastgele değil.
        val fresh = filtered
            .filter { untouched(it) }
            .sortedBy { it.word.lowercase() }

        val deck = when (currentMode) {
            // Tekrar yalnızca çalıştığın kelimelerden oluşuyor. Eskiden araya
            // yeni kelime serpiştiriyorduk; "bakmadığım kelimenin tekrarı
            // olmaz" — yenilerin yeri "Yeni" bölmesi.
            VocabMode.TODAY -> due
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
            VocabMode.NEW -> fresh

            VocabMode.IGNORED -> filtered.filter {
                scheduled(it)?.status == VocabStatus.IGNORED
            }.sortedBy { it.word.lowercase() }
            VocabMode.KNOWN -> filtered.filter {
                scheduled(it)?.status == VocabStatus.KNOWN
            }.sortedBy { it.word.lowercase() }
        }

        // Bu oturumda "geç" denenler en sona; program sırası bozulmuyor.
        // Düzenlediğin kelime ise en başta: elini attığın kart karşında kalsın.
        val ordered = deck
            .sortedBy { if (it.word in sessionState.skipped) 1 else 0 }
            .sortedBy { if (it.word == sessionState.pinned) 0 else 1 }

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
            newCount = untouchedCount,
            totalCount = filtered.size,
            redCount = scoped.count { it.isPassage },
            blueCount = scoped.count { !it.isPassage },
            // Liste de bölmeye uyuyor: "Öğrendiklerim"e basılıyken listede de
            // yalnız onlar olmalı. "Tümü" ise gerçekten tümü — kart destesi
            // öğrendiklerini ve önemsizleri atlıyor, liste atlamıyor.
            list = (if (currentMode == VocabMode.ALL) filtered else deck)
                .sortedBy { it.word.lowercase() }
                .map { VocabListItem(word = it, status = statusOf(it)) },
            bookCount = penWords.count { it.source == VocabSource.BOOK },
            subtitleCount = penWords.count { it.source == VocabSource.SUBTITLE },
            selectionCount = penWords.count { it.source == VocabSource.SELECTION },
            listCount = penWords.count { it.source == VocabSource.LIST },
            orphanCount = penWords.count {
                it.sourceName.isBlank() && it.source != VocabSource.SELECTION
            },
            sources = penWords
                .filter { it.sourceName.isNotBlank() }
                .map { it.source to it.sourceName }
                .distinct()
                .sortedBy { it.second.lowercase() },
            filter = sessionState.filter,
            dueToday = due.size,
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

    /** Mavi (kelime) ile kırmızı (cümle) arasında geçiş. */
    fun setPassage(word: VocabWord, passage: Boolean) {
        viewModelScope.launch {
            repository.setPassage(word, passage)
            session.value = session.value.copy(
                refresh = session.value.refresh + 1,
                turn = session.value.turn + 1,
            )
        }
    }

    /** Listeden tamamen kaldır. */
    fun delete(word: VocabWord) {
        advance(word)
        viewModelScope.launch { repository.deleteWord(word) }
    }

    /**
     * Listede görünen ne varsa hepsini siler.
     *
     * Süzgeç ne gösteriyorsa o siliniyor — "şu filmden gelenler", "kaynağı
     * silinmişler", "yalnız kırmızılar". Yüz kelimeyi tek tek silmenin
     * karşılığı bu; kapsamı daraltmak süzgecin işi.
     */
    fun deleteListed() {
        val targets = uiState.value.list.map { it.word }
        if (targets.isEmpty()) return
        viewModelScope.launch { repository.deleteWords(targets) }
    }

    /**
     * Elle düzenlenen kelime bilgisini kaydeder.
     *
     * Kelimenin ya da cümlenin kendisi değiştiyse bu artık başka bir kelime:
     * kitaptaki işaret düzeltiliyor, eski yazıma ait anlam, örnek ve
     * eşdizimler siliniyor. Kart boş kalıyor — yanlış kelimenin bilgisini
     * taşımaktansa boş durması iyi. Yenisini kartın altındaki düğmeyle
     * getiriyorsun; kendiliğinden istek atmıyoruz.
     */
    fun saveEdit(original: VocabWord, edited: VocabWord) {
        val renamed = edited.word.trim() != original.word.trim()
        if (!renamed) {
            repository.saveEdit(edited)
            session.value = session.value.copy(
                refresh = session.value.refresh + 1,
                pinned = edited.word,
            )
            return
        }
        viewModelScope.launch {
            repository.renameWord(original, edited.word)
            session.value = session.value.copy(
                refresh = session.value.refresh + 1,
                pinned = edited.word.trim(),
            )
        }
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
            val brief = briefFor(word)
            when (
                val result = openAi.describeWord(
                    word = word.word,
                    context = word.context,
                    passage = word.isPassage,
                    sourceName = word.sourceName,
                    brief = brief,
                )
            ) {
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
            val brief = briefFor(word)
            when (
                val result = openAi.describeWord(
                    word = word.word,
                    context = word.context,
                    passage = word.isPassage,
                    sourceName = word.sourceName,
                    brief = brief,
                )
            ) {
                is AiResult.Ok -> {
                    val fresh = result.value
                    repository.saveEdit(
                        word.copy(
                            meaning = word.meaning.ifBlank { fresh.meaning },
                            definition = word.definition.ifBlank { fresh.definition },
                            examples = (word.examples + fresh.examples).distinct(),
                            related = (word.related + fresh.related).distinct(),
                            root = word.root.ifBlank { fresh.root },
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
        // Kaynak değişse de kalem seçimi korunuyor: iki süzgeç birbirinden
        // bağımsız, kitap değiştirince "yalnız kırmızılar" bozulmamalı.
        val kept = filter.copy(pen = session.value.filter.pen)
        session.value = session.value.copy(filter = kept, turn = session.value.turn + 1)
    }

    /** Kalem düğmesi: her ikisi → kırmızı → mavi → her ikisi. */
    fun cyclePen() {
        val current = session.value.filter
        val next = current.pen.next()
        prefs.pen = next
        session.value = session.value.copy(
            filter = current.copy(pen = next),
            turn = session.value.turn + 1,
        )
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
            // Karar verildi: düzenlenen kelimeyi başta tutmanın anlamı kalmadı.
            pinned = null,
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

    fun setFontScale(value: Int) {
        prefs.fontScale = value
        _fontScale.value = prefs.fontScale
    }
}

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

private const val DAY_MILLIS = 86_400_000L
