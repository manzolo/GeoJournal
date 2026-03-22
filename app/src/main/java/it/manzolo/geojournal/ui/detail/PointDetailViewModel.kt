package it.manzolo.geojournal.ui.detail

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.R
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.data.notification.ReminderScheduler
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
    application: Application,
    private val repository: GeoPointRepository,
    private val visitLogRepository: VisitLogRepository,
    private val reminderRepository: ReminderRepository,
    private val reminderScheduler: ReminderScheduler,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    private val pointId: String = savedStateHandle.get<String>("pointId") ?: ""

    private val _uiState = MutableStateFlow(PointDetailUiState())
    val uiState: StateFlow<PointDetailUiState> = _uiState.asStateFlow()

    init {
        loadPoint()
    }

    private fun loadPoint() {
        viewModelScope.launch {
            var wasLoaded = false
            repository.observeAll()
                .map { list -> list.find { it.id == pointId } }
                .collect { point ->
                    if (point != null) {
                        wasLoaded = true
                        _uiState.update { it.copy(point = point, isLoading = false) }
                    } else if (wasLoaded) {
                        // Il punto è stato eliminato: naviga indietro
                        _uiState.update { it.copy(isDeleted = true) }
                    } else {
                        _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.error_point_not_found)) }
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
            // Cancella gli alarm dei promemoria prima di archiviare
            _uiState.value.reminders.forEach { reminderScheduler.cancelReminder(it) }
            repository.archivePoint(pointId)
            // Naviga indietro esplicitamente (il punto è ancora in observeAll ma archiviato)
            _uiState.update { it.copy(isDeleted = true) }
        }
    }

    fun unarchivePoint() {
        viewModelScope.launch {
            _uiState.update { it.copy(showArchiveConfirm = false) }
            repository.unarchivePoint(pointId)
            // Riprogramma i promemoria ancora attivi
            _uiState.value.reminders.forEach { reminderScheduler.scheduleReminder(it) }
            // Rimane sul dettaglio: il punto torna attivo e observeAll() lo aggiorna
        }
    }
}
