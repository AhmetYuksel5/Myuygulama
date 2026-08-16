package com.ahmety.uygulama.feature.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import kotlinx.datetime.todayIn
import javax.inject.Inject

data class AgendaUiState(
    val hasPermission: Boolean = false,
    val events: List<CalendarEvent> = emptyList(),
    val loaded: Boolean = false,
)

@HiltViewModel
class AgendaViewModel @Inject constructor(
    private val repository: DeviceCalendarRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AgendaUiState())
    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    /**
     * Takvim sağlayıcısı akış (Flow) sunmuyor; bu yüzden ekran her öne
     * geldiğinde ve izin verildikten sonra elle yeniliyoruz.
     */
    fun refresh(daysAhead: Int = 1) {
        viewModelScope.launch {
            if (!repository.hasReadPermission()) {
                _uiState.value = AgendaUiState(hasPermission = false, loaded = true)
                return@launch
            }
            val zone = TimeZone.currentSystemDefault()
            val today = Clock.System.todayIn(zone)
            val start = today.atStartOfDayMillis(zone)
            val end = today.plusDays(daysAhead).atStartOfDayMillis(zone)
            _uiState.value = AgendaUiState(
                hasPermission = true,
                events = repository.events(start, end),
                loaded = true,
            )
        }
    }
}

private fun LocalDate.plusDays(days: Int): LocalDate =
    LocalDate.fromEpochDays(toEpochDays() + days)

private fun LocalDate.atStartOfDayMillis(zone: TimeZone): Long =
    atTime(LocalTime(0, 0)).toInstant(zone).toEpochMilliseconds()
