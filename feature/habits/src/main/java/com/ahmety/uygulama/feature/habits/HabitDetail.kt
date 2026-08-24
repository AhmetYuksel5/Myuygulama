package com.ahmety.uygulama.feature.habits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.core.designsystem.MerkezPalette
import com.ahmety.uygulama.core.model.HabitCheck
import com.ahmety.uygulama.core.model.HabitStreaks
import kotlinx.datetime.LocalDate

/** Bir yıl kaç haftaya sığıyor: elli iki tam hafta artı içinde bulunulan. */
private const val WEEKS = 53

/**
 * Alışkanlığın detayı: seriler ve bir yıllık takvim.
 *
 * Satırdaki yedi günlük şerit "bu hafta ne oldu" sorusunu cevaplıyor ama
 * "geçen kış ne yapıyordum" sorusunu cevaplamıyor. Bir yıllık ızgara
 * mevsimliği gösteriyor — insanlar alışkanlıklarını hep aynı aylarda
 * bırakıyor ve bu ancak böyle görünüyor.
 *
 * Izgaradaki her kareye dokunarak o günü işaretleyip kaldırabiliyorsun:
 * "dün yapmıştım, işaretlemeyi unutmuşum" en sık ihtiyaç.
 */
@Composable
internal fun HabitDetailDialog(
    item: HabitUiItem,
    checks: List<HabitCheck>,
    today: Int,
    onToggleDay: (date: Int, done: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    val accent = item.habit.colorArgb?.let { Color(it) }
        ?: MerkezPalette.colorFor(item.habit.uuid)
    val target = item.habit.targetPerDay.coerceAtLeast(1)

    val completed = remember(checks, target) {
        checks.filter { it.count >= target }.map { it.date }.toSet()
    }
    val longest = remember(completed, item.habit.schedule, today) {
        HabitStreaks.longestStreak(item.habit.schedule, completed, today)
    }
    // Izgaranın kapsadığı aralık: bugünün haftasının sonundan geriye bir yıl.
    val gridEnd = remember(today) { HabitStreaks.weekStart(today) + 6 }
    val gridStart = remember(gridEnd) { gridEnd - WEEKS * 7 + 1 }
    val inWindow = remember(completed, gridStart, gridEnd) {
        completed.count { it in gridStart..gridEnd }
    }

    var selected by remember { mutableStateOf<Int?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(item.habit.name, style = MaterialTheme.typography.titleLarge)
                Text(
                    text = scheduleLabel(item.habit.schedule),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat("Şu anki seri", "${item.currentStreak}", accent)
                    Stat("En uzun", "$longest", MaterialTheme.colorScheme.onSurface)
                    Stat("Son bir yıl", "$inWindow gün", MaterialTheme.colorScheme.onSurface)
                }

                YearGrid(
                    gridStart = gridStart,
                    today = today,
                    completed = completed,
                    accent = accent,
                    onTap = { date ->
                        selected = date
                        onToggleDay(date, date !in completed)
                    },
                )

                Text(
                    text = selected?.let { "${formatDate(it)} — dokununca işareti değişir" }
                        ?: "Bir kareye dokunarak geçmiş bir günü işaretleyebilirsin.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Kapat") } },
    )
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Bir yıllık ızgara: sütun hafta, satır haftanın günü.
 *
 * Tek bir [Canvas]; üç yüz yetmiş bir ayrı bileşen kurmak pencereyi
 * yavaşlatırdı. Dokunma noktası aynı hesapla güne çevriliyor.
 */
@Composable
private fun YearGrid(
    gridStart: Int,
    today: Int,
    completed: Set<Int>,
    accent: Color,
    onTap: (Int) -> Unit,
) {
    val density = LocalDensity.current
    val cell = 13.dp
    val gap = 3.dp
    val step = cell + gap
    val empty = MaterialTheme.colorScheme.surfaceVariant
    val todayRing = MaterialTheme.colorScheme.onSurfaceVariant

    val scroll = rememberScrollState()
    // En yeni hafta sağda; açılınca oraya bakılıyor.
    LaunchedEffect(Unit) { scroll.scrollTo(scroll.maxValue) }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll),
        ) {
            Column {
                MonthLabels(gridStart = gridStart, step = step)
                Canvas(
                    modifier = Modifier
                        .width(step * WEEKS)
                        .height(step * 7)
                        .pointerInput(gridStart, completed) {
                            detectTapGestures { offset ->
                                val stepPx = with(density) { step.toPx() }
                                val week = (offset.x / stepPx).toInt()
                                val day = (offset.y / stepPx).toInt()
                                if (week in 0 until WEEKS && day in 0..6) {
                                    val date = gridStart + week * 7 + day
                                    if (date <= today) onTap(date)
                                }
                            }
                        },
                ) {
                    val cellPx = cell.toPx()
                    val stepPx = step.toPx()
                    val radius = androidx.compose.ui.geometry.CornerRadius(cellPx * 0.25f)
                    for (week in 0 until WEEKS) {
                        for (day in 0..6) {
                            val date = gridStart + week * 7 + day
                            if (date > today) continue
                            val done = date in completed
                            drawRoundRect(
                                color = if (done) accent else empty,
                                topLeft = Offset(week * stepPx, day * stepPx),
                                size = Size(cellPx, cellPx),
                                cornerRadius = radius,
                            )
                            if (date == today) {
                                drawRoundRect(
                                    color = todayRing,
                                    topLeft = Offset(week * stepPx, day * stepPx),
                                    size = Size(cellPx, cellPx),
                                    cornerRadius = radius,
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = 2f,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Ay adları: ızgarada nerede olduğunu anlamanın tek yolu. */
@Composable
private fun MonthLabels(gridStart: Int, step: androidx.compose.ui.unit.Dp) {
    Row(modifier = Modifier.height(16.dp), verticalAlignment = Alignment.Bottom) {
        var previousMonth = -1
        for (week in 0 until WEEKS) {
            val date = LocalDate.fromEpochDays(gridStart + week * 7)
            val label = if (date.monthNumber != previousMonth) {
                previousMonth = date.monthNumber
                MONTHS[date.monthNumber - 1]
            } else {
                ""
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.width(step),
            )
        }
    }
}

private fun formatDate(epochDay: Int): String {
    val date = LocalDate.fromEpochDays(epochDay)
    return "${date.dayOfMonth} ${MONTHS[date.monthNumber - 1]} ${date.year}"
}

private val MONTHS = listOf(
    "Oca", "Şub", "Mar", "Nis", "May", "Haz",
    "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara",
)
