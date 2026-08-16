package com.ahmety.uygulama.feature.habits

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.core.model.HabitSchedule

private enum class ScheduleKind(val label: String) {
    DAILY("Her gün"),
    SPECIFIC_DAYS("Belirli günler"),
    TIMES_PER_WEEK("Haftada N kez"),
}

@Composable
fun AddHabitDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, schedule: HabitSchedule, targetPerDay: Int) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ScheduleKind.DAILY) }
    var daysMask by remember { mutableIntStateOf(0b0011111) } // hafta içi
    var timesPerWeek by remember { mutableIntStateOf(3) }
    var targetPerDay by remember { mutableIntStateOf(1) }

    val schedule = when (kind) {
        ScheduleKind.DAILY -> HabitSchedule.Daily
        ScheduleKind.SPECIFIC_DAYS -> HabitSchedule.SpecificDays(daysMask)
        ScheduleKind.TIMES_PER_WEEK -> HabitSchedule.TimesPerWeek(timesPerWeek)
    }
    val valid = name.isNotBlank() &&
        (kind != ScheduleKind.SPECIFIC_DAYS || daysMask != 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni alışkanlık") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Ad") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Sıklık", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ScheduleKind.entries.forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                when (kind) {
                    ScheduleKind.SPECIFIC_DAYS -> Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        dayShortNames.forEachIndexed { index, label ->
                            val selected = (daysMask shr index) and 1 == 1
                            FilterChip(
                                selected = selected,
                                onClick = { daysMask = daysMask xor (1 shl index) },
                                label = { Text(label) },
                            )
                        }
                    }

                    ScheduleKind.TIMES_PER_WEEK -> Stepper(
                        label = "Haftada",
                        value = timesPerWeek,
                        range = 1..7,
                        onChange = { timesPerWeek = it },
                        suffix = "kez",
                    )

                    ScheduleKind.DAILY -> Unit
                }

                Stepper(
                    label = "Günde",
                    value = targetPerDay,
                    range = 1..20,
                    onChange = { targetPerDay = it },
                    suffix = "kez",
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = { onConfirm(name.trim(), schedule, targetPerDay) },
            ) {
                Text("Ekle")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Vazgeç") }
        },
    )
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    range: IntRange,
    onChange: (Int) -> Unit,
    suffix: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
        TextButton(
            enabled = value > range.first,
            onClick = { onChange((value - 1).coerceIn(range)) },
        ) {
            Text("−")
        }
        Text(
            text = "$value $suffix",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )
        TextButton(
            enabled = value < range.last,
            onClick = { onChange((value + 1).coerceIn(range)) },
        ) {
            Text("+")
        }
    }
}
