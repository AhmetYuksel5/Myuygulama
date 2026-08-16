package com.ahmety.uygulama.feature.habits

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.MoreVert
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * "Bugün" ekranında görünen alışkanlık bölümü. Ayrı bir ekran değil, çünkü
 * alışkanlıkların doğal yeri günün geri kalanının yanı.
 */
@Composable
fun HabitsSection(
    state: HabitsUiState,
    onAdvance: (HabitUiItem) -> Unit,
    onArchive: (HabitUiItem) -> Unit,
    onDelete: (HabitUiItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val due = state.dueToday
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Alışkanlıklar",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
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
    onArchive: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val accent = item.habit.colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.primary

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = item.isDueToday, onClick = onAdvance),
        colors = CardDefaults.cardColors(
            containerColor = if (item.isDoneToday) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
            ) {
                Text(
                    text = item.habit.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = subtitleFor(item),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, contentDescription = "Seçenekler")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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

@Composable
private fun ProgressBadge(
    count: Int,
    target: Int,
    done: Boolean,
    enabled: Boolean,
    accent: Color,
) {
    Box(
        modifier = Modifier.size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            done -> Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Tamamlandı",
                    tint = MaterialTheme.colorScheme.surface,
                )
            }

            target > 1 -> {
                CircularProgressIndicator(
                    progress = { count.toFloat() / target },
                    modifier = Modifier.size(40.dp),
                    color = accent,
                )
                Text(
                    text = "$count/$target",
                    style = MaterialTheme.typography.labelSmall,
                )
            }

            else -> CircularProgressIndicator(
                progress = { 0f },
                modifier = Modifier.size(40.dp),
                color = if (enabled) accent else MaterialTheme.colorScheme.outlineVariant,
            )
        }
    }
}

private fun subtitleFor(item: HabitUiItem): String {
    val parts = mutableListOf<String>()
    if (!item.isDueToday) parts += "bugün değil"
    if (item.currentStreak > 0) parts += streakLabel(item.habit.schedule, item.currentStreak)
    if (parts.isEmpty()) parts += scheduleLabel(item.habit.schedule)
    return parts.joinToString(" · ")
}
