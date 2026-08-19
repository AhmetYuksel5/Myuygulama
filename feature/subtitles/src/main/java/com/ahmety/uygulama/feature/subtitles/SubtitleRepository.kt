package com.ahmety.uygulama.feature.subtitles

import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.feature.vocab.LevelTestStore
import com.ahmety.uygulama.feature.vocab.estimateLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Bir film için indirilen altyazı çifti. */
data class SubtitlePair(
    val movie: String,
    val year: Int,
    val english: SubtitleHit,
    val turkish: SubtitleHit?,
    val englishText: String,
    val turkishText: String,
)

@Singleton
class SubtitleRepository @Inject constructor(
    private val client: OpenSubtitlesClient,
    private val entryRepository: EntryRepository,
    private val levelTest: LevelTestStore,
) {

    /**
     * Filmi arayıp aynı sürüme ait İngilizce ve Türkçe altyazıyı indirir.
     *
     * Önce en çok indirilen İngilizce altyazı seçiliyor; Türkçesi ona göre,
     * [ReleaseMatch] puanıyla eşleştiriliyor. Türkçesi bulunamazsa akış
     * durmuyor — İngilizce tek başına da kelime çıkarmaya yetiyor.
     */
    suspend fun prepare(query: String, year: Int?): SubtitleResult<SubtitlePair> {
        val hits = when (val result = client.search(query, year)) {
            is SubtitleResult.Failed -> return result
            is SubtitleResult.Ok -> result.value
        }
        if (hits.isEmpty()) return SubtitleResult.Failed("Bu isimle altyazı bulunamadı.")

        val english = hits.filter { it.language.startsWith("en") }
            .maxByOrNull { it.downloads + if (it.fromHash) 100_000 else 0 }
            ?: return SubtitleResult.Failed("İngilizce altyazı bulunamadı.")

        val turkish = hits.filter { it.language.startsWith("tr") }
            .maxByOrNull { ReleaseMatch.score(english.release, it.release) * 1000 + it.downloads }

        val englishText = when (val result = client.download(english.fileId)) {
            is SubtitleResult.Failed -> return result
            is SubtitleResult.Ok -> result.value
        }
        // İndirme "başarılı" dönüp elimize altyazı yerine boş gövde ya da bir
        // hata sayfası geçebiliyor. Bunu burada yakalamazsak akış sessizce
        // "seviyenin üstünde kelime bulunamadı" diye biter ve sebep görünmez.
        if (SubtitleText.lines(englishText).size < MIN_LINES) {
            return SubtitleResult.Failed(
                "Altyazı indirildi ama içi okunamadı. Günlük indirme hakkın " +
                    "dolmuş olabilir; Ayar'dan kullanıcı adı ve parolanı gir.",
            )
        }
        // Türkçe indirilemezse (kota, yok) sessizce boş geçiyoruz.
        val turkishText = turkish?.let {
            (client.download(it.fileId) as? SubtitleResult.Ok)?.value
        }.orEmpty()

        return SubtitleResult.Ok(
            SubtitlePair(
                movie = english.movieName.ifBlank { query },
                year = english.year.takeIf { it > 0 } ?: (year ?: 0),
                english = english,
                turkish = turkish,
                englishText = englishText,
                turkishText = turkishText,
            ),
        )
    }

    /**
     * Seviyene göre bilmediğin kelimeleri çıkarır.
     *
     * Eşik seviye sınavından geliyor. Sınava hiç girmediysen makul bir
     * varsayımla (ilk 2000 kelime) çalışıyoruz — yoksa "the, and, you" gibi
     * kelimeleri de çıkarırdı.
     */
    suspend fun extractWords(pair: SubtitlePair, limit: Int): List<SubtitleWord> =
        withContext(Dispatchers.IO) {
            val ranks = levelTest.words().withIndex().associate { (index, word) -> word to index + 1 }
            val estimate = estimateLevel(levelTest.answers, ranks.size.coerceAtLeast(1))
            val threshold = estimate.knownUpToRank.takeIf { it > 0 } ?: DEFAULT_KNOWN_RANK
            val seen = entryRepository.listByType(EntryType.HIGHLIGHT)
                .map { it.title.trim().lowercase() }
                .toSet()
            val words = SubtitleText.selectUnknown(
                words = SubtitleText.words(pair.englishText),
                frequencyRank = ranks,
                knownUpToRank = threshold,
                alreadySeen = seen,
                limit = limit,
            )
            // Filmdeki ifadeler tek tek kelimelerden farklı: "put up with"i
            // üç kelimenin anlamından çıkaramıyorsun. Kalıpları ayrıca
            // topluyoruz ve listenin başına koyuyoruz.
            val phrases = SubtitleText.phrases(pair.englishText)
                .filter { it.word.lowercase() !in seen }
                .take(PHRASE_LIMIT)
            phrases + words
        }

    /**
     * Seçilen kelimeleri kelime destesine aktarır.
     *
     * Film, kitaplarla aynı biçimde bir kayıt olarak açılıyor; böylece kelime
     * listesinde "şu filmden" diye süzülebiliyor.
     */
    suspend fun save(pair: SubtitlePair, words: List<SubtitleWord>): Int {
        if (words.isEmpty()) return 0
        val title = buildString {
            append(pair.movie)
            if (pair.year > 0) append(" (").append(pair.year).append(")")
        }
        val movieId = entryRepository.createEntry(
            type = EntryType.DOCUMENT,
            title = title,
            body = pair.english.release,
            source = HighlightRef.SUBTITLE_SOURCE_MARKER,
        )
        words.forEach { word ->
            entryRepository.createEntry(
                type = EntryType.HIGHLIGHT,
                title = word.word,
                body = word.context,
                source = HighlightRef.encode(
                    kind = HighlightRef.KIND_SUBTITLE,
                    sourceId = movieId,
                    color = HighlightColor.BLUE,
                ),
            )
        }
        return words.size
    }

    private companion object {
        /** Seviye sınavına girilmemişse varsayılan bilinen sıklık aralığı. */
        const val DEFAULT_KNOWN_RANK = 2000

        /** Bu kadar replik yoksa elimizdeki şey altyazı değildir. */
        const val MIN_LINES = 20

        /** Bir filmden alınacak en fazla kalıp sayısı. */
        const val PHRASE_LIMIT = 15

        /** Bir filmden alınacak en fazla kalıp sayısı. */
        const val PHRASE_LIMIT = 15

    }
}
