package com.ahmety.uygulama.ui.gestures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmety.uygulama.feature.gestures.GestureFeedback
import com.ahmety.uygulama.feature.gestures.GestureSettings

/**
 * Kenar hareketlerinin ayar ekranı — Fluid NG'nin yerine geçen modül.
 *
 * Servisin kendisi yalnızca sistem ayarlarından açılabiliyor; Android bir
 * uygulamanın kendi erişilebilirlik servisini programatik olarak açmasına
 * izin vermiyor ve bu doğru bir kısıt. Biz sadece oraya yönlendiriyoruz.
 */
@Composable
fun GestureSettingsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { GestureSettings(context) }

    var serviceEnabled by remember { mutableStateOf(GestureSettings.isServiceEnabled(context)) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var onRight by remember { mutableStateOf(settings.onRightEdge) }
    var widthDp by remember { mutableIntStateOf(settings.widthDp) }
    var heightDp by remember { mutableIntStateOf(settings.heightDp) }
    var vibrate by remember { mutableStateOf(settings.vibrateEnabled) }
    var vibrateStrength by remember { mutableIntStateOf(settings.vibrateStrength) }

    // Sistem ayarlarından dönüldüğünde durumu tazeliyoruz.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = GestureSettings.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Kenar hareketleri", style = MaterialTheme.typography.headlineMedium)
        Text(
            text = "Ekranın kenarındaki şeritten yukarı kaydırınca son uygulamalar, " +
                "aşağı kaydırınca bildirim paneli, içeri kaydırınca geri.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Neden erişilebilirlik izni?", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "\"Son uygulamaları aç\" ve \"bildirim panelini indir\" " +
                        "komutlarını verebilmenin tek yolu bu API. Fluid NG'nin " +
                        "Play Store'a çıkamamasının sebebi de buydu.\n\n" +
                        "Farkımız: bu servis ekran içeriğini okuma bayrağı kapalı " +
                        "tanımlandı. Yani yazdığın şifreyi, banka bakiyeni, gelen " +
                        "SMS'i göremez — sadece jesti alıp komutu tetikler.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = if (serviceEnabled) "Servis açık." else "Servis kapalı.",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "Sistem ayarlarında Erişilebilirlik → İndirilen uygulamalar → " +
                        "Kenar hareketleri.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = {
                        context.startActivity(GestureSettings.accessibilitySettingsIntent())
                    },
                ) {
                    Text(if (serviceEnabled) "Ayarları aç" else "Servisi aç")
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Şeridi göster", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    settings.enabled = it
                },
            )
        }

        Text("Kenar", style = MaterialTheme.typography.labelLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = !onRight,
                onClick = {
                    onRight = false
                    settings.onRightEdge = false
                },
                label = { Text("Sol") },
            )
            FilterChip(
                selected = onRight,
                onClick = {
                    onRight = true
                    settings.onRightEdge = true
                },
                label = { Text("Sağ") },
            )
        }

        Stepper(
            label = "Kalınlık",
            value = widthDp,
            range = 2..16,
            suffix = "dp",
        ) {
            widthDp = it
            settings.widthDp = it
        }

        Stepper(
            label = "Uzunluk",
            value = heightDp,
            range = 60..400,
            step = 20,
            suffix = "dp",
        ) {
            heightDp = it
            settings.heightDp = it
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Titreşim", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = vibrate,
                onCheckedChange = {
                    vibrate = it
                    settings.vibrateEnabled = it
                    if (it) GestureFeedback.vibrate(context, settings)
                },
            )
        }

        if (vibrate) {
            Text("Titreşim gücü", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Hafif", "Orta", "Güçlü").forEachIndexed { index, label ->
                    FilterChip(
                        selected = vibrateStrength == index,
                        onClick = {
                            vibrateStrength = index
                            settings.vibrateStrength = index
                            // Seçer seçmez hissettir: ayrı bir "dene" düğmesine gerek kalmasın.
                            GestureFeedback.vibrate(context, settings)
                        },
                        label = { Text(label) },
                    )
                }
            }
        }

        Text(
            text = "Değişikliklerin görünmesi için servisi kapatıp açman gerekebilir; " +
                "katman servis başlarken çiziliyor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    step: Int = 1,
    suffix: String,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium)
        TextButton(
            enabled = value > range.first,
            onClick = { onChange((value - step).coerceIn(range)) },
        ) {
            Text("−")
        }
        Text(text = "$value $suffix", style = MaterialTheme.typography.bodyLarge)
        TextButton(
            enabled = value < range.last,
            onClick = { onChange((value + step).coerceIn(range)) },
        ) {
            Text("+")
        }
    }
}
