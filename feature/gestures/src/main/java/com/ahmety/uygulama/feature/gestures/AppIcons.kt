package com.ahmety.uygulama.feature.gestures

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache

/**
 * Uygulama simgeleri, bir kez yüklenip saklanıyor.
 *
 * Şerit her geçişte, kısayol kartı her açılışta simgeleri paket
 * yöneticisinden baştan çözüyordu; ikisi de dokunuşun hemen ardından ana
 * iş parçacığında çalıştığı için açılış takılıyordu.
 */
internal object AppIcons {
    private val cache = LruCache<String, Drawable>(32)

    fun get(context: Context, name: String): Drawable? {
        cache.get(name)?.let { return it.constantState?.newDrawable()?.mutate() ?: it }
        val icon = runCatching { context.packageManager.getApplicationIcon(name) }.getOrNull()
            ?: return null
        cache.put(name, icon)
        return icon
    }
}
