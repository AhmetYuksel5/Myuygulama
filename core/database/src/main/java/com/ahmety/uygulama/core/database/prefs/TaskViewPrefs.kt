package com.ahmety.uygulama.core.database.prefs

import android.content.Context

/**
 * Görev listesinin görünüm tercihleri. Hem uygulama ekranı hem ana ekran
 * widget'ı okusun diye çekirdekte duruyor; ikisi ayrı tercih tutsaydı
 * "uygulamada gizli, widget'ta görünür" gibi tutarsızlıklar çıkardı.
 */
class TaskViewPrefs(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Üstü çizili (tamamlanmış) görevler listede hiç görünmesin. */
    var hideCompleted: Boolean
        get() = prefs.getBoolean(KEY_HIDE_COMPLETED, false)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_COMPLETED, value).apply()

    private companion object {
        const val PREFS_NAME = "merkez_gorev_gorunum"
        const val KEY_HIDE_COMPLETED = "hide_completed"
    }
}
