package com.ahmety.uygulama.textaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.ai.WordInfo
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.core.model.penFor
import com.ahmety.uygulama.feature.vocab.VocabRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TextActionUiState(
    val text: String = "",
    val aiConfigured: Boolean = false,
    val loading: Boolean = false,
    val info: WordInfo? = null,
    val error: String? = null,
    /** Kaydedildikten sonra kutuda görünen kısa bilgi. */
    val saved: String? = null,
    /** Soru kutusu açık mı. */
    val asking: Boolean = false,
    val question: String = "",
    val answer: String = "",
    val answering: Boolean = false,
)

/**
 * Başka bir uygulamada seçilen metnin işlendiği ekranın durumu.
 *
 * Yazmalar [NonCancellable] içinde: kullanıcı "ekle"ye basıp kutunun dışına
 * dokunduğunda etkinlik kapanıyor ve viewModelScope iptal ediliyordu; kayıt
 * yarıda kalırdı.
 */
@HiltViewModel
class TextActionViewModel @Inject constructor(
    private val client: OpenAiClient,
    private val settings: AiSettings,
    private val entryRepository: EntryRepository,
    private val vocabRepository: VocabRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TextActionUiState())
    val state: StateFlow<TextActionUiState> = _state.asStateFlow()

    private var started = false

    fun start(raw: String) {
        if (started) return
        started = true
        val text = normalize(raw)
        _state.value = TextActionUiState(text = text, aiConfigured = settings.configured)
        // Anahtar varsa kullanıcıyı bir tuşa daha bastırmıyoruz: metni seçip
        // uygulamayı açmak zaten "bunu sorgula" demek.
        if (settings.configured && text.isNotBlank()) ask()
    }

    fun ask() {
        val text = _state.value.text
        if (text.isBlank() || _state.value.loading) return
        if (!settings.configured) {
            _state.value = _state.value.copy(error = "Önce Araçlar → Yapay zekâ'dan anahtarı gir.")
            return
        }
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            // Dört kelimeden uzun bir seçim sözlük maddesi değil, anlamadığın
            // bir cümledir; yapay zekâdan çeviri ve açıklama isteniyor.
            val passage = text.split(' ').count { it.isNotBlank() } >= PASSAGE_WORDS
            when (val result = client.describeWord(text, context = text, passage = passage)) {
                is AiResult.Ok -> _state.value = _state.value.copy(
                    loading = false,
                    info = result.value,
                    error = null,
                )

                is AiResult.Failed -> _state.value = _state.value.copy(
                    loading = false,
                    error = result.reason,
                )
            }
        }
    }

    /** Bilinmeyen kelimeler destesine ekler (kitaptaki mavi işaretlemeyle aynı yer). */
    fun addToVocab() {
        val text = _state.value.text
        if (text.isBlank()) return
        viewModelScope.launch {
            withContext(NonCancellable) {
                val existing = entryRepository.observeByType(EntryType.HIGHLIGHT).first()
                val already = existing.any { it.title.trim().equals(text, ignoreCase = true) }
                if (!already) {
                    entryRepository.createEntry(
                        type = EntryType.HIGHLIGHT,
                        title = text,
                        // Seçilen metin bağlam olarak da duruyor: kartta
                        // kelimenin geçtiği yeri göstermek için elimizdeki
                        // tek şey bu. Tek kelime seçildiyse bağlam da o
                        // kelime oluyor ve kart onu ayrıca yazmıyor.
                        body = text,
                        source = HighlightRef.encode(
                            kind = HighlightRef.KIND_SELECTION,
                            sourceId = 0L,
                            // Tek kelime mavi, ifade kırmızı — yüklenen
                            // listeyle ve kitapla aynı kural.
                            color = penFor(text),
                        ),
                    )
                }
                // Yapay zekâ zaten getirdiyse kartta hazır dursun; sorulan
                // soru varsa cevabı da kartın altına yazılıyor.
                val note = savedAnswer()
                _state.value.info?.let { info ->
                    vocabRepository.saveEnrichment(
                        info.copy(
                            word = text,
                            answers = info.answers + listOfNotNull(note),
                        ),
                    )
                }
                _state.value = _state.value.copy(
                    saved = if (already) "Bu zaten kelimelerde vardı." else "Kelimelere eklendi.",
                )
            }
        }
    }


    /** Kutunun altındaki soru alanını açar/kapatır. */
    fun toggleQuestion() {
        _state.value = _state.value.copy(asking = !_state.value.asking)
    }

    fun setQuestion(value: String) {
        _state.value = _state.value.copy(question = value)
    }

    /**
     * Metin hakkında ek soru.
     *
     * Gelen açıklama her zaman yetmiyor; "burada neden bu anlama geliyor"
     * diye sorabilmek gerekiyor. Kartın o anki hâli de gönderiliyor ki
     * cevap kendisiyle çelişmesin.
     */
    fun sendQuestion() {
        val current = _state.value
        val text = current.text
        val question = current.question.trim()
        if (text.isBlank() || question.isBlank() || current.answering) return
        if (!settings.configured) {
            _state.value = current.copy(error = "Önce Araçlar → Yapay zekâ'dan anahtarı gir.")
            return
        }
        _state.value = current.copy(answering = true, error = null)
        viewModelScope.launch {
            when (val result = client.askAbout(text, question, context = text, card = cardSummary())) {
                is AiResult.Ok -> _state.value = _state.value.copy(
                    answering = false,
                    answer = result.value,
                )

                is AiResult.Failed -> _state.value = _state.value.copy(
                    answering = false,
                    error = result.reason,
                )
            }
        }
    }

    /** Kartın o anki özeti; soruyu bağlamıyla birlikte sormak için. */
    private fun cardSummary(): String {
        val info = _state.value.info ?: return ""
        return listOf(info.meaning, info.definition)
            .filter { it.isNotBlank() }
            .joinToString(" — ")
    }

    /** Soruldu ve cevaplandıysa karta yazılacak not. */
    private fun savedAnswer(): String? {
        val current = _state.value
        if (current.answer.isBlank()) return null
        return buildString {
            if (current.question.isNotBlank()) append(current.question.trim()).append("\n")
            append(current.answer.trim())
        }
    }

    /**
     * Seçim genelde satır sonları ve fazladan boşluk taşıyor; sözlüğe de
     * kayda da tek satır hâlinde gitmeli.
     */
    private fun normalize(raw: String): String =
        raw.replace(Regex("\\s+"), " ").trim().take(MAX_LENGTH)

    private companion object {
        /** Yanlışlıkla bütün bir yazının seçilmesine karşı üst sınır. */
        const val MAX_LENGTH = 400

        /** Bu kadar kelimeden uzun seçim, sözlük maddesi değil cümle sayılıyor. */
        const val PASSAGE_WORDS = 4
    }
}
