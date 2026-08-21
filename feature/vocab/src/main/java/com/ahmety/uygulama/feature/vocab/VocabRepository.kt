package com.ahmety.uygulama.feature.vocab

import com.ahmety.uygulama.core.database.dao.VocabDao
import com.ahmety.uygulama.core.database.entity.ChangeEntityType
import com.ahmety.uygulama.core.database.entity.ChangeOperation
import com.ahmety.uygulama.core.database.entity.VocabProgressEntity
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.database.sync.ChangeRecorder
import com.ahmety.uygulama.core.database.sync.Now
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightColor
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.core.model.VocabSource
import com.ahmety.uygulama.core.model.VocabDecision
import com.ahmety.uygulama.core.model.VocabSchedule
import com.ahmety.uygulama.core.model.VocabStatus
import com.ahmety.uygulama.core.model.nextSchedule
import com.ahmety.uygulama.core.model.startOfDay
import com.ahmety.uygulama.core.model.VocabWord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Kelime listesi tamamen kendi okuduğun ve izlediğinden geliyor: kitapta
 * **mavi** işaretlediğin kelimeler, **kırmızı** işaretlediğin cümleler, film
 * altyazısından çıkarılanlar ve başka uygulamalarda seçip gönderdiklerin.
 * Hazır bir deste yok — bilmediğin kelime zaten senin karşına çıkan kelime.
 *
 * Kararlar ve tekrar programı veritabanında; yazmalar değişiklik
 * günlüğünden geçtiği için ikinci telefona da taşınıyor.
 */
