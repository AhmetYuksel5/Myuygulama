package com.ahmety.uygulama.ui.gestures

import android.content.Context
import android.content.Intent

/** Cihazdaki bir uygulama: paket adı ve kullanıcıya görünen adı. */
data class AppInfo(val packageName: String, val label: String)

/**
 * Cihazdaki başlatılabilir uygulamaları alfabetik döndürür.
 *
 * Kenar hareketine "şu uygulamayı aç" atarken seçim listesi buradan
 * geliyor. Eskiden kendi ana ekranımızın parçasıydı; ana ekran kalkınca
 * hareketler ekranıyla birlikte kaldı — tek kullanıcısı o.
 *
 * Sorgu birkaç yüz uygulamada yavaş; çağıran taraf listeyi kutu açılınca
 * bir kez hazırlıyor.
 */
fun loadLaunchableApps(context: Context): List<AppInfo> {
    val packages = context.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return runCatching {
        packages.queryIntentActivities(intent, 0)
            .mapNotNull { resolved ->
                val name = resolved.activityInfo?.packageName ?: return@mapNotNull null
                AppInfo(name, resolved.loadLabel(packages).toString())
            }
            .distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }.getOrDefault(emptyList())
}
