package com.ahmety.uygulama.textaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.AiSettings
import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.ai.WordInfo
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.core.model.penFor
import com.ahmety.uygulama.feature.reader.ReaderRepository
import com.ahmety.uygulama.feature.reader.SaveArticleResult
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
    /**
     * Paylaşılan şey bir sayfa bağlantısıysa adres burada durur ve kutu
     * kelime kutusu olmaktan çıkıp Pocket kutusuna dönüşür. Boşsa her şey
     * eskisi gibi: seçilen metin kelime olarak işleniyor.
     */
    val url: String? = null,
    /** Sayfanın paylaşımdan gelen adı; yoksa adresin alan adı. */
    val pageTitle: String = "",
    val savingPage: Boolean = false,
    val aiConfigured: Boolean = false,
    val loading: Boolean = false,
    val info: WordInfo? = null,
    val error: String? = null,
    /**
     * Eklenirken kullanılacak kalem. Seçime bakarak kendiliğinden
     * belirleniyor ama kullanıcı çevirebiliyor.
     */
    val pen: HighlightColor = HighlightColor.BLUE,
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
    private val readerRepository: ReaderRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TextActionUiState())
    val state: StateFlow<TextActionUiState> = _state.asStateFlow()

    /**
     * En son işlenen paylaşım. Etkinlik `singleTask` olduğu için kutu
     * açıkken ikinci bir metin gelebiliyor; o zaman etkinlik yeniden
     * kurulmadığından yalnız bir "başladı" bayrağı yeni metni yutuyordu.
     */
    private var started: String? = null

    fun start(raw: String, subject: String? = null, fromShare: Boolean = false) {
        if (started == raw) return
        started = raw
        val text = normalize(raw)
        val url = if (fromShare) pageUrl(raw) else null
        _state.value = TextActionUiState(
            text = text,
            url = url,
            pageTitle = url?.let { pageTitle(raw, it, subject) }.orEmpty(),
            aiConfigured = settings.configured,
            // Tek kelime mavi, ifade kırmızı. Başlangıç değeri; kutunun
            // üstündeki daireden çevrilebiliyor.
            pen = penFor(text),
        )
        // Anahtar varsa kullanıcıyı bir tuşa daha bastırmıyoruz: metni seçip
        // uygulamayı açmak zaten "bunu sorgula" demek. Sayfa paylaşımında
        // sorulacak bir kelime yok; adresi yapay zekâya sormak boşuna.
        if (url == null && settings.configured && text.isNotBlank()) ask()
    }

    /**
     * Paylaşılan metin bir sayfa bağlantısı mı?
     *
     * Tarayıcıdan sayfa paylaşınca gelen şey ya yalnızca adres oluyor ya da
     * "Başlık<yeni satır>adres". Adresin dışında kalan kısım bir başlığı
     * aşacak kadar uzunsa bu bir sayfa değil, içinde link geçen bir metindir
     * ve kelime kutusunda kalması gerekir.
     */
    private fun pageUrl(raw: String): String? {
        val url = readerRepository.findUrl(raw) ?: return null
        val rest = raw.replace(url, " ").replace(Regex("\\s+"), " ").trim()
        return url.takeIf { rest.length <= MAX_TITLE_LENGTH }
    }

    /** Kutunun tepesinde görünecek ad: paylaşımın başlığı, yoksa alan adı. */
    private fun pageTitle(raw: String, url: String, subject: String?): String {
        val rest = raw.replace(url, " ").replace(Regex("\\s+"), " ").trim()
        val named = rest.ifBlank { subject?.trim().orEmpty() }
        if (named.isNotBlank()) return named
        return url.substringAfter("://").substringBefore('/')
    }

    /**
     * Sayfayı Pocket'a indirir.
     *
     * [NonCancellable]: indirme birkaç saniye sürüyor, kullanıcı bu sırada
     * kutunun dışına dokunursa etkinlik kapanır ve kayıt yarıda kalırdı.
     */
    fun saveToPocket() {
        val url = _state.value.url ?: return
        if (_state.value.savingPage) return
        _state.value = _state.value.copy(savingPage = true, error = null, saved = null)
        viewModelScope.launch {
            withContext(NonCancellable) {
                when (val result = readerRepository.saveFromUrl(url)) {
                    is SaveArticleResult.Saved -> _state.value = _state.value.copy(
                        savingPage = false,
                        saved = "Pocket'a eklendi: ${result.title}",
                    )

                    is SaveArticleResult.Failed -> _state.value = _state.value.copy(
                        savingPage = false,
                        error = result.reason,
                    )
                }
            }
        }
    }

    /**
     * "Bu bir sayfa değil" dediğimiz yol: kutu kelime kutusuna dönüyor.
     * Ayırma kuralı basit olduğu için yanılabiliyor; elle geri dönebilmek
     * gerekiyor.
     */
    fun useAsWord() {
        val current = _state.value
        if (current.url == null) return
        _state.value = current.copy(url = null, error = null, saved = null)
        if (settings.configured && current.text.isNotBlank()) ask()
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
            // Kalem hangisiyse sorgu da o kalıpta: kırmızı "anlamadığım
            // ifade" demek ve çeviri, sade İngilizce, içindeki kalıplar
            // isteniyor; mavi ise sözlük maddesi.
            val passage = _state.value.pen == HighlightColor.RED
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
                            color = _state.value.pen,
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


    /**
     * Kalemi çevirir: kırmızı ↔ mavi.
     *
     * Elde bir açıklama varsa atılıp yenisi isteniyor. Çevirmenin sebebi
     * zaten "bu yanlış kalıpta geldi" olduğu için eski metni bırakmak
     * kartın kendisiyle çelişmesi demek olurdu.
     */
    fun togglePen() {
        val current = _state.value
        val next = if (current.pen == HighlightColor.RED) {
            HighlightColor.BLUE
        } else {
            HighlightColor.RED
        }
        _state.value = current.copy(pen = next, info = null, error = null)
        if (settings.configured && current.text.isNotBlank()) ask()
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

        /** Adresin yanındaki metin bundan uzunsa paylaşılan şey bir sayfa değil. */
        const val MAX_TITLE_LENGTH = 160
    }
}
