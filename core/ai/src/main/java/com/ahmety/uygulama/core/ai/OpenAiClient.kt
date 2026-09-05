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
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Bir kelime için üretilen sözlük bilgisi. */
data class WordInfo(
    val word: String,
    val meaning: String,
    val definition: String,
    /** Arapçada harekeli yazım, okunuş ve ezberlenecek biçimler. */
    val reading: String = "",
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
    /** Kullanıcının kart üstünde sorup kaydettiği sorular ve yanıtları. */
    val answers: List<String> = emptyList(),
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

        // Dili kelimenin kendisinden anlıyoruz: Arap harfi varsa Arapça
        // yönergesi. Böylece iki dil aynı destede yan yana durabiliyor ve
        // kayıtlara bir "dil" alanı eklemek gerekmiyor.
        val arabic = word.any { it in '\u0600'..'\u06FF' }
        val instruction = when {
            passage -> passageInstruction(arabic)
            arabic -> arabicWordInstruction()
            else -> wordInstruction()
        }
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
     * Kelimenin bir satırlık karşılığı.
     *
     * [describeWord] ile aynı şey değil: o kartın tamamını üretiyor ve
     * saniyeler sürüyor. Burada amaç işaretlemeden önceki o kısa bakış —
     * "bunu zaten biliyor muyum?" — bu yüzden yanıt tek satır ve model
     * her seferinde en ucuzu.
     */
    suspend fun glossWord(
        word: String,
        context: String = "",
        sourceName: String = "",
    ): AiResult<String> {
        val key = settings.apiKey
        if (key.isBlank()) return AiResult.Failed("OpenAI anahtarı girilmemiş.")
        if (word.isBlank()) return AiResult.Failed("Kelime boş.")

        val instruction = buildString {
            append("Translate the given English text into Turkish, AS IT IS USED ")
            append("in the passage. ")
            // Tek kelimede karşılık, öbekte çeviri isteniyor: ikisine tek
            // bir uzunluk vermek yanlıştı. Kısa tutmayı emretmek uzun bir
            // seçimde özete dönüyordu — cümlenin yarısı kayboluyordu.
            append("A single word gets its Turkish equivalent(s) in a few words. ")
            append("Anything longer gets a COMPLETE translation: translate every ")
            append("part of it, do not summarise, do not shorten, do not leave ")
            append("anything out. The translation may be as long as the original. ")
            append("If the sense is figurative or idiomatic, give that sense, not ")
            append("the literal one, and translate a phrase as a phrase rather ")
            append("than word by word. ")
            append("No quotation marks, no markdown, no preamble, and never ")
            append("repeat the English text itself.")
        }

        val userText = buildString {
            append("Input: ").append(word)
            if (context.isNotBlank()) append("\nPassage: ").append(context)
            if (sourceName.isNotBlank()) append("\nFrom: ").append(sourceName)
        }

        val payload = JSONObject().apply {
            // Kısa bakış her zaman ucuz modelle: kart üretimi için seçilen
            // büyük model bir satırlık karşılık için gereksiz pahalı.
            put("model", AiSettings.GLOSS_MODEL)
            put("temperature", 0.2)
            // Uzun bir seçimin çevirisi kesilmesin diye geniş bir sınır;
            // tek kelimede zaten birkaç belirteç harcanıyor.
            put("max_tokens", 700)
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
                        .trim()
                        .trim('"')
                    if (content.isBlank()) {
                        AiResult.Failed("Karşılık gelmedi.")
                    } else {
                        AiResult.Ok(content)
                    }
                }
            }.getOrElse { error ->
                AiResult.Failed("Bağlantı kurulamadı: ${error.message ?: "bilinmeyen hata"}")
            }
        }
    }

    /**
     * Kart hakkında serbest soru.
     *
     * Hazır açıklama her zaman yetmiyor: "peki neden böyle deniyor",
     * "bunu ben nerede kullanırım" gibi sorular kalıyor. Kelimenin kendisi,
     * geçtiği cümle ve eserin künyesi soruyla birlikte gidiyor, yani soruyu
     * baştan anlatmak gerekmiyor.
     */
    suspend fun askAbout(
        word: String,
        question: String,
        context: String = "",
        sourceName: String = "",
        brief: String = "",
        card: String = "",
    ): AiResult<String> {
        val key = settings.apiKey
        if (key.isBlank()) return AiResult.Failed("OpenAI anahtarı girilmemiş.")
        if (question.isBlank()) return AiResult.Failed("Soru boş.")

        val instruction = buildString {
            append("You are a bilingual English-Turkish teacher. The reader is ")
            append("studying an English word or sentence and has a follow-up ")
            append("question about it. Answer IN TURKISH, directly, at most 120 ")
            append("words, for an adult who already reads English at an ")
            append("intermediate level. ")
            append("If the answer involves a figurative sense, give the literal ")
            append("meaning and how the sense travelled from it, not only the ")
            append("result — but say it in ordinary Turkish, never using the ")
            append("word \"köprü\" or any other jargon. ")
            append("Never explain that a swear word is rude or sexual; the reader ")
            append("knows. Use the background on the work only to pick the right ")
            append("reading — do not talk about the work or its genre. ")
            append("You are shown what the study card already says. If your answer ")
            append("agrees with it, do not repeat it — add what the card does not ")
            append("cover. If it CONTRADICTS the card, say so in the first ")
            append("sentence (\"karttaki not yanlış\" ya da \"kart doğru\") and ")
            append("explain which reading is right and why; never leave two ")
            append("opposite answers standing side by side. ")
            append("Do not simply agree with the way the question is framed: if ")
            append("its premise is wrong, say that first. Both readings being ")
            append("possible is also an answer — say which one this line means. ")
            append("No markdown, no lists, no preamble.")
        }

        val userText = buildString {
            append("Expression: ").append(word)
            if (context.isNotBlank()) append("\nIt appeared here: ").append(context)
            if (sourceName.isNotBlank()) append("\nFrom: ").append(sourceName)
            if (brief.isNotBlank()) append("\nBackground (do not discuss): ").append(brief)
            if (card.isNotBlank()) append("\nWhat the card already says:\n").append(card)
            append("\nQuestion: ").append(question)
        }

        val payload = JSONObject().apply {
            put("model", settings.model)
            put("temperature", 0.3)
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
                        .trim()
                    if (content.isBlank()) {
                        AiResult.Failed("Yanıt boş döndü.")
                    } else {
                        AiResult.Ok(content)
                    }
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

    /**
     * Arapça kelime maddesi.
     *
     * İngilizcedekinin çevirisi değil: Arapçada öğrencinin ezberlediği şey
     * başka. Harekesiz yazım okunuşu vermiyor, isimde çoğul ve fiilde mastar
     * kuralsız, kök üç sessizden oluşuyor ve aynı kökten türeyenler gerçek
     * bir aile kuruyor — İngilizcedeki köken bölümünün Arapçada karşılığı
     * çok daha güçlü. Şekil benzerliği de burada daha kritik: noktası
     * değişen iki harf ayrı kelime yapıyor.
     */
    private fun arabicWordInstruction(): String = buildString {
        append("You are a bilingual Arabic-Turkish lexicographer writing a study ")
        append("card for a Turkish learner of Arabic. The input may be a single ")
        append("word or a phrase; treat it as one unit. Modern Standard Arabic is ")
        append("the default, but if the input is dialect, say so and give the MSA ")
        append("equivalent. ")
        append("Return STRICT JSON with keys: ")

        append("t (Turkish meanings, 1-3, comma separated. Write the Turkish a ")
        append("Turkish speaker would actually say, not a word-by-word gloss), ")

        append("y (the memorisation line, in exactly this order separated by ")
        append("\" — \": the input FULLY VOWELLED with harakat; its Latin ")
        append("transcription; then for a noun its plural, for a verb its ")
        append("maṣdar and present tense. Example for كتاب: ")
        append("\"كِتَاب — kitāb — ج. كُتُب\". Example for كتب: ")
        append("\"كَتَبَ — kataba — يَكْتُب، الكِتَابَة\". This line is the single ")
        append("most useful thing on the card: unvowelled Arabic does not show ")
        append("its own pronunciation and Arabic plurals are irregular), ")

        append("d (a short definition in SIMPLE Arabic, max 12 words, fully ")
        append("understandable to an intermediate learner), ")

        append("e (array of exactly 3 natural Arabic example sentences using it, ")
        append("6-14 words each, with harakat on the input word only), ")

        append("s (SYNONYMS: 2-4 Arabic words that could replace the input with ")
        append("roughly the same meaning), ")

        append("a (ANTONYMS: 1-3 Arabic opposites; empty only if there is none), ")

        append("r (RELATED: 3-5 Arabic words from the same topic that are NOT ")
        append("interchangeable with the input), ")

        append("k (ROOT: the triliteral (or quadriliteral) root, written with ")
        append("spaces between the radicals and then its core sense in Turkish, ")
        append("in exactly this shape: \"ك ت ب (yazmak)\". Empty string only for ")
        append("borrowed words with no Arabic root, and then say so), ")

        append("f (WORD FAMILY: 4-8 OTHER words built on that SAME root, the ")
        append("place where Arabic rewards a learner most. Give the derived ")
        append("forms a learner actually meets — the maṣdar, the active and ")
        append("passive participle, the noun of place, the instrument noun — ")
        append("not a mechanical list. Write each as \"kelime — Türkçe\": ")
        append("\"مَكْتَب — yazıhane, ofis\". Never repeat the input), ")

        append("x (LOOK-ALIKES: 2-3 Arabic words that LOOK like the input on the ")
        append("page even though their meaning and root are unrelated. In Arabic ")
        append("this matters more than in Latin script: words differ by a single ")
        append("dot or by letters that share a shape (ب ت ث، ج ح خ، د ذ، ر ز، ")
        append("س ش، ص ض، ط ظ، ع غ). Look for exactly that kind of pair — ")
        append("بَحَث/بَحَت، ضَلَّ/ظَلَّ. Write each as \"kelime — Türkçe\" and ")
        append("nothing else; do not spell out which letter differs, the reader ")
        append("sees it. Never list the input itself or a form of it), ")

        append("c (COLLOCATIONS grouped by grammatical pattern: array of objects ")
        append("with g and w. Decide the input's part of speech first, then use ")
        append("only these groups: for a NOUN \"fiil +\" (verbs that take it as ")
        append("object), \"sıfat +\" (adjectives that describe it), ")
        append("\"tamlama\" (the nouns it is commonly annexed to, iḍāfa); ")
        append("for a VERB \"+ isim\" (its typical objects), \"+ harf-i cer\" ")
        append("(the preposition it governs — in Arabic this changes the meaning ")
        append("and must be learned with the verb), \"zarf +\"; ")
        append("for an ADJECTIVE \"+ isim\", \"zarf +\". ")
        append("w is an array of 3-6 collocates, Arabic only, no translation. ")
        append("Give 2-4 groups), ")

        append("Never pad a section to reach a count: fewer good items beat ")
        append("filler. This does not apply to x — give the 2-3 closest ")
        append("look-alikes you found. ")
        append("No markdown, no extra keys, no commentary.")
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
    private fun passageInstruction(arabic: Boolean = false): String = buildString {
        if (arabic) {
            append("You are a bilingual Arabic-Turkish teacher. ")
            append("The input is an Arabic sentence or clause a Turkish learner ")
            append("did not understand. Subtitles are often in a dialect rather ")
            append("than Modern Standard Arabic; when the line is dialect, say ")
            append("which one and give the MSA equivalent. ")
        } else {
            append("You are a bilingual English-Turkish teacher. ")
        }
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
        append("t is the one to fix. ")
        append("Write the Turkish a Turkish speaker would actually SAY in ")
        append("that situation, not a word-by-word gloss: \"on the house\" ")
        append("is \"müessesenin ikramı\", not \"bedava\"; \"it is on me\" ")
        append("is \"benden\", not \"hesap bana ait\". Reach for the ")
        append("ready-made Turkish expression whenever one exists), ")

        append("e (array of 0-2 notes in Turkish, written for an adult who ")
        append("already reads English at an intermediate level. ")
        append("Write a note ONLY when a learner who knows every word separately ")
        append("would still read the sentence wrongly: a figurative use, an idiom ")
        append("whose meaning is not the sum of its parts, something left out. ")
        append("If the sentence means exactly what it says, return an EMPTY ")
        append("array — that is the normal case, not a failure. ")
        append("When the expression IS figurative — an idiom, a phrasal verb, or ")
        append("a word used outside its plain sense — the note must do three ")
        append("things in one or two sentences: give the word\u0027s literal, ")
        append("original meaning; give the sense it carries here; and show the ")
        append("CONNECTION between the two — which image or association carries the ")
        append("meaning across, and for a phrasal verb what the particle ")
        append("contributes. This is the most useful thing you can write, ")
        append("because seeing how the sense travelled is what lets the ")
        append("reader guess the next idiom unaided. Show that movement in ")
        append("ordinary Turkish; never name the mechanism and never use ")
        append("the word \"köprü\" or any other jargon for it. ")
        append("To show the kind of thing meant (do not reuse these ")
        append("unless the input actually contains them): \"can\" is literally a ")
        append("metal container, hence a small closed box you cannot leave, ")
        append("hence prison; \"stand\" is literally to stay upright, hence to ")
        append("hold your ground under a weight, hence to tolerate; the particle ")
        append("\"off\" carries separation, so \"hack off\" is cutting a piece ")
        append("AWAY rather than cutting into something; \"around\" carries ")
        append("\"here and there, no fixed point\", so \"see you around\" is ")
        append("meeting again at no arranged time. ")
        append("If no real connection exists, say plainly that the meaning is ")
        append("idiomatic and unmotivated — never invent an etymology. ")
        append("Never explain that a swear word is rude, vulgar or sexual: the ")
        append("reader is an adult and already knows. Write about a swear word ")
        append("only when it carries a sense its parts do not give ")
        append("(\"fuck off\" = defol), and then explain only that sense. ")
        append("Each note is a PLAIN Turkish string — never a JSON object, ")
        append("never English. ")
        append("Do not restate the sentence. Do not label its speech act ")
        append("(\"bir tavsiye\", \"bir soru\"). Do not mention word order. ")
        append("Do not say that a word\u0027s reference \"depends on context\" — ")
        append("that is true of every word and teaches nothing. ")
        append("Never decorate a note with the genre or the work. Sentences like ")
        append("\"mafya ortamında böyle kullanılır\" or \"bu tür filmlerde ...\" ")
        append("are almost always invented, and the reader is tired of reading ")
        append("them on every card. A wig advert is a wig advert even in a mob ")
        append("film. Mention the world of the work only if the expression truly ")
        append("belongs to it and would otherwise be misread), ")
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
        val reading = json.optString("y").trim()
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
                reading = reading,
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

    /**
     * Dizideki metinler.
     *
     * Model bazen düz metin yerine `{"en":"..."}` gibi bir nesne koyuyor;
     * o zaman `optString` nesnenin JSON'unu olduğu gibi veriyor ve kartta
     * süslü parantezler görünüyordu. Nesne gelirse içindeki ilk metni
     * alıyoruz.
     */
    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val nested = optJSONObject(index)
            val raw = if (nested != null) {
                nested.keys().asSequence()
                    .mapNotNull { nested.optString(it).trim().takeIf { value -> value.isNotBlank() } }
                    .firstOrNull()
                    .orEmpty()
            } else {
                optString(index)
            }
            raw.trim().takeIf { it.isNotBlank() }
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

    /**
     * Kelime için küçük bir hatırlatıcı görsel üretir.
     *
     * Amaç sanat değil, kelimeyi bir sahneye bağlamak: görsel bellek sözlük
     * tanımından daha iyi tutuyor. Bu yüzden en ucuz model, en düşük kalite
     * ve tek görsel — bir kelimeye bir kez üretiliyor ve dosyada kalıyor.
     *
     * Görselde yazı istemiyoruz: bu modeller harfleri güvenilmez çiziyor ve
     * yanlış yazılmış bir kelime öğrenirken zarar veriyor.
     */
    suspend fun generateImage(word: String, meaning: String = ""): AiResult<ByteArray> {
        val key = settings.apiKey
        if (key.isBlank()) return AiResult.Failed("OpenAI anahtarı girilmemiş.")
        if (word.isBlank()) return AiResult.Failed("Kelime boş.")

        val prompt = buildString {
            append("Simple flat illustration that helps a language learner ")
            append("remember the English expression \"").append(word).append("\"")
            if (meaning.isNotBlank()) append(" (meaning: ").append(meaning).append(")")
            append(". One clear subject, plain background, soft colours. ")
            append("No text, no letters, no numbers, no captions, no watermark.")
        }

        val payload = JSONObject().apply {
            put("model", IMAGE_MODEL)
            put("prompt", prompt)
            put("size", "1024x1024")
            put("quality", "low")
            put("n", 1)
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(IMAGE_ENDPOINT)
                    .addHeader("Authorization", "Bearer $key")
                    .post(payload.toString().toRequestBody(JSON_MEDIA))
                    .build()

                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@use AiResult.Failed(readableError(response.code, body))
                    }
                    val encoded = JSONObject(body)
                        .getJSONArray("data")
                        .getJSONObject(0)
                        .getString("b64_json")
                    AiResult.Ok(Base64.getDecoder().decode(encoded))
                }
            }.getOrElse { error ->
                AiResult.Failed(error.message ?: "Görsel üretilemedi.")
            }
        }
    }

    private companion object {
        const val ENDPOINT = "https://api.openai.com/v1/chat/completions"

        const val IMAGE_ENDPOINT = "https://api.openai.com/v1/images/generations"

        /**
         * Ailenin en ucuzu. Düşük kalitede kare bir görsel, görsel başına birkaç
         * kuruş; kart başına bir kez üretildiği için toplam da küçük kalıyor.
         */
        const val IMAGE_MODEL = "gpt-image-1-mini"

        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
