package it.manzolo.geojournal.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.backup.AutoBackupScheduler
import it.manzolo.geojournal.data.backup.BackupManager
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val userPrefsRepository: UserPreferencesRepository,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val geojExporter: GeoPointExporter,
    private val geoPointRepository: GeoPointRepository
) : ViewModel() {

    sealed class State {
        object Idle : State()
        object Working : State()
        data class ExportOk(val pointCount: Int) : State()
        data class ImportOk(val pointCount: Int, val reminderCount: Int, val visitCount: Int) : State()
        data class ImportPointOk(val title: String) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val autoBackupEnabled: StateFlow<Boolean> = userPrefsRepository.preferences
        .map { it.autoBackupEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val driveBackupUri: StateFlow<String> = userPrefsRepository.preferences
        .map { it.driveBackupUri }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init {
        viewModelScope.launch {
            if (userPrefsRepository.preferences.first().autoBackupEnabled) {
                autoBackupScheduler.schedule()
            }
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            userPrefsRepository.setAutoBackupEnabled(enabled)
            if (enabled) autoBackupScheduler.schedule() else autoBackupScheduler.cancel()
        }
    }

    fun setDriveBackupUri(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            userPrefsRepository.setDriveBackupUri(uri.toString())
        }
    }

    fun clearDriveBackupUri() {
        viewModelScope.launch {
            userPrefsRepository.setDriveBackupUri("")
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _state.value = State.Working
            _state.value = runCatching { State.ExportOk(backupManager.exportToUri(uri)) }
                .getOrElse { State.Error(it.message ?: "Errore durante l'esportazione") }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.value = State.Working
            _state.value = runCatching {
                val r = backupManager.importFromUri(uri)
                State.ImportOk(r.pointCount, r.reminderCount, r.visitCount)
            }.getOrElse { State.Error(it.message ?: "Errore durante l'importazione") }
        }
    }

    fun importGeojPoint(uri: Uri) {
        viewModelScope.launch {
            _state.value = State.Working
            _state.value = runCatching {
                val point = geojExporter.importFromUri(uri)
                geoPointRepository.save(point)
                State.ImportPointOk(point.title)
            }.getOrElse { State.Error(it.message ?: "Errore durante l'importazione del punto") }
        }
    }

    fun resetState() { _state.value = State.Idle }
}
