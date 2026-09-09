package com.ahmety.uygulama.ui.gestures

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ahmety.uygulama.feature.gestures.QuickCursorSettings

@Composable
fun QuickCursorScreen(onBack: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings = remember { QuickCursorSettings(context) }

    var serviceEnabled by remember { mutableStateOf(QuickCursorSettings.isServiceEnabled(context)) }
    var enabled by remember { mutableStateOf(settings.enabled) }
    var barWidth by remember { mutableIntStateOf(settings.handleWidthDp) }
    var barHeight by remember { mutableIntStateOf(settings.handleHeightDp) }
    var opacity by remember { mutableIntStateOf(settings.opacityPercent) }
    var sensitivityTimes10 by remember { mutableIntStateOf((settings.sensitivity * 10).toInt()) }
    var offset by remember { mutableIntStateOf(settings.bottomOffsetDp) }
    var centerOffset by remember { mutableIntStateOf(settings.centerOffsetDp) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                serviceEnabled = QuickCursorSettings.isServiceEnabled(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        MerkezTopBar(title = "Tek elle imleç", onBack = onBack)
        Text(
            text = "Ekranın dibindeki çubuğa parmağını basıp gezdir; ekranda bir imleç " +
                "trackpad gibi dolaşır, parmağını kaldırınca oraya dokunur. Sol üst gibi " +
                "tek elle ulaşamadığın yerlere basmak için. Çubuğu taşımak için uzun " +
                "basıp sürükle.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        ServiceCard(
            enabled = serviceEnabled,
            explanation = "İmlecin durduğu yere gerçek dokunma gönderebilmek için jest " +
                "gönderme yetkisi ister. Ekran içeriğini yine okumaz.",
            onOpen = { context.startActivity(QuickCursorSettings.accessibilitySettingsIntent()) },
        )

        Toggle("Tek elle imleci göster", enabled) {
            enabled = it
            settings.enabled = it
        }

        Text("Çubuğun ölçüsü", style = MaterialTheme.typography.labelLarge)
        Stepper("En", barWidth, 60..320, step = 10, suffix = "dp") {
            barWidth = it; settings.handleWidthDp = it
        }
        Stepper("Kalınlık", barHeight, 8..56, step = 2, suffix = "dp") {
            barHeight = it; settings.handleHeightDp = it
        }

        Text("Çubuğun yeri", style = MaterialTheme.typography.labelLarge)
        Stepper("Alttan yukarı", offset, 0..1200, step = 10, suffix = "dp") {
            offset = it; settings.bottomOffsetDp = it
        }
        Stepper("Yatay kayma", centerOffset, -300..300, step = 10, suffix = "dp") {
            centerOffset = it; settings.centerOffsetDp = it
        }
        Text(
            text = "Alttan yukarı 0 = ekranın tam dibinde. Yatay kayma 0 = ortada, " +
                "eksi değer sola, artı değer sağa. Çubuğa uzun basıp sürükleyerek de " +
                "taşıyabilirsin; bıraktığın yer kaydedilir.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text("İnce ayarlar", style = MaterialTheme.typography.labelLarge)
        Stepper("Saydamlık", opacity, 20..100, step = 5, suffix = "%") { opacity = it; settings.opacityPercent = it }
        Stepper("Hassasiyet", sensitivityTimes10, 10..40, step = 2, suffix = "×10") {
            sensitivityTimes10 = it
            settings.sensitivity = it / 10f
        }
        Text(
            text = "Hassasiyet, parmak hareketinin kaç katı imleç hareketi olduğu. " +
                "Yüksek değer küçük hareketle ekranın öbür ucuna ulaştırır.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
