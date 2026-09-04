package com.ahmety.uygulama.feature.ebook

import com.ahmety.uygulama.core.ai.AiResult
import com.ahmety.uygulama.core.ai.OpenAiClient
import com.ahmety.uygulama.core.designsystem.WordGloss
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * İşaretlemeden önceki kısa bakış.
 *
 * Kelime bilinmediği için işaretleniyor, ama çoğu zaman bilinen bir kelime
 * çıkıyor; kutuda bir satırlık karşılık görünürse o karar orada veriliyor
 * ve deste gereksiz kartla dolmuyor.
 *
 * Aynı kelime aynı okuma boyunca bir kez soruluyor: sayfayı ileri geri
 * gezerken aynı isteği tekrar tekrar göndermenin karşılığı yok.
 */
class GlossLookup(private val openAi: OpenAiClient) {

    private val cache = mutableMapOf<String, String>()

    /**
     * [state] akışını doldurur: önce "bakılıyor", sonra karşılık ya da hata.
     * Anahtar girilmemişse hiç istek atılmıyor ve kutuda o satır çıkmıyor.
     */
    suspend fun into(
        state: MutableStateFlow<WordGloss>,
        word: String,
        context: String,
        sourceName: String = "",
    ) {
        val key = word.trim().lowercase()
        if (key.isEmpty()) {
            state.value = WordGloss()
            return
        }
        cache[key]?.let {
            state.value = WordGloss(text = it)
            return
        }
        state.value = WordGloss(busy = true)
        when (val result = openAi.glossWord(word.trim(), context, sourceName)) {
            is AiResult.Ok -> {
                cache[key] = result.value
                state.value = WordGloss(text = result.value)
            }

            is AiResult.Failed -> state.value = WordGloss(error = result.reason)
        }
    }
}
