package com.ahmety.uygulama.feature.habits

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.core.designsystem.MerkezIcons
import com.ahmety.uygulama.core.designsystem.MerkezPalette

/**
 * "Bugün" ekranındaki alışkanlık bölümü.
 *
 * Satır düzeni popüler alışkanlık uygulamalarının ortak kalıbını izliyor:
 * ilerleme rozeti + ad + 🔥 seri + son 7 günün nokta şeridi. Şeritteki
 * geçmiş günlere dokunarak unutulan işaretleme geriye dönük yapılabiliyor —
 * bu, kategorinin en sevilen özelliği (seri kaybı en büyük kullanıcı acısı).
 */
@Composable
fun HabitsSection(
    state: HabitsUiState,
    onAdvance: (HabitUiItem) -> Unit,
    onToggleDay: (HabitUiItem, Int) -> Unit,
    onArchive: (HabitUiItem) -> Unit,
    onDelete: (HabitUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val due = state.dueToday
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Alışkanlıklar",
                style = MaterialTheme.typography.titleLarge,
            )
            if (due.isNotEmpty()) {
                Text(
                    text = "${state.doneCount}/${due.size}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.loaded && state.items.isEmpty()) {
            Text(
                text = "Henüz alışkanlık yok. Sağ alttaki düğmeyle ekleyebilirsin.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Bugün yapılması gerekenler üstte, diğerleri altta.
        val ordered = due + state.items.filterNot { it.isDueToday }
        ordered.forEach { item ->
            HabitRow(
                item = item,
                onAdvance = { onAdvance(item) },
                onToggleDay = { date -> onToggleDay(item, date) },
                onArchive = { onArchive(item) },
                onDelete = { onDelete(item) },
            )
        }
    }
}

@Composable
private fun HabitRow(
    item: HabitUiItem,
    onAdvance: () -> Unit,
    onToggleDay: (Int) -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current
    val accent = item.habit.colorArgb?.let { Color(it) }
        ?: MerkezPalette.colorFor(item.habit.uuid)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isDueToday) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onAdvance()
            },
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDoneToday) {
                accent.copy(alpha = 0.10f)
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ProgressBadge(
                count = item.todayCount,
                target = item.habit.targetPerDay,
                done = item.isDoneToday,
                enabled = item.isDueToday,
                accent = accent,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.habit.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (item.currentStreak > 0) {
                        Text(
                            text = "  🔥${item.currentStreak}",
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                WeekStrip(
                    week = item.week,
                    accent = accent,
                    onToggleDay = { date ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onToggleDay(date)
                    },
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(MerkezIcons.MoreVert, contentDescription = "Seçenekler")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text(scheduleLabel(item.habit.schedule)) },
                        onClick = { menuOpen = false },
                        enabled = false,
                    )
                    DropdownMenuItem(
                        text = { Text(if (item.habit.archived) "Arşivden çıkar" else "Arşivle") },
                        onClick = {
                            menuOpen = false
                            onArchive()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("Sil") },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

/** Son 7 gün: dolu nokta = yapıldı, soluk = yapılmadı, halka = bugün. */
@Composable
private fun WeekStrip(
    week: List<DayCell>,
    accent: Color,
    onToggleDay: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        week.forEach { cell ->
            val color = when {
                cell.isComplete -> accent
                cell.isDue -> MaterialTheme.colorScheme.outlineVariant
                else -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            }
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .background(color, CircleShape)
                    // Bugüne dokunmak kartın kendisiyle aynı işi yapar; şerit
                    // geçmiş günleri düzeltmek için.
                    .clickable(enabled = !cell.isToday) { onToggleDay(cell.date) },
                contentAlignment = Alignment.Center,
            ) {
                if (cell.isToday && !cell.isComplete) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.surface, CircleShape),
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressBadge(
    count: Int,
    target: Int,
    done: Boolean,
    enabled: Boolean,
    accent: Color,
) {
    // Tamamlanınca küçük bir "pop": Streaks'in en övülen hissi.
    val scale by animateFloatAsState(
        targetValue = if (done) 1f else 0.92f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "habitPop",
    )
    val progress by animateFloatAsState(
        targetValue = if (target <= 0) 0f else (count.toFloat() / target).coerceIn(0f, 1f),
        label = "habitProgress",
    )

    Box(
        modifier = Modifier
            .size(44.dp)
            .scale(scale),
        contentAlignment = Alignment.Center,
    ) {
        when {
            done -> Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = MerkezIcons.Check,
                    contentDescription = "Tamamlandı",
                    tint = Color.White,
                )
            }

            else -> {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(44.dp),
                    color = if (enabled) accent else MaterialTheme.colorScheme.outlineVariant,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                )
                if (target > 1) {
                    Text(
                        text = "$count/$target",
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}
