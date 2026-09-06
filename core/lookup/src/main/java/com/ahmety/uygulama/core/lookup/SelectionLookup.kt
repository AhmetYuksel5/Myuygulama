package com.ahmety.uygulama.core.lookup

import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.ai.WorkBriefStore
import com.ahmety.uygulama.core.designsystem.WordGloss
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Seçim kutusunun arkasındaki durum: karşılık, soru cevabı, kelime kartı.
 *
 * Üç okuma ekranı (e-kitap, PDF, Pocket) aynı kutuyu açıyor; her birinin
 * kendi ViewModel'inde aynı üç akışı ve aynı altı fonksiyonu yazmak,
 * birinde yapılan değişikliğin ötekilere gitmemesi demekti — Pocket bir
 * süre geride kaldı. ViewModel bunu bir alan olarak tutuyor, ekran
 * [SelectionDialogs] ile çiziyor.
 *
 * [sourceName] her seferinde çağrılıyor: eserin adı ViewModel'e sonradan
 * geliyor, kurulurken bilinmiyor.
 */
class SelectionLookup(
    openAi: OpenAiClient,
    briefs: WorkBriefStore,
    private val scope: CoroutineScope,
    private val sourceName: () -> String,
) {
    private val lookup = GlossLookup(openAi, briefs)

    /** Kutuda gösterilen bir satırlık karşılık. */
    private val _gloss = MutableStateFlow(WordGloss())
    val gloss: StateFlow<WordGloss> = _gloss.asStateFlow()

    /** "Soru sor" ile gelen cevap. */
    private val _answer = MutableStateFlow(WordGloss())
    val answer: StateFlow<WordGloss> = _answer.asStateFlow()

    /** Okurken açılan kelime kartı. */
    private val _detail = MutableStateFlow<WordDetail?>(null)
    val detail: StateFlow<WordDetail?> = _detail.asStateFlow()

    /** Kutu açılınca karşılığı sorar; karar buna bakılarak veriliyor. */
    fun lookUp(word: String, context: String) {
        scope.launch { lookup.into(_gloss, word, context, sourceName()) }
    }

    fun ask(word: String, context: String, question: String) {
        if (_answer.value.busy) return
        scope.launch { lookup.ask(_answer, word, context, question, sourceName()) }
    }

    fun openDetail(word: String, context: String) {
        scope.launch { lookup.detail(_detail, word, context, sourceName()) }
    }

    /** Karta üç örnek daha ekler. */
    fun moreExamples() {
        val current = _detail.value ?: return
        if (current.busy) return
        scope.launch {
            lookup.detail(
                state = _detail,
                word = current.word,
                context = current.context,
                sourceName = sourceName(),
                more = current.info,
            )
        }
    }

    fun closeDetail() {
        _detail.value = null
    }

    /** Kutu kapanınca: bir sonraki seçim öncekinin karşılığıyla açılmasın. */
    fun clear() {
        _gloss.value = WordGloss()
        _answer.value = WordGloss()
    }
}
