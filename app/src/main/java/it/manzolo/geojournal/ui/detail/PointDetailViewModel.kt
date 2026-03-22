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
    val reminders: List<Reminder> = emptyList(),
    val isDeleted: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val showArchiveConfirm: Boolean = false
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
            var wasLoaded = false
            repository.observeActive()
                .map { list -> list.find { it.id == pointId } }
                .collect { point ->
                    if (point != null) {
                        wasLoaded = true
                        _uiState.update { it.copy(point = point, isLoading = false) }
                    } else if (wasLoaded) {
                        // Il punto era caricato ed è scomparso (eliminato o archiviato): naviga indietro
                        _uiState.update { it.copy(isDeleted = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = "Punto non trovato") }
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

    fun toggleDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = !it.showDeleteConfirm) }

    fun deletePoint() {
        viewModelScope.launch {
            _uiState.update { it.copy(showDeleteConfirm = false) }
            repository.deleteById(pointId)
            // La navigazione back viene gestita da loadPoint() tramite wasLoaded+isDeleted
        }
    }

    fun toggleArchiveConfirm() = _uiState.update { it.copy(showArchiveConfirm = !it.showArchiveConfirm) }

    fun archivePoint() {
        viewModelScope.launch {
            _uiState.update { it.copy(showArchiveConfirm = false) }
            repository.archivePoint(pointId)
            // observeActive() non lo vedrà più → wasLoaded=true → isDeleted=true → naviga indietro
        }
    }
}
