package com.ahmety.uygulama.feature.subtitles

import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.feature.ebook.BookRepository
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
    private val bookRepository: BookRepository,
) {

    /**
     * Filmi arayıp aynı sürüme ait İngilizce ve Türkçe altyazıyı indirir.
     *
     * Önce en çok indirilen İngilizce altyazı seçiliyor; Türkçesi ona göre,
     * [ReleaseMatch] puanıyla eşleştiriliyor. Türkçesi bulunamazsa akış
     * durmuyor — İngilizce tek başına da kelime çıkarmaya yetiyor.
     */
    suspend fun prepare(
        query: String,
        year: Int?,
        language: SubtitleLanguage,
    ): SubtitleResult<SubtitlePair> {
        val hits = when (val result = client.search(query, year, language.code)) {
            is SubtitleResult.Failed -> return result
            is SubtitleResult.Ok -> result.value
        }
        if (hits.isEmpty()) return SubtitleResult.Failed("Bu isimle altyazı bulunamadı.")

        val english = hits.filter { it.language.startsWith(language.code) }
            .maxByOrNull { it.downloads + if (it.fromHash) 100_000 else 0 }
            ?: return SubtitleResult.Failed("${language.label} altyazı bulunamadı.")

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
     * Zorluk eşiğini geçen kelimeleri ve cümleleri çıkarır.
     *
     * Eşik kullanıcıdan geliyor (0-100). Seviye sınavına girdiysen, bildiğin
     * kelimeler ayrıca eleniyor: sınav "şu sıklığa kadarını biliyorum"
     * diyorsa o aralık eşiğin altında sayılmasa bile listeye girmiyor.
     */
    suspend fun extract(
        pair: SubtitlePair,
        minDifficulty: Int,
        wordLimit: Int,
        sentenceLimit: Int,
    ): List<SubtitlePick> = withContext(Dispatchers.IO) {
        // Sıklık listesi metnin dilinden seçiliyor: Arapça altyazıya
        // İngilizce liste tutulursa her kelime "listede yok" çıkar.
        val arabic = ArabicText.isArabic(pair.englishText.take(SCRIPT_SAMPLE))
        val vocabulary = if (arabic) levelTest.arabicWords() else levelTest.words()
        val ranks = vocabulary.withIndex().associate { (index, word) -> word to index + 1 }
        val estimate = estimateLevel(levelTest.answers, ranks.size.coerceAtLeast(1))
        val knownUpToRank = estimate.knownUpToRank

        val seen = entryRepository.listByType(EntryType.HIGHLIGHT)
            .map { it.title.trim().lowercase() }
            .toSet()

        // Sınavdan gelen "bunları biliyorum" aralığı zorluk eşiğinden
        // bağımsız: eşik düşük olsa bile bildiğin kelimeyi göstermenin
        // anlamı yok.
        // Seviye sınavı şimdilik yalnız İngilizce; Arapçada bu eşiği
        // uygulamak yanlış listeye bakmak olurdu.
        val known = if (knownUpToRank > 0 && !arabic) {
            ranks.filterValues { it <= knownUpToRank }.keys
        } else {
            emptySet()
        }

        val properNouns = SubtitleText.properNouns(pair.englishText)
        val words = SubtitleText.selectWords(
            words = SubtitleText.words(pair.englishText),
            ranks = ranks,
            properNouns = properNouns,
            minDifficulty = minDifficulty,
            alreadySeen = seen + known,
            limit = wordLimit,
        )
        val sentences = SubtitleText.selectSentences(
            srt = pair.englishText,
            ranks = ranks,
            minDifficulty = minDifficulty,
            alreadySeen = seen,
            limit = sentenceLimit,
        )
        // Cümleler başta: filmde seni asıl durduran onlar.
        sentences + words
    }

    /**
     * Seçilenleri kelime destesine aktarır.
     *
     * Film, kitaplarla aynı biçimde okunabilir bir belge olarak kaydediliyor:
     * altyazıyı kitap gibi okuyup kendin de işaretleyebilesin diye. Kelime
     * maviye, anlaşılması zor cümle kırmızıya gidiyor.
     */
    suspend fun save(pair: SubtitlePair, picks: List<SubtitlePick>): Int {
        if (picks.isEmpty()) return 0
        val title = buildString {
            append(pair.movie)
            if (pair.year > 0) append(" (").append(pair.year).append(")")
        }
        val movieId = bookRepository.importFilm(
            title = title,
            release = pair.english.release,
            sentences = SubtitleText.sentences(pair.englishText),
        )
        picks.forEach { pick ->
            entryRepository.createEntry(
                type = EntryType.HIGHLIGHT,
                title = pick.text,
                body = pick.context,
                source = HighlightRef.encode(
                    kind = HighlightRef.KIND_SUBTITLE,
                    sourceId = movieId,
                    color = if (pick.sentence) HighlightColor.RED else HighlightColor.BLUE,
                ),
            )
        }
        return picks.size
    }

    private companion object {
        /** Bu kadar replik yoksa elimizdeki şey altyazı değildir. */
        const val MIN_LINES = 20

        /** Metnin dilini anlamak için bakılan karakter sayısı. */
        const val SCRIPT_SAMPLE = 4000
    }
}