@Singleton
class VocabRepository @Inject constructor(
    private val vocabDao: VocabDao,
    private val entryRepository: EntryRepository,
    private val enrichment: WordEnrichmentStore,
    private val changeRecorder: ChangeRecorder,
    private val json: Json,
    private val now: Now,
) {

    /**
     * Kitapta/altyazıda **mavi** işaretlenmiş kelimeler. Akış olarak veriyoruz:
     * yeni bir kelime işaretleyince uygulamayı yeniden başlatmadan destede
     * belirmesi gerekiyor.
     *
     * Kaynağın adı (kitabın/filmin başlığı) da geliyor: kelime listesini
     * "şu kitaptan" ya da "şu filmden" diye süzebilmek için.
     */
    fun observeBookWords(): Flow<List<VocabWord>> = combine(
        entryRepository.observeByType(EntryType.HIGHLIGHT),
        entryRepository.observeByType(EntryType.DOCUMENT),
    ) { entries, documents ->
        val titleById = documents.associate { it.id to it.title.trim() }
        entries
            // Mavi = bilmediğin kelime, kırmızı = anlamadığın cümle/cümlecik.
            // İkisi de çalışılacak; kartta farklı davranıyorlar.
            .filter { HighlightRef.color(it.source) in STUDIED_COLORS }
            .filter { it.title.isNotBlank() }
            .map { entry ->
                val word = entry.title.trim()
                // Daha önce yapay zekâyla doldurulduysa onu kullan.
                val filled = enrichment.get(word)
                val kind = HighlightRef.kind(entry.source)
                val passage = HighlightRef.color(entry.source) == HighlightColor.RED
                VocabWord(
                    word = word,
                    meaning = filled?.meaning.orEmpty(),
                    definition = filled?.definition.orEmpty(),
                    reading = filled?.reading.orEmpty(),
                    examples = filled?.examples.orEmpty(),
                    related = filled?.related.orEmpty(),
                    synonyms = filled?.synonyms.orEmpty(),
                    antonyms = filled?.antonyms.orEmpty(),
                    root = filled?.root.orEmpty(),
                    family = filled?.family.orEmpty(),
                    confusions = filled?.confusions.orEmpty(),
                    collocations = filled?.collocations.orEmpty(),
                    answers = filled?.answers.orEmpty(),
                    context = entry.body.trim(),
                    source = when (kind) {
                        HighlightRef.KIND_SUBTITLE -> VocabSource.SUBTITLE
                        HighlightRef.KIND_SELECTION -> VocabSource.SELECTION
                        else -> VocabSource.BOOK
                    },
                    sourceName = HighlightRef.sourceId(entry.source)
                        ?.let { titleById[it] }
                        .orEmpty(),
                    isPassage = passage,
                )
            }
            .distinctBy { it.word.lowercase() }
    }

    /**
     * Kelimeyi kökünden siler: kitaptaki/filmdeki işaret de kalkıyor.
     *
     * Eskiden yalnızca listeden gizliyorduk; kitabı açınca kelime hâlâ
     * boyalı duruyordu. Artık işaretleme kaydının kendisi siliniyor.
     *
     * Yalnızca çalışılan renkler siliniyor: sarı ya da yeşil işaretlediğin
     * bir yer kelime listesine hiç düşmüyor, dolayısıyla buradan silinmesi
     * de gerekmiyor.
     *
     * Tekrar programındaki satır da temizleniyor. Olmasaydı kelimeyi
     * yeniden işaretlediğinde eski "öğrendim" damgasıyla geri gelir ve
     * destede hiç görünmezdi.
     */
    suspend fun deleteWord(word: VocabWord) {
        val target = word.word.trim()
        entryRepository.listByType(EntryType.HIGHLIGHT)
            .filter { it.title.trim().equals(target, ignoreCase = true) }
            .filter { HighlightRef.color(it.source) in STUDIED_COLORS }
            .forEach { entryRepository.deleteEntry(it.id) }
        forgetProgress(target)
    }

    /**
     * Kelimenin ya da cümlenin kendisini düzeltir.
     *
     * Kitaptan gelen metin bazen bozuk oluyor ("shittyjobs"), bazen yanlış
     * yeri seçmiş oluyorsun. Düzeltme işaretleme kaydının başlığına yazılıyor,
     * yani kitapta da düzeliyor. Eski yazımın bilgisi ve tekrar geçmişi
     * siliniyor: artık başka bir kelime.
     */
    suspend fun renameWord(old: VocabWord, newText: String): Boolean {
        val target = newText.trim()
        val previous = old.word.trim()
        if (target.isBlank() || target.equals(previous, ignoreCase = true)) return false

        entryRepository.listByType(EntryType.HIGHLIGHT)
            .filter { it.title.trim().equals(previous, ignoreCase = true) }
            .filter { HighlightRef.color(it.source) in STUDIED_COLORS }
            .forEach { entryRepository.updateEntry(it.id, target, it.body) }

        enrichment.forget(previous)
        forgetProgress(previous)
        return true
    }

    /**
     * İşaretin rengini değiştirir: mavi kelime, kırmızı cümle.
     *
     * Çalışırken fark ediyorsun — "bu aslında bir kelime değil, çözemediğim
     * bir cümleymiş". Kitaba dönüp işareti bulmak gerekmesin diye karttan da
     * yapılabiliyor. Bilgisi siliniyor çünkü kelime ve cümle için yapay
     * zekâya giden yönerge başka; eldeki bilgi yanlış kalıptan.
     */
    suspend fun setPassage(word: VocabWord, passage: Boolean) {
        if (word.isPassage == passage) return
        val target = word.word.trim()
        val color = if (passage) HighlightColor.RED else HighlightColor.BLUE
        entryRepository.listByType(EntryType.HIGHLIGHT)
            .filter { it.title.trim().equals(target, ignoreCase = true) }
            .filter { HighlightRef.color(it.source) in STUDIED_COLORS }
            .forEach { entry ->
                val kind = HighlightRef.kind(entry.source) ?: HighlightRef.KIND_BOOK
                val sourceId = HighlightRef.sourceId(entry.source) ?: return@forEach
                entryRepository.updateSource(
                    entry.id,
                    HighlightRef.encode(kind, sourceId, color),
                )
            }
        enrichment.forget(target)
    }

    /** Tekrar programındaki satırı siler; senkronda karşı tarafa da geçiyor. */
    private suspend fun forgetProgress(word: String) {
        val existing = vocabDao.get(word) ?: return
        val timestamp = now.millis()
        val cleared = existing.copy(deletedAt = timestamp, updatedAt = timestamp)
        vocabDao.upsert(cleared)
        changeRecorder.record(
            entityType = ChangeEntityType.VOCAB,
            entityUuid = word,
            operation = ChangeOperation.DELETE,
            payload = json.encodeToString(VocabProgressEntity.serializer(), cleared),
        )
    }

    /** Kullanıcının elle düzenlediği ya da çoğalttığı kelime bilgisi. */
    fun saveEdit(word: VocabWord) {
        enrichment.put(
            com.ahmety.uygulama.core.ai.WordInfo(
                word = word.word,
                meaning = word.meaning,
                definition = word.definition,
                reading = word.reading,
                examples = word.examples,
                related = word.related,
                synonyms = word.synonyms,
                antonyms = word.antonyms,
                root = word.root,
                family = word.family,
                confusions = word.confusions,
                collocations = word.collocations,
                answers = word.answers,
            ),
        )
    }

    /** Yapay zekâyla üretilen bilgiyi saklar. */
    /**
     * Yapay zekâdan gelen bilgiyi yazar.
     *
     * Kart üstünde sorup kaydettiğin yanıtlar korunuyor: onlar senin
     * notların, modelin ürettiği bilginin yenilenmesiyle silinmemeliler.
     */
    fun saveEnrichment(info: com.ahmety.uygulama.core.ai.WordInfo) {
        val kept = enrichment.get(info.word)?.answers.orEmpty()
        enrichment.put(if (kept.isEmpty()) info else info.copy(answers = kept))
    }

    /** Kart üstünde sorulan soruyu ve yanıtını kelimeye ekler. */
    fun saveAnswer(word: VocabWord, note: String) {
        val current = enrichment.get(word.word) ?: word
        saveEdit(current.copy(word = word.word, answers = current.answers + note))
    }

    /**
     * Akıştan gelen kitap kelimelerine, varsa yapay zekâ ile doldurulmuş
     * bilgileri uygular. Akış veritabanından geldiği için zenginleştirme
     * yazıldığında kendiliğinden güncellenmiyor.
     */
    fun applyEnrichment(words: List<VocabWord>): List<VocabWord> = words.map { word ->
        val filled = enrichment.get(word.word) ?: return@map word
        word.copy(
            meaning = filled.meaning,
            definition = filled.definition,
            reading = filled.reading,
            examples = filled.examples,
            related = filled.related,
            synonyms = filled.synonyms,
            antonyms = filled.antonyms,
            root = filled.root,
            family = filled.family,
            confusions = filled.confusions,
            collocations = filled.collocations,
            answers = filled.answers,
        )
    }

    fun observeProgress(): Flow<List<VocabProgressEntity>> = vocabDao.observeAll()

    fun observeCount(status: VocabStatus): Flow<Int> =
        vocabDao.observeCountByStatus(status.name)

    /**
     * Karara göre kelimeyi programda ilerletir.
     *
     * Zamanlamanın kendisi [nextSchedule] içinde, saf bir fonksiyonda; burada
     * yalnızca satırı okuyup yazıyoruz. [revealed] kartın anlamının açılıp
     * açılmadığı: açtıysan hatırlayamadın, kademe ilerlemiyor.
     */
    suspend fun applyDecision(word: String, decision: VocabDecision, revealed: Boolean) {
        val timestamp = now.millis()
        val existing = vocabDao.get(word)
        val next = nextSchedule(
            current = existing?.toSchedule() ?: VocabSchedule(word = word),
            decision = decision,
            now = timestamp,
            revealed = revealed,
            dayStart = startOfDay(timestamp, zoneOffsetMillis(timestamp)),
        )
        val entity = VocabProgressEntity(
            word = word,
            status = next.status.name,
            updatedAt = timestamp,
            box = next.box,
            dueAt = next.dueAt,
            lastReviewedAt = next.lastReviewedAt,
            introducedAt = next.introducedAt,
            reviewCount = next.reviewCount,
            lapseCount = next.lapseCount,
            postponeCount = next.postponeCount,
            revealCount = next.revealCount,
        )
        vocabDao.upsert(entity)
        changeRecorder.record(
            entityType = ChangeEntityType.VOCAB,
            entityUuid = word,
            operation = ChangeOperation.UPSERT,
            payload = json.encodeToString(VocabProgressEntity.serializer(), entity),
        )
    }

    /** Cihazın UTC farkı; gün sınırı yerel saatle hesaplansın diye. */
    fun zoneOffsetMillis(at: Long): Long =
        java.util.TimeZone.getDefault().getOffset(at).toLong()

    fun nowMillis(): Long = now.millis()

    private companion object {
        /** Kelime destesine düşen işaretleme renkleri. */
        val STUDIED_COLORS = setOf(HighlightColor.BLUE, HighlightColor.RED)
    }
}

/** Veritabanı satırını zamanlama mantığının anladığı saf hâle çevirir. */
fun VocabProgressEntity.toSchedule(): VocabSchedule = VocabSchedule(
    word = word,
    status = runCatching { VocabStatus.valueOf(status) }.getOrDefault(VocabStatus.NEW).normalized(),
    box = box,
    dueAt = dueAt,
    lastReviewedAt = lastReviewedAt,
    introducedAt = introducedAt,
    reviewCount = reviewCount,
    lapseCount = lapseCount,
    postponeCount = postponeCount,
    revealCount = revealCount,
)
