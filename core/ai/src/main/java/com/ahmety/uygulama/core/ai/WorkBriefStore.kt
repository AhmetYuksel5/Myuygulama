package com.ahmety.uygulama.core.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Eser künyesi: bir kitabın ya da filmin kısa tanıtımı.
 *
 * Metnin tamamını her kelime sorgusunda göndermek hem yavaş hem pahalı, bir
 * kitapta zaten modelin penceresine sığmıyor. Bunun yerine metni bir kez
 * gönderip yüz elli kelimelik bir künye çıkarıyoruz — dönem, mekân, kişiler,
 * dilin düzeyi, tekrar eden argo — ve onu her sorguya ekliyoruz.
 *
 * Kazancı somut: künyede "1970'ler New York mafyası, muhbir teması, ağır
 * argo" yazıyorsa model "stool" için tabure ile muhbir arasında
 * duraksamıyor.
 */
@Singleton
class WorkBriefStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(work: String): String? =
        prefs.getString(key(work), null)?.takeIf { it.isNotBlank() }

    fun put(work: String, brief: String) {
        prefs.edit().putString(key(work), brief.trim()).apply()
    }

    fun forget(work: String) {
        prefs.edit().remove(key(work)).apply()
    }

    private fun key(work: String) = work.trim().lowercase()

    private companion object {
        const val PREFS_NAME = "merkez_eser_kunyesi"
    }
}
