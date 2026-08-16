package com.ahmety.uygulama.feature.tasks

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun TasksRoute(
    modifier: Modifier = Modifier,
    viewModel: TasksViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val importMessage by viewModel.importMessage.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var showImport by remember { mutableStateOf(false) }
    var showNewList by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    RefreshTodayOnResume(viewModel::refreshToday)

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, top = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Görevler",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "Seçenekler")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Yeni liste") },
                            onClick = {
                                menuOpen = false
                                showNewList = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("İçe aktar (To Do)") },
                            onClick = {
                                menuOpen = false
                                showImport = true
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.lists.forEach { list ->
                    FilterChip(
                        selected = list.uuid == state.selectedListUuid,
                        onClick = { viewModel.selectList(list.uuid) },
                        label = { Text(list.name) },
                    )
                }
                AssistChip(
                    onClick = { showNewList = true },
                    label = { Text("+ Liste") },
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 8.dp,
                    end = 8.dp,
                    bottom = 88.dp,
                ),
            ) {
                items(state.openTasks, key = { it.uuid }) { task ->
                    TaskRow(
                        task = task,
                        today = state.today,
                        onToggle = { viewModel.setCompleted(task, it) },
                    )
                }

                if (state.doneTasks.isNotEmpty()) {
                    item {
                        Column {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                            Text(
                                text = "Tamamlananlar (${state.doneTasks.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp),
                            )
                        }
                    }
                    items(state.doneTasks, key = { it.uuid }) { task ->
                        TaskRow(
                            task = task,
                            today = state.today,
                            onToggle = { viewModel.setCompleted(task, it) },
                        )
                    }
                }

                if (state.tasks.isEmpty()) {
                    item {
                        Text(
                            text = "Bu listede görev yok. Sağ alttan ekleyebilir ya da " +
                                "üstteki menüden To Do'dan içe aktarabilirsin.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showAdd = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Görev ekle")
        }
    }

    if (showAdd) {
        AddTaskDialog(
            today = state.today,
            onDismiss = { showAdd = false },
            onConfirm = { title, due, priority, recurrence ->
                viewModel.addTask(title, due, priority, recurrence)
                showAdd = false
            },
        )
    }

    if (showNewList) {
        SingleFieldDialog(
            title = "Yeni liste",
            label = "Liste adı",
            onDismiss = { showNewList = false },
            onConfirm = {
                viewModel.createList(it)
                showNewList = false
            },
        )
    }

    if (showImport) {
        ImportTasksDialog(
            onDismiss = { showImport = false },
            onPreview = viewModel::preview,
            onConfirm = {
                viewModel.import(it)
                showImport = false
            },
        )
    }

    importMessage?.let { message ->
        LaunchedEffect(message) { viewModel.clearImportMessage() }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 96.dp),
            )
        }
    }
}

/**
 * "Bugün" ekranındaki görev bölümü: bugüne ve geçmişe ait tamamlanmamış işler.
 * Geçmiş tarihliler de burada, çünkü dün yapmadığın iş bugün de duruyor.
 */
@Composable
fun TodayTasksSection(
    state: TodayTasksUiState,
    onToggle: (com.ahmety.uygulama.core.model.Task, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (state.due.isEmpty() && state.completedToday.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Görevler",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (state.due.isNotEmpty()) {
                Text(
                    text = "${state.due.size} açık",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        state.due.forEach { task ->
            TaskRow(task = task, today = state.today, onToggle = { onToggle(task, it) })
        }
        state.completedToday.forEach { task ->
            TaskRow(task = task, today = state.today, onToggle = { onToggle(task, it) })
        }
    }
}

@Composable
internal fun RefreshTodayOnResume(onResume: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) onResume()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
