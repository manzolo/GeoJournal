package it.manzolo.geojournal.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.domain.repository.VisitLogRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class CalendarUiState(
    val currentMonth: YearMonth = YearMonth.now(),
    val reminderDays: Set<LocalDate> = emptySet(),
    val visitDays: Set<LocalDate> = emptySet(),
    val selectedDay: LocalDate? = null,
    val remindersForDay: List<Reminder> = emptyList(),
    val visitsForDay: List<VisitLogEntry> = emptyList(),
    // map geoPointId -> title for display
    val pointTitles: Map<String, String> = emptyMap()
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val visitLogRepository: VisitLogRepository,
    geoPointRepository: GeoPointRepository
) : ViewModel() {

    private val _currentMonth = MutableStateFlow(YearMonth.now())
    private val _selectedDay = MutableStateFlow<LocalDate?>(null)

    // Tutti i reminder attivi — il filtering per mese avviene nel combine
    private val remindersFlow = reminderRepository.observeAllActive()

    private val visitsFlow = _currentMonth.flatMapLatest { ym ->
        visitLogRepository.observeForDateRange(ym.startEpoch(), ym.endEpoch())
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        _currentMonth,
        remindersFlow,
        visitsFlow,
        _selectedDay,
        geoPointRepository.observeAll()
    ) { month, reminders, visits, selectedDay, allPoints ->
        val pointTitles = allPoints.associate { it.id to it.title }

        // Calcola le date "effettive" di ogni reminder nel mese corrente
        fun Reminder.occurrenceDaysInMonth(month: YearMonth): List<LocalDate> {
            val startOrig = LocalDate.ofEpochDay(startDate / 86400000)
            val endOrig = endDate?.let { LocalDate.ofEpochDay(it / 86400000) }

            // Per i ricorrenti annuali: sposta anno al mese visualizzato
            val start = if (type == it.manzolo.geojournal.domain.model.ReminderType.ANNUAL_RECURRING)
                startOrig.withYear(month.year)
            else startOrig

            val end = if (type == it.manzolo.geojournal.domain.model.ReminderType.ANNUAL_RECURRING)
                endOrig?.withYear(month.year) ?: start
            else endOrig ?: start

            // Tieni solo le date che cadono nel mese corrente
            return generateSequence(start) { d -> if (d <= end) d.plusDays(1) else null }
                .filter { it.year == month.year && it.monthValue == month.monthValue }
                .toList()
        }

        fun Reminder.matchesDay(day: LocalDate): Boolean {
            val startOrig = LocalDate.ofEpochDay(startDate / 86400000)
            val endOrig = endDate?.let { LocalDate.ofEpochDay(it / 86400000) }
            val start = if (type == it.manzolo.geojournal.domain.model.ReminderType.ANNUAL_RECURRING)
                startOrig.withYear(day.year) else startOrig
            val end = if (type == it.manzolo.geojournal.domain.model.ReminderType.ANNUAL_RECURRING)
                endOrig?.withYear(day.year) ?: start else endOrig ?: start
            return !day.isBefore(start) && !day.isAfter(end)
        }

        val reminderDays = reminders
            .flatMap { it.occurrenceDaysInMonth(month) }
            .toSet()

        val visitDays = visits.map { v ->
            LocalDate.ofEpochDay(v.visitedAt / 86400000)
        }.toSet()

        val remindersForDay = selectedDay?.let { day ->
            reminders.filter { it.matchesDay(day) }
        } ?: emptyList()

        val visitsForDay = selectedDay?.let { day ->
            visits.filter { v ->
                LocalDate.ofEpochDay(v.visitedAt / 86400000) == day
            }
        } ?: emptyList()

        CalendarUiState(
            currentMonth = month,
            reminderDays = reminderDays,
            visitDays = visitDays,
            selectedDay = selectedDay,
            remindersForDay = remindersForDay,
            visitsForDay = visitsForDay,
            pointTitles = pointTitles
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CalendarUiState()
    )

    fun selectDay(day: LocalDate?) = _selectedDay.update { day }
    fun nextMonth() = _currentMonth.update { it.plusMonths(1) }
    fun previousMonth() = _currentMonth.update { it.minusMonths(1) }

    private fun YearMonth.startEpoch(): Long =
        atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun YearMonth.endEpoch(): Long =
        atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
