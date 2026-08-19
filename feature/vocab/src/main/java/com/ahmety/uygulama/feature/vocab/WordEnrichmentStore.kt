package com.ahmety.uygulama.feature.vocab

import android.content.Context
import com.ahmety.uygulama.core.ai.WordInfo
import com.ahmety.uygulama.core.model.Collocation
import com.ahmety.uygulama.core.model.VocabWord
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Yapay zekâyla doldurulan kelime bilgisi.
 *
 * Kitaptan gelen kelimelerin sözlükte karşılığı yok; bir kez üretilip burada
 * saklanıyor ki her açılışta yeniden istek gitmesin. Veritabanı şeması
 * değişmesin diye uygulamanın kendi dosyasında JSON olarak duruyor.
 */
@Singleton
class WordEnrichmentStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Bellek önbelleği: deste her hesaplandığında kelime başına bir dosya
     * okuması yapılıyordu ve bu iş ana iş parçacığında dönüyor.
     */
    private val cache = ConcurrentHashMap<String, VocabWord>()
    private val missing = ConcurrentHashMap.newKeySet<String>()

    fun get(word: String): VocabWord? {
        val key = word.lowercase()
        cache[key]?.let { return it }
        if (key in missing) return null
        val raw = prefs.getString(key, null)
        if (raw == null) {
            missing.add(key)
            return null
        }
        return runCatching {
            val json = JSONObject(raw)
            VocabWord(
                word = word,
                meaning = json.optString("t"),
                definition = json.optString("d"),
                examples = json.optJSONArray("e").toList(),
                related = json.optJSONArray("r").toList(),
                synonyms = json.optJSONArray("s").toList(),
                antonyms = json.optJSONArray("a").toList(),
                root = json.optString("k"),
                family = json.optJSONArray("f").toList(),
                confusions = json.optJSONArray("x").toList(),
                collocations = json.optJSONArray("c").toCollocations(),
            )
        }.getOrNull()?.also { cache[key] = it }
    }

    fun put(info: WordInfo) {
        val json = JSONObject().apply {
            put("t", info.meaning)
            put("d", info.definition)
            put("e", JSONArray().apply { info.examples.forEach { put(it) } })
            put("r", JSONArray().apply { info.related.forEach { put(it) } })
            put("s", JSONArray().apply { info.synonyms.forEach { put(it) } })
            put("a", JSONArray().apply { info.antonyms.forEach { put(it) } })
            put("k", info.root)
            put("f", JSONArray().apply { info.family.forEach { put(it) } })
            put("x", JSONArray().apply { info.confusions.forEach { put(it) } })
            put(
                "c",
                JSONArray().apply {
                    info.collocations.forEach { group ->
                        put(
                            JSONObject()
                                .put("g", group.pattern)
                                .put("w", JSONArray().apply { group.words.forEach { put(it) } }),
                        )
                    }
                },
            )
        }
        val key = info.word.lowercase()
        prefs.edit().putString(key, json.toString()).apply()
        missing.remove(key)
        cache[key] = VocabWord(
            word = info.word,
            meaning = info.meaning,
            definition = info.definition,
            examples = info.examples,
            related = info.related,
            synonyms = info.synonyms,
            antonyms = info.antonyms,
            root = info.root,
            family = info.family,
            confusions = info.confusions,
            collocations = info.collocations,
        )
    }

    fun has(word: String): Boolean = prefs.contains(word.lowercase())

    private fun JSONArray?.toCollocations(): List<Collocation> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val group = optJSONObject(index) ?: return@mapNotNull null
            val pattern = group.optString("g").trim()
            val words = group.optJSONArray("w").toList()
            if (pattern.isBlank() || words.isEmpty()) null else Collocation(pattern, words)
        }
    }

    private fun JSONArray?.toList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }
    }

    private companion object {
        const val PREFS_NAME = "merkez_kelime_ai"
    }
}
