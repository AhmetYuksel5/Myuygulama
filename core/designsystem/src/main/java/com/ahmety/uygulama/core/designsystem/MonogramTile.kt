package com.ahmety.uygulama.core.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp

/**
 * Bir öğenin görseli; yoksa yerine geçen renkli karo.
 *
 * Kaydedilen sayfaların yarısında kapak resmi çıkmıyor — sayfa etiket
 * koymamış ya da resim indirilememiş. Boş bırakınca liste bozuk görünüyor:
 * gri delikler "yüklenemedi" diye okunuyor. Onun yerine kaynağın adından
 * türeyen bir degrade ve baş harf konuyor.
 *
 * Renk adın karma değerinden geliyor, yani aynı site her zaman aynı renk:
 * bir süre sonra yazıyı okumadan kaynağı renginden tanıyorsun.
 */
@Composable
fun MonogramTile(
    seed: String,
    label: String,
    image: ImageBitmap?,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
) {
    Box(modifier = modifier.clip(shape)) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            return@Box
        }

        val base = MerkezPalette.colorFor(seed)
        val brush = remember(base) {
            Brush.linearGradient(
                colors = listOf(base, base.copy(alpha = 0.55f).compositeOver(Color.Black)),
                start = Offset.Zero,
                end = Offset.Infinite,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(brush),
            contentAlignment = Alignment.BottomStart,
        ) {
            Text(
                text = label.trim().take(1).uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(start = 8.dp, bottom = 2.dp),
            )
        }
    }
}
