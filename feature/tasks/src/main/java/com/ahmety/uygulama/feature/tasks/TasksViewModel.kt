package com.ahmety.uygulama.feature.tasks

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.importer.ImportResult
import com.ahmety.uygulama.core.database.importer.TodoImportParser
import com.ahmety.uygulama.core.database.prefs.TaskViewPrefs
import com.ahmety.uygulama.core.database.repository.TaskRepository
import com.ahmety.uygulama.core.model.RecurrenceRule
import com.ahmety.uygulama.core.model.Task
import com.ahmety.uygulama.core.model.TaskList
import com.ahmety.uygulama.core.model.TaskPriority
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class TasksUiState(
    val lists: List<TaskList> = emptyList(),
    val selectedListUuid: String? = null,
    val tasks: List<Task> = emptyList(),
    val today: Int = 0,
    val hideCompleted: Boolean = false,
) {
    val openTasks: List<Task> get() = tasks.filterNot { it.isCompleted }
    val doneTasks: List<Task> get() = tasks.filter { it.isCompleted }
    val selectedList: TaskList? get() = lists.firstOrNull { it.uuid == selectedListUuid }
}

data class TodayTasksUiState(
    val today: Int = 0,
    val due: List<Task> = emptyList(),
    val completedToday: List<Task> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TasksViewModel @Inject constructor(
    private val repository: TaskRepository,
    @ApplicationContext context: Context,
) : ViewModel() {

    private val prefs = TaskViewPrefs(context)
    private val selectedListUuid = MutableStateFlow<String?>(null)
    private val todayFlow = MutableStateFlow(currentEpochDay())
    private val hideCompletedFlow = MutableStateFlow(prefs.hideCompleted)

    /** Son içe aktarma özeti; kullanıcıya "kaç görev geldi" demek için. */
    private val _importMessage = MutableStateFlow<String?>(null)
    val importMessage: StateFlow<String?> = _importMessage

    init {
        viewModelScope.launch {
            val defaultList = repository.ensureDefaultList()
            if (selectedListUuid.value == null) selectedListUuid.value = defaultList
        }
    }

    val uiState: StateFlow<TasksUiState> = combine(
        repository.observeLists(),
        selectedListUuid,
        todayFlow,
        hideCompletedFlow,
    ) { lists, selected, today, hideCompleted ->
        Quad(lists, selected ?: lists.firstOrNull()?.uuid, today, hideCompleted)
    }.flatMapLatest { (lists, selected, today, hideCompleted) ->
        val tasksFlow = if (selected == null) flowOf(emptyList()) else repository.observeTasks(selected)
        tasksFlow.map { tasks ->
            TasksUiState(
                lists = lists,
                selectedListUuid = selected,
                tasks = tasks,
                today = today,
                hideCompleted = hideCompleted,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    fun setHideCompleted(hide: Boolean) {
        prefs.hideCompleted = hide
        hideCompletedFlow.value = hide
    }

    val todayState: StateFlow<TodayTasksUiState> = todayFlow.flatMapLatest { today ->
        combine(
            repository.observeDueThrough(today),
            repository.observeCompletedOn(today),
        ) { due, completed ->
            TodayTasksUiState(today = today, due = due, completedToday = completed)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayTasksUiState())

    fun refreshToday() {
        todayFlow.value = currentEpochDay()
    }

    fun selectList(uuid: String) {
        selectedListUuid.value = uuid
    }

    fun createList(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { selectedListUuid.value = repository.createList(name) }
    }

    fun addTask(
        title: String,
        dueDate: Int? = null,
        priority: TaskPriority = TaskPriority.NONE,
        recurrence: RecurrenceRule? = null,
        notes: String = "",
    ) {
        if (title.isBlank()) return
        val listUuid = uiState.value.selectedListUuid ?: return
        viewModelScope.launch {
            repository.createTask(
                listUuid = listUuid,
                title = title,
                notes = notes,
                dueDate = dueDate,
                priority = priority,
                recurrence = recurrence,
            )
        }
    }

    fun setCompleted(task: Task, completed: Boolean) {
        viewModelScope.launch {
            repository.setCompleted(task.uuid, completed, todayFlow.value)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch { repository.deleteTask(task.uuid) }
    }

    /** Yapıştırılan metni ayrıştırıp önizleme döndürür; henüz yazmaz. */
    fun preview(input: String): ImportResult = TodoImportParser.parse(input)

    fun import(result: ImportResult) {
        viewModelScope.launch {
            val summary = repository.importTasks(result)
            _importMessage.value = buildString {
                append("${summary.imported} görev içe aktarıldı")
                if (summary.skipped > 0) {
                    // Sayfa sayfa aktarımda üst üste binen görevler burada görünür.
                    append(", ${summary.skipped} tanesi zaten vardı")
                }
                append(".")
            }
        }
    }

    fun clearImportMessage() {
        _importMessage.value = null
    }
}

internal fun currentEpochDay(): Int =
    Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()

private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)
