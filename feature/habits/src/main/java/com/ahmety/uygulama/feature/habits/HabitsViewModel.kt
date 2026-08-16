package com.ahmety.uygulama.feature.habits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ahmety.uygulama.core.database.repository.HabitRepository
import com.ahmety.uygulama.core.model.Habit
import com.ahmety.uygulama.core.model.HabitCheck
import com.ahmety.uygulama.core.model.HabitSchedule
import com.ahmety.uygulama.core.model.HabitProgress
import com.ahmety.uygulama.core.model.HabitStreaks
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import javax.inject.Inject

/** Seri hesabı için geriye kaç güne bakıldığı. */
private const val STREAK_LOOKBACK_DAYS = 400

/** Satırdaki 7 günlük şeridin bir hücresi. */
data class DayCell(
    val date: Int,
    val isDue: Boolean,
    val isComplete: Boolean,
    val isToday: Boolean,
)

data class HabitUiItem(
    val habit: Habit,
    val todayCount: Int,
    val currentStreak: Int,
    val isDueToday: Boolean,
    /** Son 7 gün, eskiden bugüne. */
    val week: List<DayCell> = emptyList(),
) {
    val isDoneToday: Boolean get() = todayCount >= habit.targetPerDay
}

data class HabitsUiState(
    val today: Int,
    val items: List<HabitUiItem> = emptyList(),
    val loaded: Boolean = false,
    val level: HabitProgress.Level = HabitProgress.levelFor(0),
) {
    val dueToday: List<HabitUiItem> get() = items.filter { it.isDueToday }
    val doneCount: Int get() = dueToday.count { it.isDoneToday }
}

@HiltViewModel
class HabitsViewModel @Inject constructor(
    private val repository: HabitRepository,
) : ViewModel() {

    private val todayFlow = MutableStateFlow(currentEpochDay())

    /**
     * Pencere kurulurken bir kez hesaplanır ve gün dönse bile yeterince geniş kalır;
     * böylece gün değişiminde akışı yeniden kurmak gerekmiyor.
     */
    private val windowStart = currentEpochDay() - STREAK_LOOKBACK_DAYS
    private val windowEnd = currentEpochDay() + 1

    val uiState: StateFlow<HabitsUiState> = combine(
        repository.observeHabits(),
        repository.observeChecksBetween(windowStart, windowEnd),
        repository.observeTotalCompletions(),
        todayFlow,
    ) { habits, checks, totalCompletions, today ->
        buildState(today, habits, checks, totalCompletions)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HabitsUiState(today = currentEpochDay()),
    )

    /** Uygulama gece yarısını geçerek ön plana döndüğünde çağrılır. */
    fun refreshToday() {
        todayFlow.value = currentEpochDay()
    }

    fun advance(item: HabitUiItem) {
        viewModelScope.launch {
            repository.advanceCheck(
                habitUuid = item.habit.uuid,
                date = uiState.value.today,
                targetPerDay = item.habit.targetPerDay,
            )
        }
    }

    /**
     * Şeritteki geçmiş bir güne dokununca o günü tamamlandı/tamamlanmadı yapar.
     * Loop kullanıcılarının en sevdiği özellik: dünü unutmuşsan seri kaybolmasın.
     */
    fun toggleDay(item: HabitUiItem, date: Int) {
        viewModelScope.launch {
            val target = item.habit.targetPerDay.coerceAtLeast(1)
            val done = item.week.firstOrNull { it.date == date }?.isComplete == true
            repository.setCheckCount(item.habit.uuid, date, if (done) 0 else target)
        }
    }

    fun createHabit(
        name: String,
        schedule: HabitSchedule,
        targetPerDay: Int,
    ) {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createHabit(
                name = name,
                schedule = schedule,
                targetPerDay = targetPerDay,
            )
        }
    }

    fun deleteHabit(uuid: String) {
        viewModelScope.launch { repository.deleteHabit(uuid) }
    }

    fun setArchived(uuid: String, archived: Boolean) {
        viewModelScope.launch { repository.setArchived(uuid, archived) }
    }
}

private fun buildState(
    today: Int,
    habits: List<Habit>,
    checks: List<HabitCheck>,
    totalCompletions: Int,
): HabitsUiState {
    val checksByHabit: Map<String, List<HabitCheck>> = checks.groupBy { it.habitUuid }
    val items = habits.map { habit ->
        val habitChecks = checksByHabit[habit.uuid].orEmpty()
        val completed = habitChecks
            .filter { it.count >= habit.targetPerDay }
            .mapTo(mutableSetOf()) { it.date }
        HabitUiItem(
            habit = habit,
            todayCount = habitChecks.firstOrNull { it.date == today }?.count ?: 0,
            currentStreak = HabitStreaks.currentStreak(habit.schedule, completed, today),
            isDueToday = HabitStreaks.isDue(habit.schedule, today),
            week = (6 downTo 0).map { offset ->
                val date = today - offset
                DayCell(
                    date = date,
                    isDue = HabitStreaks.isDue(habit.schedule, date),
                    isComplete = date in completed,
                    isToday = date == today,
                )
            },
        )
    }
    val activeStreakSum = items.sumOf { it.currentStreak }
    val level = HabitProgress.levelFor(HabitProgress.score(totalCompletions, activeStreakSum))
    return HabitsUiState(today = today, items = items, loaded = true, level = level)
}

internal fun currentEpochDay(): Int =
    Clock.System.todayIn(TimeZone.currentSystemDefault()).toEpochDays()
