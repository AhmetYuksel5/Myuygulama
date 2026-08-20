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
        sourceName: String = "",
        brief: String = "",
    ): AiResult<WordInfo> {
        // "word" tek kelime de olabilir, kitaptan seçilmiş bir öbek de.
        val key = settings.apiKey
        if (key.isBlank()) return AiResult.Failed("OpenAI anahtarı girilmemiş.")

        val instruction = if (passage) passageInstruction() else wordInstruction()
        // Kitabın ya da filmin adı da gidiyor: aynı cümle bir mafya filminde
        // ve bir iş kitabında farklı şey demek olabiliyor, argo ve göndermeler
        // ancak eseri bilerek çözülüyor.
        val userText = buildString {
            append("Input: ").append(word)
            if (sourceName.isNotBlank()) {
                append("\nIt is from this book or film: ").append(sourceName)
            }
            // Eserin künyesi: dönem, kişiler, dilin düzeyi. Metnin tamamını
            // göndermenin ucuz ve kalıcı karşılığı.
            if (brief.isNotBlank()) {
                append("\nBackground on that work, for YOUR disambiguation only — ")
                append("never write about it, never mention the genre: ")
                append(brief)
            }
            if (context.isNotBlank()) {
                append("\nIt appeared in this passage, so prefer the reading that fits it:\n")
                append(context)
            }
        }

        val payload = JSONObject().apply {
            put("model", settings.model)
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

    /**
     * Eserin künyesini çıkarır: kitabın ya da filmin kısa tanıtımı.
     *
     * Metinden bir örnek gönderiliyor — altyazının tamamı, kitapta ilk
     * bölümler. Dönen metin cihazda saklanıp bundan sonraki her kelime
     * sorgusuna ekleniyor, yani bu istek eser başına bir kez atılıyor.
     */
    suspend fun describeWork(title: String, sample: String): AiResult<String> {
        val key = settings.apiKey
        if (key.isBlank()) return AiResult.Failed("OpenAI anahtarı girilmemiş.")
        if (sample.isBlank()) return AiResult.Failed("Eserin metni bulunamadı.")

        val instruction = buildString {
            append("You brief a Turkish-English dictionary assistant on a work ")
            append("so that it can read individual lines correctly later. ")
            append("From the sample, write at most 150 words in TURKISH covering: ")
            append("türü ve dönemi; geçtiği yer ve çevre; kimler var ve ")
            append("aralarındaki ilişki; dilin düzeyi (argo, ağız, resmî, ")
            append("eskimiş); tekrar eden argo sözcükler ve göndermeler ve ne ")
            append("anlama geldikleri. ")
            append("Write it as dense running prose a machine will read, not as ")
            append("a review: no praise, no plot summary, no markdown, no lists. ")
            append("If the sample is too thin to tell, say only what you can.")
        }

        val payload = JSONObject().apply {
            put("model", settings.model)
            put("temperature", 0.2)
            put(
                "messages",
                JSONArray()
                    .put(JSONObject().put("role", "system").put("content", instruction))
                    .put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", "Work: $title\n\nSample:\n$sample"),
                    ),
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
                        .trim()
                    if (content.isBlank()) {
                        AiResult.Failed("Künye boş döndü.")
                    } else {
                        AiResult.Ok(content)
                    }
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
        append("this section is for. Never list the input itself and never an ")
        append("INFLECTION of it — a form that only adds a grammatical ending ")
        append("(-s, -es, -ed, -ing) is the same word, so for \"discard\" do not ")
        append("list discards, discarded or discarding. A DERIVATION is fine ")
        append("(it is a different word and easy to mix up): discordance and ")
        append("discordantly may appear for \"discordant\". Write each one as ")
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
        append("If you are told which book or film it comes from, use that: the ")
        append("same line can mean different things in a mob film and in a ")
        append("business book, and slang, allusions and running jokes only make ")
        append("sense once you know the work. ")
        append("Return STRICT JSON with keys: ")

        append("d (the same idea in simple English, max 15 words. It covers the ")
        append("INPUT and nothing else), ")

        append("t (natural Turkish translation of the INPUT and nothing else. ")
        append("Do not carry information from the surrounding context into it: ")
        append("if the input is \"he could be a stool\" and the context is ")
        append("\"Another few minutes, he could be a stool\", then t is ")
        append("\"muhbir olabilir\" — NOT \"biraz daha beklersek muhbir olabilir\". ")
        append("t and d must say the same thing about the same words: write d ")
        append("first, then make t the Turkish of exactly that. If they disagree, ")
        append("t is the one to fix), ")

        append("e (array of 0-2 notes in Turkish. Write a note ONLY when a ")
        append("learner who knows every word separately would still read the ")
        append("sentence wrongly: an idiom whose meaning is not the sum of its ")
        append("parts, a word used in an unexpected sense, something left out. ")
        append("If the sentence means exactly what it says, return an EMPTY ")
        append("array — that is the normal case, not a failure. ")
        append("Do not restate the sentence. Do not label its speech act ")
        append("(\"bir tavsiye\", \"bir soru\"). Do not mention word order. ")
        append("Above all: never decorate a note with the genre or the work. ")
        append("Sentences like \"mafya ortamında böyle kullanılır\" or ")
        append("\"bu tür filmlerde ...\" are almost always invented, and the ")
        append("reader is tired of reading them on every card. A wig advert is ")
        append("a wig advert even in a mob film. Mention the world of the work ")
        append("only if the expression truly belongs to it and would otherwise ")
        append("be misread. ")
        append("When a note IS warranted, teach the expression: what it ")
        append("literally says, when an English speaker reaches for it, what ")
        append("register it belongs to. Grammar labels alone ")
        append("(\"present perfect kullanılmış\") teach nothing; say what that ")
        append("choice does to the meaning), ")
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
            .filterNot { sameWord(it.headWord(), word.trim()) }
        // Kelimenin kendisi ve çekimleri ("discard / discarded") benzeyen
        // kelime değil, aynı kelime. Türevleri ("discordance") kalabiliyor:
        // onlar ayrı birer kelime ve karıştırılmaya çok müsait.
        val confusions = json.optJSONArray("x").toStringList()
            .filterNot { sameWord(it.headWord(), word.trim()) }
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

    /**
     * İki yazım aynı kelimenin biçimleri mi.
     *
     * Yalnız çekim ekleri: çokluk, geçmiş zaman, -ing. Türev ekleri
     * (-ance, -ly, -ness) kasıtla dışarıda — onlar ayrı kelime.
     */
    private fun sameWord(candidate: String, input: String): Boolean {
        val a = candidate.lowercase()
        val b = input.lowercase()
        if (a == b) return true
        return inflections(b).contains(a) || inflections(a).contains(b)
    }

    private fun inflections(base: String): Set<String> {
        if (base.length < 3) return emptySet()
        val forms = mutableSetOf(base + "s", base + "es", base + "ed", base + "d", base + "ing")
        if (base.endsWith("e")) {
            val stem = base.dropLast(1)
            forms += stem + "ing"
            forms += stem + "ed"
        }
        if (base.endsWith("y")) {
            val stem = base.dropLast(1)
            forms += stem + "ies"
            forms += stem + "ied"
        }
        val last = base.last()
        if (last !in "aeiou" && base.length >= 3 && base[base.length - 2] in "aeiou") {
            forms += base + last + "ing"
            forms += base + last + "ed"
        }
        return forms
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

        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
