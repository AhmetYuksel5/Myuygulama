package com.ahmety.uygulama.feature.vocab

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Silinen kelimeler.
 *
 * Sabit deste uygulamanın asset'inde duruyor, oradan bir kayıt silinemez;
 * bu yüzden "bir daha gösterme" kararını ayrı tutuyoruz. Kitaptan/filmden
 * gelen kelimenin kaydı gerçekten siliniyor, ama aynı kelime aynı kitapta
 * yeniden işaretlenirse geri gelmesin diye o da buraya yazılıyor.
 */
@Singleton
class HiddenWordStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Volatile
    private var cache: Set<String>? = null

    fun words(): Set<String> = cache ?: read().also { cache = it }

    fun hide(word: String) {
        val next = words() + word.trim().lowercase()
        cache = next
        prefs.edit().putStringSet(KEY_WORDS, next).apply()
    }

    fun unhide(word: String) {
        val next = words() - word.trim().lowercase()
        cache = next
        prefs.edit().putStringSet(KEY_WORDS, next).apply()
    }

    private fun read(): Set<String> = prefs.getStringSet(KEY_WORDS, emptySet()).orEmpty().toSet()

    private companion object {
        const val PREFS_NAME = "merkez_kelime_gizli"
        const val KEY_WORDS = "hidden"
    }
}
