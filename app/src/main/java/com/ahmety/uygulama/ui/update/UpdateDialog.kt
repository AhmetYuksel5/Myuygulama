package com.ahmety.uygulama.ui.update

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Güncelleme penceresi: açılır açılmaz kontrol eder.
 *
 * Ayrı bir sayfaya gidip düğmeye basmak gereksiz bir adımdı; burada
 * açılışta kontrol başlıyor ve sonuç aynı pencerede görünüyor.
 */
@Composable
fun UpdateDialog(
    onDismiss: () -> Unit,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) { viewModel.check() }

    val downloading = state.downloadProgress != null
    val available = state.available

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Güncelleme") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Yüklü sürüm: ${state.currentVersion.ifBlank { "bilinmiyor" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                when {
                    state.checking -> {
                        Text("Kontrol ediliyor…", style = MaterialTheme.typography.bodyMedium)
                        CircularProgressIndicator()
                    }

                    downloading -> {
                        Text("İndiriliyor…", style = MaterialTheme.typography.bodyMedium)
                        LinearProgressIndicator(
                            progress = { state.downloadProgress ?: 0f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    available != null -> {
                        Text(
                            text = "Yeni sürüm var: ${available.versionName}",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        ReleaseNotes(available.notes)
                    }

                    else -> Text(
                        text = state.message ?: "En güncel sürümü kullanıyorsun.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                if (!state.canInstall) {
                    Text(
                        text = "Kurulum için \"bilinmeyen kaynaklara izin ver\" gerekiyor.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            when {
                // İzin yoksa yapılacak tek iş bu; "kapat" düğmesi yerini
                // kaplamasın diye onay tarafına alındı.
                !state.canInstall -> TextButton(
                    onClick = {
                        viewModel.openInstallPermissionSettings { intent ->
                            runCatching { context.startActivity(intent) }
                        }
                    },
                ) {
                    Text("İzin ver")
                }

                available != null && !downloading -> TextButton(
                    onClick = { viewModel.downloadAndInstall() },
                ) {
                    Text("İndir ve kur")
                }

                !state.checking && !downloading -> TextButton(onClick = { viewModel.check() }) {
                    Text("Yeniden kontrol et")
                }

                else -> Unit
            }
        },
        // Kapatma her durumda açık: indirme takılırsa pencereden çıkışın
        // hiçbir yolu kalmıyordu. İndirme görünüm modelinde sürdüğü için
        // pencereyi kapatmak indirmeyi iptal etmiyor.
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (downloading) "Arka planda sürsün" else "Kapat")
            }
        },
    )
}

/**
 * Sürüm notu: maddeli, kaydırılabilir, kaydırma çubuklu.
 *
 * Eskiden not dört yüz karakterde kesiliyordu ve son cümle yarıda
 * kalıyordu. Artık tamamı duruyor; sığmayınca kaydırılıyor ve sağdaki ince
 * çubuk daha aşağıda bir şey olduğunu gösteriyor.
 */
@Composable
private fun ReleaseNotes(raw: String) {
    val blocks = remember(raw) { formatReleaseNotes(raw) }
    if (blocks.isEmpty()) return

    val scroll = rememberScrollState()
    val density = LocalDensity.current

    BoxWithConstraints(modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scroll)
                // Çubuğun altında yazı kalmasın.
                .padding(end = 10.dp),
        ) {
            blocks.forEach { block ->
                when (block) {
                    is NoteBlock.Heading -> Text(
                        text = block.text,
                        style = MaterialTheme.typography.titleSmall,
                    )

                    is NoteBlock.Paragraph -> Text(
                        text = block.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    is NoteBlock.Bullet -> Row {
                        // Sabit genişlik: maddenin ikinci satırı da işaretin
                        // sağından başlıyor, altına kaymıyor.
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(14.dp),
                        )
                        Text(
                            text = block.text,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Kaydırılacak bir şey yoksa çubuk da yok.
        if (scroll.maxValue > 0) {
            val viewport = with(density) { maxHeight.toPx() }
            val content = viewport + scroll.maxValue
            val minThumb = with(density) { 24.dp.toPx() }
            val thumb = (viewport * viewport / content).coerceAtLeast(minThumb)
            val travel = (viewport - thumb).coerceAtLeast(0f)
            val progress = scroll.value.toFloat() / scroll.maxValue
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = with(density) { (travel * progress).toDp() })
                    .width(3.dp)
                    .height(with(density) { thumb.toDp() })
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)),
            )
        }
    }
}
