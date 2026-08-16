package com.ahmety.uygulama.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.ahmety.uygulama.feature.calendar.AgendaSection
import com.ahmety.uygulama.feature.calendar.AgendaViewModel
import com.ahmety.uygulama.feature.habits.AddHabitDialog
import com.ahmety.uygulama.feature.habits.HabitsSection
import com.ahmety.uygulama.feature.habits.HabitsViewModel
import com.ahmety.uygulama.feature.tasks.AddTaskDialog
import com.ahmety.uygulama.feature.tasks.TasksViewModel
import com.ahmety.uygulama.feature.tasks.TodayTasksSection
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/**
 * Günün tek ekranı: alışkanlıklar, bugünün görevleri ve ajanda bir arada.
 * Widget’lar da bu ekranın küçültülmüş hâli olacak.
 */
@Composable
fun TodayScreen(
    modifier: Modifier = Modifier,
    habitsViewModel: HabitsViewModel = hiltViewModel(),
    tasksViewModel: TasksViewModel = hiltViewModel(),
    agendaViewModel: AgendaViewModel = hiltViewModel(),
) {
    val habitsState by habitsViewModel.uiState.collectAsStateWithLifecycle()
    val tasksState by tasksViewModel.todayState.collectAsStateWithLifecycle()
    val agendaState by agendaViewModel.uiState.collectAsStateWithLifecycle()

    var fabMenuOpen by remember { mutableStateOf(false) }
    var showAddHabit by remember { mutableStateOf(false) }
    var showAddTask by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                habitsViewModel.refreshToday()
                tasksViewModel.refreshToday()
                agendaViewModel.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = todayHeadline(),
                style = MaterialTheme.typography.headlineSmall,
            )

            HabitsSection(
                state = habitsState,
                onAdvance = habitsViewModel::advance,
                onArchive = { habitsViewModel.setArchived(it.habit.uuid, !it.habit.archived) },
                onDelete = { habitsViewModel.deleteHabit(it.habit.uuid) },
            )

            TodayTasksSection(
                state = tasksState,
                onToggle = tasksViewModel::setCompleted,
            )

            AgendaSection(state = agendaState)
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
        ) {
            FloatingActionButton(onClick = { fabMenuOpen = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Ekle")
            }
            DropdownMenu(expanded = fabMenuOpen, onDismissRequest = { fabMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Görev ekle") },
                    onClick = {
                        fabMenuOpen = false
                        showAddTask = true
                    },
                )
                DropdownMenuItem(
                    text = { Text("Alışkanlık ekle") },
                    onClick = {
                        fabMenuOpen = false
                        showAddHabit = true
                    },
                )
            }
        }
    }

    if (showAddHabit) {
        AddHabitDialog(
            onDismiss = { showAddHabit = false },
            onConfirm = { name, schedule, target ->
                habitsViewModel.createHabit(name, schedule, target)
                showAddHabit = false
            },
        )
    }

    if (showAddTask) {
        AddTaskDialog(
            today = tasksState.today,
            onDismiss = { showAddTask = false },
            onConfirm = { title, due, priority, recurrence ->
                tasksViewModel.addTask(title, due, priority, recurrence)
                showAddTask = false
            },
        )
    }
}

private val monthNames = listOf(
    "Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran",
    "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık",
)

private val dayNames = listOf(
    "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar",
)

private fun todayHeadline(): String {
    val date = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val month = monthNames.getOrElse(date.monthNumber - 1) { "" }
    val day = dayNames.getOrElse(date.dayOfWeek.ordinal) { "" }
    return "${date.dayOfMonth} $month · $day"
}
