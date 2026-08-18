package com.ahmety.uygulama.core.ai

import com.ahmety.uygulama.core.model.Collocation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Bir kelime için üretilen sözlük bilgisi. */
data class WordInfo(
    val word: String,
    val meaning: String,
    val definition: String,
    val examples: List<String>,
    val related: List<String>,
    val family: List<String>,
    val confusions: List<String>,
    /**
     * Kelimenin hangi kelimelerle birlikte kullanıldığı, dilbilgisi kalıbına
     * göre gruplanmış — Oxford Collocations Dictionary mantığı.
     */
    val collocations: List<Collocation>,
)

sealed interface AiResult<out T> {
    data class Ok<T>(val value: T) : AiResult<T>
    data class Failed(val reason: String) : AiResult<Nothing>
}

/**
 * OpenAI ile konuşan ince katman.
 *
 * Anahtar yalnızca istek başlığında kullanılıyor; hiçbir yere yazılmıyor ve
 * hata mesajlarına konmuyor.
 */
@Singleton
class OpenAiClient @Inject constructor(
    private val settings: AiSettings,
) {

    private val http: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Kelimenin Türkçe karşılığını, kısa İngilizce tanımını, üç örneğini ve
     * ilgili kelimelerini üretir. [context] verilirse (kitapta geçtiği cümle)
     * anlam o bağlama göre seçilir — bir kelimenin birden çok anlamı olabiliyor.
     */
    suspend fun describeWord(
        word: String,
        context: String = "",
        passage: Boolean = false,
    ): AiResult<WordInfo> {
        // "word" tek kelime de olabilir, kitaptan seçilmiş bir öbek de.
        val key = settings.apiKey
        if (key.isBlank()) return AiResult.Failed("OpenAI anahtarı girilmemiş.")

        val instruction = if (passage) passageInstruction() else wordInstruction()
        val userText = if (context.isBlank()) {
            "Input: $word"
        } else {
            "Input: $word\nIt appeared in this sentence, so prefer the meaning that fits it:\n$context"
        }

        val payload = JSONObject().apply {
            put("model", MODEL)
            put("temperature", 0.3)
            put("response_format", JSONObject().put("type", "json_object"))
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", instruction))
                    .put(JSONObject().put("role", "user").put("content", userText)),
            )
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(ENDPOINT)
                    .addHeader("Authorization", "Bearer $key")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use AiResult.Failed(readableError(response.code, body))
                    }
                    val content = JSONObject(body)
                        .getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                    parseWord(word, JSONObject(content))
                }
            }.getOrElse { error ->
                AiResult.Failed("Bağlantı kurulamadı: ${error.message ?: "bilinmeyen hata"}")
            }
        }
    }

    /** Tek kelime ya da öbek için sözlük maddesi. */
    private fun wordInstruction(): String = buildString {
        append("You are a bilingual English-Turkish lexicographer. ")
        append("The input may be a single word OR a multi-word phrase taken ")
        append("from a book; treat it as one unit. ")
        append("Return STRICT JSON with keys: ")
        append("t (Turkish meanings, 1-3, comma separated), ")
        append("d (short English definition, max 12 words, no final period), ")
        append("e (array of exactly 3 natural example sentences using it, 6-16 words each), ")
        append("r (RELATED: 5-6 words from the same semantic field — words that ")
        append("naturally co-occur in the same topic, NOT a synonym list. ")
        append("If a true synonym belongs there, include it here; no separate ")
        append("synonyms section), ")
        append("f (WORD FAMILY: derivations of the same root with a part-of-speech ")
        append("tag in Turkish — f for fiil, i for isim, s for sıfat, z for zarf; ")
        append("format \"decision (i)\". Empty array if the word has no common ")
        append("derivations), ")
        append("x (DON'T CONFUSE: only if there is a word Turkish learners commonly ")
        append("confuse with this one — false friend, similar spelling, or close ")
        append("meaning with different usage. Format \"word — tek cümlelik fark\" ")
        append("in Turkish. Empty array if there is no such word), ")
        append("c (collocations grouped by grammatical pattern, like an Oxford ")
        append("Collocations Dictionary entry: array of objects with g and w. ")
        append("g is the pattern label in Turkish, chosen from exactly these: ")
        append("\"fiil +\" (verbs used with this noun), \"sıfat +\" (adjectives used ")
        append("with this noun), \"zarf +\" (adverbs used with this adjective or verb), ")
        append("\"+ isim\" (nouns this adjective or verb typically goes with), ")
        append("\"+ edat\" (prepositions), \"kalıp\" (fixed expressions). ")
        append("w is an array of 3-6 collocates, English only, no translation, ")
        append("no article unless it is part of the collocation. ")
        append("Give 2-4 groups, only patterns that genuinely apply to this word's ")
        append("part of speech). ")
        append("Never pad a section to reach a count: fewer good items beat filler, ")
        append("and an empty array is better than a weak entry. ")
        append("No markdown, no extra keys, no commentary.")
    }

    /**
     * Anlaşılmayan bir cümle/cümlecik için. Sözlük maddesi işe yaramaz:
     * burada gereken çeviri, sade bir İngilizce karşılık ve cümleyi zor
     * kılan yapının açıklaması.
     */
    private fun passageInstruction(): String = buildString {
        append("You are a bilingual English-Turkish teacher. ")
        append("The input is a sentence or clause a Turkish learner did not understand. ")
        append("Any surrounding text is context only — explain THE INPUT itself. ")
        append("Return STRICT JSON with keys: ")
        append("t (natural Turkish translation of the input), ")
        append("d (the same idea in simple English, max 15 words), ")
        append("e (array of 1-3 short notes in Turkish explaining what makes it hard: ")
        append("idiom, phrasal verb, inversion, ellipsis, tense — name it and explain), ")
        append("r (array of 0-4 idioms or phrasal verbs that appear in it, ")
        append("each as \"expression — Turkish meaning\"), ")
        append("c, f, x (empty arrays). ")
        append("No markdown, no extra keys, no commentary.")
    }

    private fun parseWord(word: String, json: JSONObject): AiResult<WordInfo> {
        val examples = json.optJSONArray("e").toStringList()
        val related = json.optJSONArray("r").toStringList()
        val collocations = json.optJSONArray("c").toCollocations()
        val family = json.optJSONArray("f").toStringList()
        val confusions = json.optJSONArray("x").toStringList()
        val meaning = json.optString("t").trim()
        if (meaning.isBlank() && examples.isEmpty()) {
            return AiResult.Failed("Yanıt anlaşılamadı.")
        }
        return AiResult.Ok(
            WordInfo(
                word = word,
                meaning = meaning,
                definition = json.optString("d").trim(),
                examples = examples,
                related = related,
                family = family,
                confusions = confusions,
                collocations = collocations,
            ),
        )
    }

    /** `c` alanı: kalıp adı + o kalıptaki kelimeler. */
    private fun JSONArray?.toCollocations(): List<Collocation> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val group = optJSONObject(index) ?: return@mapNotNull null
            val pattern = group.optString("g").trim()
            val words = group.optJSONArray("w").toStringList()
            if (pattern.isBlank() || words.isEmpty()) null else Collocation(pattern, words)
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            optString(index).trim().takeIf { it.isNotBlank() }
        }
    }

    /** Anahtarı asla mesaja koymuyoruz; yalnızca ne yapılacağını söylüyoruz. */
    private fun readableError(code: Int, body: String): String = when (code) {
        401 -> "Anahtar reddedildi. Ayarlardan yeniden gir."
        429 -> "Kota doldu ya da çok sık istek gönderildi."
        in 500..599 -> "OpenAI tarafında geçici bir sorun var."
        else -> {
            val message = runCatching {
                JSONObject(body).getJSONObject("error").getString("message")
            }.getOrNull()
            "İstek başarısız ($code)" + if (message != null) ": $message" else "."
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"
        const val MODEL = "gpt-4o-mini"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
