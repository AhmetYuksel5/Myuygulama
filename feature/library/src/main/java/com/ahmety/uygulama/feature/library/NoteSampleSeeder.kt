package com.ahmety.uygulama.feature.library

import android.content.Context
import com.ahmety.uygulama.core.database.repository.EntryRepository
import com.ahmety.uygulama.core.model.EntryType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Not defterini ilk açılışta boş bırakmamak için iki örnek not ekler:
 * biri işaretlenebilir liste, biri renkli düz not. Yalnızca **bir kez**
 * çalışır; kullanıcı silerse geri gelmez.
 */
@Singleton
class NoteSampleSeeder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EntryRepository,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun seedIfNeeded() {
        if (prefs.getBoolean(KEY_SEEDED, false)) return
        prefs.edit().putBoolean(KEY_SEEDED, true).apply()

        repository.createEntry(
            type = EntryType.NOTE,
            title = "Market listesi",
            body = listOf(
                "[ ] Yumurta",
                "[ ] Süt",
                "[x] Kahve",
                "[ ] Zeytinyağı",
            ).joinToString("\n"),
            source = NoteStyle(colorIndex = 3).encode(),
        )

        repository.createEntry(
            type = EntryType.NOTE,
            title = "Bu not defteri nasıl çalışıyor",
            body = "Sağ alttaki + ile not yaz.\n\n" +
                "Editörde \"Liste maddesi\" düğmesiyle işaretlenebilir madde, " +
                "\"Fotoğraf\" düğmesiyle görsel ekleyebilirsin. Kartın rengini " +
                "editördeki renk şeridinden seçersin; kart üzerinde uzun basınca " +
                "sabitleme ve silme çıkar.",
            source = NoteStyle(colorIndex = 4).encode(),
        )
    }

    private companion object {
        const val PREFS_NAME = "merkez_notlar"
        const val KEY_SEEDED = "samples_seeded"
    }
}
