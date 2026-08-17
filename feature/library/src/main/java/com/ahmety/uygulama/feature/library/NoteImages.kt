package com.ahmety.uygulama.feature.library

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Nota eklenen fotoğraflar uygulamanın kendi klasörüne kopyalanıyor. Seçicinin
 * verdiği `content://` izni geçicidir; kopyalamazsak not sonraki açılışta
 * boş bir kutu gösterirdi.
 */
suspend fun copyImageToNotes(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
    runCatching {
        val folder = File(context.filesDir, "not-gorselleri").apply { mkdirs() }
        val target = File(folder, "not_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: return@runCatching null
        target.absolutePath
    }.getOrNull()
}

/** Dosyadaki görseli arka planda, ekrana yetecek çözünürlükte çözer. */
@Composable
fun rememberNoteImage(path: String): ImageBitmap? {
    var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(path) {
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, bounds)
                var sample = 1
                while (bounds.outWidth / sample > TARGET_WIDTH_PX) sample *= 2
                val options = BitmapFactory.Options().apply { inSampleSize = sample }
                BitmapFactory.decodeFile(path, options)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

private const val TARGET_WIDTH_PX = 720
