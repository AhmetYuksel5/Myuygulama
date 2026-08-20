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
    val synonyms: List<String>,
    val antonyms: List<String>,
    /** Kelimenin kökeni: "morph- (Yun. morphē = şekil)". */
    val root: String,
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
        append("You are a bilingual English-Turkish lexicographer writing a study ")
        append("card for a Turkish learner of English. ")
        append("The input may be a single word OR a multi-word phrase; treat it as ")
        append("one unit. ")
        append("FIRST decide the input's own part of speech. For a multi-word ")
        append("phrase the part of speech is that of its HEAD word, which in ")
        append("English is the LAST word of a noun phrase: \"bum paychecks\" is a ")
        append("NOUN (head: paychecks), \"put up with\" is a VERB (head: put). ")
        append("The collocation labels depend on this decision, so make it before ")
        append("anything else and report it. ")
        append("Return STRICT JSON with keys: ")

        append("p (PART OF SPEECH of the input, exactly one of: noun, verb, ")
        append("adjective, adverb, phrase), ")

        append("t (Turkish meanings, 1-3, comma separated), ")
        append("d (short English definition, max 12 words, no final period), ")
        append("e (array of exactly 3 natural example sentences using it, ")
        append("6-16 words each), ")

        append("s (SYNONYMS: 2-4 words that could REPLACE the input in a sentence ")
        append("with roughly the same meaning. Apply this test to every candidate ")
        append("before deciding where it goes: if swapping it in keeps the ")
        append("sentence meaning the same, it is a SYNONYM and belongs here, not ")
        append("in r. For \"discordant\", dissonant and clashing pass the test, so ")
        append("they go here. Empty only if the language truly has no synonym), ")

        append("a (ANTONYMS: 1-3 words that mean the OPPOSITE. Empty array only ")
        append("if the word has no opposite at all — most adjectives and many ")
        append("verbs do have one, so look before giving up), ")

        append("r (RELATED: 3-5 words from the same TOPIC that are NOT ")
        append("interchangeable with the input — they fail the replacement test ")
        append("above. For \"discordant\" these would be harmony, chord, tone, ")
        append("orchestra: same field, different meaning. Never put a synonym ")
        append("here; s and r must not overlap), ")

        append("k (ROOT: the etymological root the word is built on, with its ")
        append("language and meaning, in exactly this shape: ")
        append("\"morph- (Yun. morphē = şekil)\". Use Turkish language ")
        append("abbreviations: Lat., Yun., Fr., Alm., Ar., İng. Empty string only ")
        append("if the word has no identifiable root), ")

        append("f (WORD FAMILY: 4-8 OTHER English words built on that SAME root, ")
        append("however far their meanings have drifted. These are cognates that ")
        append("share the root — NOT inflections or mechanical derivations of the ")
        append("input itself: for \"amorphous\" do not list amorphously or ")
        append("amorphousness, and never repeat the input. Write each one as ")
        append("\"word — Türkçe karşılığı\", one or two Turkish words, no ")
        append("explanation: \"metamorphosis — başkalaşım\". ")
        append("Empty array only if the root has no other descendants in English), ")

        append("x (LOOK-ALIKES: 2-3 English words that LOOK like the input — ")
        append("similar spelling or shape — even when their meaning and origin are ")
        append("completely unrelated. The point is not to warn about a likely ")
        append("mix-up; it is to carve a sharp outline of the word in memory, the ")
        append("way \"zero\" and \"Nero\" define each other by contrast. So include ")
        append("a look-alike even when confusing them is unlikely. ")
        append("Search deliberately, in this order: words sharing the input's ")
        append("first 3-4 letters; words sharing its ending; words that differ ")
        append("by one or two letters; words with the same letter skeleton. ")
        append("For \"discordant\" that search yields discard, descendant, ")
        append("redundant. Never list a word that shares the input's root — ")
        append("those belong in f, and a shared root is the opposite of what ")
        append("this section is for. Never list the input itself, and never a ")
        append("mere inflection or derivation of it: for \"discordant\", the ")
        append("words discordant, discordance and discordantly are all the same ")
        append("word and none of them belongs here. A look-alike is a ")
        append("DIFFERENT word that happens to look similar. Write each one as ")
        append("\"lookalike — Türkçe karşılığı\" and nothing else. Do NOT explain ")
        append("which letters differ; the reader sees that at a glance. Example ")
        append("for amorphous: \"amorous — aşk dolu\". ")
        append("English has half a million words: an empty x almost always means ")
        append("you did not search, not that nothing resembles the input), ")

        append("c (COLLOCATIONS grouped by grammatical pattern, like an Oxford ")
        append("Collocations Dictionary entry: array of objects with g and w. ")
        append("The allowed groups are fixed by p and you may use no others: ")
        append("p=noun -> \"fiil +\" (verbs that take it as object), ")
        append("\"sıfat +\" (adjectives that modify it), \"+ edat\" ")
        append("(prepositions that follow it); ")
        append("p=verb -> \"+ isim\" (its typical objects), \"zarf +\" ")
        append("(adverbs that modify it), \"+ edat\"; ")
        append("p=adjective -> \"+ isim\" (nouns it modifies), \"zarf +\", ")
        append("\"+ edat\"; ")
        append("p=adverb or p=phrase -> \"kalıp\" only. ")
        append("\"kalıp\" (fixed expressions containing the input) is allowed ")
        append("alongside the groups above. ")
        append("The trap to avoid, worked out: for \"bum paychecks\" p is noun, ")
        append("so \"+ isim\" and \"zarf +\" are FORBIDDEN — the right groups are ")
        append("\"fiil +\" (earn, collect, live on) and \"sıfat +\". ")
        append("Every collocate must form a real phrase WITH the input: read ")
        append("\"collocate + input\" or \"input + collocate\" aloud and drop it ")
        append("if it is not something English speakers actually say. ")
        append("w is an array of 3-6 collocates, English only, no translation, no ")
        append("article unless it belongs to the collocation. Give 2-4 groups). ")

        append("Never pad a section to reach a count: fewer good items beat filler, ")
        append("and an empty array is better than a weak entry. This does not ")
        append("apply to x — a look-alike does not need to be a likely mix-up to ")
        append("earn its place, so give x the 2-3 closest words you found. ")
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
        append("c, f, x, s, a (empty arrays), k (empty string). ")
        append("No markdown, no extra keys, no commentary.")
    }

    private fun parseWord(word: String, json: JSONObject): AiResult<WordInfo> {
        val examples = json.optJSONArray("e").toStringList()
        val related = json.optJSONArray("r").toStringList()
        val collocations = json.optJSONArray("c").toCollocations()
        val synonyms = json.optJSONArray("s").toStringList()
        val antonyms = json.optJSONArray("a").toStringList()
        val root = json.optString("k").trim()
        val family = json.optJSONArray("f").toStringList()
            .filterNot { it.headWord().equals(word.trim(), ignoreCase = true) }
        // Modelin kelimenin kendisini "benzeyen kelime" diye vermesi olan bir
        // şey; yönerge yasaklıyor ama bir de burada eliyoruz.
        val confusions = json.optJSONArray("x").toStringList()
            .filterNot { it.headWord().equals(word.trim(), ignoreCase = true) }
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
                synonyms = synonyms,
                antonyms = antonyms,
                root = root,
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

    /** "amorous — aşk dolu" satırından yalnız kelimeyi alır. */
    private fun String.headWord(): String =
        substringBefore('\u2014').substringBefore(" - ").substringBefore(';').trim()

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
