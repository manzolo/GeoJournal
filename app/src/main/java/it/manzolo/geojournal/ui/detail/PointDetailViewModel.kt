package it.manzolo.geojournal.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.domain.repository.VisitLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PointDetailUiState(
    val point: GeoPoint? = null,
    val isLoading: Boolean = true,
    val error: String? = null,
    val visitLogs: List<VisitLogEntry> = emptyList(),
    val reminders: List<Reminder> = emptyList()
)

@HiltViewModel
class PointDetailViewModel @Inject constructor(
    private val repository: GeoPointRepository,
    private val visitLogRepository: VisitLogRepository,
    private val reminderRepository: ReminderRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val pointId: String = savedStateHandle.get<String>("pointId") ?: ""

    private val _uiState = MutableStateFlow(PointDetailUiState())
    val uiState: StateFlow<PointDetailUiState> = _uiState.asStateFlow()

    init {
        loadPoint()
    }

    private fun loadPoint() {
        viewModelScope.launch {
            repository.observeAll()
                .map { list -> list.find { it.id == pointId } }
                .collect { point ->
                    _uiState.update {
                        if (point != null) it.copy(point = point, isLoading = false)
                        else it.copy(isLoading = false, error = "Punto non trovato")
                    }
                }
        }
        viewModelScope.launch {
            visitLogRepository.observeByGeoPointId(pointId).collect { logs ->
                _uiState.update { it.copy(visitLogs = logs) }
            }
        }
        viewModelScope.launch {
            reminderRepository.observeByGeoPointId(pointId).collect { list ->
                _uiState.update { it.copy(reminders = list) }
            }
        }
    }

    fun logVisitToday(note: String = "") {
        viewModelScope.launch { visitLogRepository.logVisit(pointId, note) }
    }

    fun deleteVisitLog(entry: VisitLogEntry) {
        viewModelScope.launch { visitLogRepository.delete(entry) }
    }

    fun deleteReminder(reminder: Reminder) {
        viewModelScope.launch { reminderRepository.delete(reminder) }
    }
}
