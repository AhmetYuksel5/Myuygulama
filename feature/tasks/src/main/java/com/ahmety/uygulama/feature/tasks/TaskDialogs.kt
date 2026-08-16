package com.ahmety.uygulama.feature.tasks

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ahmety.uygulama.core.database.importer.ImportFormat
import com.ahmety.uygulama.core.database.importer.ImportResult
import com.ahmety.uygulama.core.model.RecurrenceRule
import com.ahmety.uygulama.core.model.RecurrenceUnit
import com.ahmety.uygulama.core.model.TaskPriority

@Composable
internal fun SingleFieldDialog(
    title: String,
    label: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = value.isNotBlank(), onClick = { onConfirm(value.trim()) }) {
                Text("Ekle")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private enum class DueChoice(val label: String) {
    NONE("Tarihsiz"),
    TODAY("Bugün"),
    TOMORROW("Yarın"),
    NEXT_WEEK("Gelecek hafta"),
}

private enum class RepeatChoice(val label: String) {
    NONE("Tekrar yok"),
    DAILY("Her gün"),
    WEEKLY("Her hafta"),
    MONTHLY("Her ay"),
}

@Composable
fun AddTaskDialog(
    today: Int,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        dueDate: Int?,
        priority: TaskPriority,
        recurrence: RecurrenceRule?,
    ) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var due by remember { mutableStateOf(DueChoice.NONE) }
    var repeat by remember { mutableStateOf(RepeatChoice.NONE) }
    var highPriority by remember { mutableStateOf(false) }

    val dueDate = when (due) {
        DueChoice.NONE -> null
        DueChoice.TODAY -> today
        DueChoice.TOMORROW -> today + 1
        DueChoice.NEXT_WEEK -> today + 7
    }
    val recurrence = when (repeat) {
        RepeatChoice.NONE -> null
        RepeatChoice.DAILY -> RecurrenceRule(RecurrenceUnit.DAY)
        RepeatChoice.WEEKLY -> RecurrenceRule(RecurrenceUnit.WEEK)
        RepeatChoice.MONTHLY -> RecurrenceRule(RecurrenceUnit.MONTH)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Yeni görev") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Ne yapılacak?") },
                    modifier = Modifier.fillMaxWidth(),
                )

                Text("Tarih", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DueChoice.entries.forEach { option ->
                        FilterChip(
                            selected = due == option,
                            onClick = { due = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                Text("Tekrar", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RepeatChoice.entries.forEach { option ->
                        FilterChip(
                            selected = repeat == option,
                            onClick = { repeat = option },
                            label = { Text(option.label) },
                        )
                    }
                }

                FilterChip(
                    selected = highPriority,
                    onClick = { highPriority = !highPriority },
                    label = { Text("Öncelikli") },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onConfirm(
                        title.trim(),
                        dueDate,
                        if (highPriority) TaskPriority.HIGH else TaskPriority.NONE,
                        recurrence,
                    )
                },
            ) {
                Text("Ekle")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

/**
 * Microsoft To Do'nun dışa aktarma düğmesi yok. Kullanıcı ya Graph Explorer'dan
 * aldığı JSON'u ya da elle yazdığı listeyi yapıştırır; biçimi ayrıştırıcı anlar.
 * Yazmadan önce özet gösteriyoruz ki yanlış yapıştırma sessizce içeri girmesin.
 */
@Composable
internal fun ImportTasksDialog(
    onDismiss: () -> Unit,
    onPreview: (String) -> ImportResult,
    onConfirm: (ImportResult) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val preview = remember(input) { if (input.isBlank()) null else onPreview(input) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Görevleri içe aktar") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Graph Explorer'dan aldığın JSON'u ya da düz liste metnini " +
                        "buraya yapıştır. Adımlar için: docs/TODO-ICE-AKTARMA.md",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("Yapıştır") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 140.dp, max = 260.dp),
                )
                preview?.let { Text(previewSummary(it), style = MaterialTheme.typography.bodyMedium) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = preview != null && (preview.taskCount > 0 || preview.lists.isNotEmpty()),
                onClick = { preview?.let(onConfirm) },
            ) {
                Text("İçe aktar")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Vazgeç") } },
    )
}

private fun previewSummary(result: ImportResult): String = when (result.format) {
    ImportFormat.EMPTY -> "Tanınabilir bir şey bulunamadı."
    ImportFormat.GRAPH_LISTS ->
        "Graph liste çıktısı tanındı: ${result.lists.size} liste oluşturulacak " +
            "(görevleri ayrıca yapıştırman gerekiyor)."

    ImportFormat.GRAPH_TASKS ->
        "Graph görev çıktısı tanındı: ${result.taskCount} görev."

    ImportFormat.PLAIN_TEXT ->
        "Düz metin tanındı: ${result.lists.size} liste, ${result.taskCount} görev."
}
