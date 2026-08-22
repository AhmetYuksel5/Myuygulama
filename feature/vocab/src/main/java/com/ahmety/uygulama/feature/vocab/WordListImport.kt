package com.ahmety.uygulama.feature.vocab

import android.content.Context
import android.net.Uri
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.EntryType
import com.ahmety.uygulama.core.model.HighlightRef
import com.ahmety.uygulama.core.model.penFor
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Çözümlenmiş dosya: listenin adı ve içindeki maddeler. */
data class ParsedWordList(val name: String, val entries: List<String>)

/** Yükleme sonucu; ekranda ne olduğunu yazabilmek için. */
data class WordListImportResult(
    val name: String,
    val added: Int,
    val skipped: Int,
)

/**
 * Dışarıdan gelen kelime listesi dosyasını çözer.
 *
 * Biçim kasıtlı olarak gevşek: elde ne varsa yüklenebilsin. Her satır bir
 * madde, ilk dolu satır listenin adı. Tırnak zorunlu değil; tırnaklıysa
 * kaldırılıyor.
 *
 * Virgülden bölmüyoruz. Bölseydik "The truth is, probably, just an
 * inconvenience" cümlesi üç parçaya ayrılırdı — oysa CSV'de tırnak içindeki
 * virgül metnin parçası. Bir satır tek madde demek, nokta.
 */
object WordListFile {

    fun parse(raw: String): ParsedWordList? {
        val lines = raw.removePrefix("﻿")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .map { unquote(it.trim()) }
            .filter { it.isNotBlank() }
        if (lines.size < 2) return null

        val name = lines.first()
        // Aynı madde iki kez geçerse bir kez alınıyor; büyük/küçük harf farkı
        // ayrı madde saymıyor.
        val seen = HashSet<String>()
        val entries = lines.drop(1).filter { seen.add(it.lowercase()) }
        return if (entries.isEmpty()) null else ParsedWordList(name, entries)
    }

    /**
     * Tırnakları kaldırır.
     *
     * CSV'de tırnak içindeki tırnak iki kez yazılıyor (`""`); geri
     * çeviriyoruz. Satırın sonunda tek başına kalan virgül de tek sütunlu
     * bir CSV satırının artığı, o da gidiyor.
     */
    private fun unquote(line: String): String {
        val trimmed = line.trim().removeSuffix(",").trim()
        if (trimmed.length < 2 || !trimmed.startsWith('"')) return trimmed

        val out = StringBuilder(trimmed.length)
        var index = 1
        while (index < trimmed.length) {
            val char = trimmed[index]
            if (char != '"') {
                out.append(char)
                index++
                continue
            }
            // İki tırnak yan yana: metnin içindeki tırnak.
            if (index + 1 < trimmed.length && trimmed[index + 1] == '"') {
                out.append('"')
                index += 2
                continue
            }
            // Tek tırnak: alan burada bitiyor.
            break
        }
        return out.toString().trim()
    }
}

/**
 * Yüklenen listeleri kaydeder.
 *
 * Liste, kitap ve filmle aynı kayıt türünü kullanıyor: böylece kelime
 * listesinde "şu listeden" diye süzülebiliyor. Farkı okunacak bir metninin
 * olmaması — kitaplıkta görünmüyor.
 */
@Singleton
class WordListRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entryRepository: EntryRepository,
) {

    suspend fun read(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.bufferedReader().readText()
            }
        }.getOrNull()
    }

    suspend fun import(list: ParsedWordList): WordListImportResult {
        val document = entryRepository.createEntry(
            type = EntryType.DOCUMENT,
            title = list.name,
            body = "Yüklenen liste",
            source = HighlightRef.WORDLIST_SOURCE_MARKER,
        )

        // Zaten çalıştığın bir madde ikinci kez eklenmesin: kelime listesi
        // aynı yazımı tek satır gösteriyor, ikinci kayıt görünmeyen bir
        // artık olarak kalırdı.
        val existing = entryRepository.listByType(EntryType.HIGHLIGHT)
            .map { it.title.trim().lowercase() }
            .toSet()

        var added = 0
        var skipped = 0
        list.entries.forEach { entry ->
            if (entry.lowercase() in existing) {
                skipped++
                return@forEach
            }
            entryRepository.createEntry(
                type = EntryType.HIGHLIGHT,
                title = entry,
                body = "",
                source = HighlightRef.encode(
                    kind = HighlightRef.KIND_LIST,
                    sourceId = document,
                    color = penFor(entry),
                ),
            )
            added++
        }
        return WordListImportResult(name = list.name, added = added, skipped = skipped)
    }
}
