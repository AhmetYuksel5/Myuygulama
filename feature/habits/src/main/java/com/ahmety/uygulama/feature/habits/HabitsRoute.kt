package com.ahmety.uygulama.feature.habits

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.ahmety.uygulama.core.designsystem.MerkezTopBar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmety.uygulama.core.designsystem.MerkezIcons

/**
 * Alışkanlıklar ekranı.
 *
 * Eskiden "Bugün" adlı bir ekranın içinde, ajanda ve günün özetiyle birlikte
 * duruyordu. O ekran kaldırıldı: alışkanlık takibi kendi başına bir iş ve
 * yanına konan şeyler onu seyreltiyordu.
 *
 * Üstte tek satır — bugün kaçta kaç — ve ince bir çubuk. Puan, seviye,
 * rozet yok: ikinci bir sayaç, asıl sayaç olan seriyle yarışıyor.
 */
@Composable
fun HabitsRoute(
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    viewModel: HabitsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    // Detayı açık olan alışkanlık. Kimlikle tutuluyor ki işaretleme
    // yapıldığında penceredeki sayılar da tazelensin.
    var detailUuid by remember { mutableStateOf<String?>(null) }

    // Gün dönmüş olabilir: uygulamaya her dönüşte bugünü tazeliyoruz.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshToday()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Detay artık kutu değil ekran: listenin yerini alıyor. Gezinme
    // grafiğine yeni bir rota koymadık, çünkü alışkanlıklar zaten "Daha"nın
    // altında bir alt sayfa ve iki kat derinlik geri tuşunu karıştırıyor.
    val detailItem = detailUuid?.let { uuid ->
        state.items.firstOrNull { it.habit.uuid == uuid }
    }
    if (detailItem != null) {
        BackHandler { detailUuid = null }
        val history by remember(detailItem.habit.uuid) {
            viewModel.historyOf(detailItem.habit.uuid)
        }.collectAsStateWithLifecycle(initialValue = emptyList())
        HabitDetailScreen(
            item = detailItem,
            checks = history,
            today = state.today,
            onToggleDay = { date, done -> viewModel.setDay(detailItem, date, done) },
            onDismiss = { detailUuid = null },
            modifier = modifier,
        )
        return
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (onBack != null) {
                MerkezTopBar(title = "Alışkanlıklar", onBack = onBack)
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val due = state.dueToday.size
                val done = state.doneCount
                // Ad üst çubukta duruyor; buradaki satır günün durumu.
                Text(
                    text = if (due == 0) "Bugün için bir şey yok" else "Bugün $done/$due",
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (due > 0) {
                    LinearProgressIndicator(
                        progress = { done.toFloat() / due },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            HabitsSection(
                state = state,
                onOpen = { detailUuid = it.habit.uuid },
                onAdvance = viewModel::advance,
                onToggleDay = viewModel::toggleDay,
                onArchive = { viewModel.setArchived(it.habit.uuid, !it.habit.archived) },
                onDelete = { viewModel.deleteHabit(it.habit.uuid) },
            )
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(MerkezIcons.Add, contentDescription = "Alışkanlık ekle")
        }
    }

    if (showAdd) {
        AddHabitDialog(
            onDismiss = { showAdd = false },
            onConfirm = { name, schedule, target ->
                viewModel.createHabit(name, schedule, target)
                showAdd = false
            },
        )
    }
}
