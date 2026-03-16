package it.manzolo.geojournal.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.backup.BackupManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager
) : ViewModel() {

    sealed class State {
        object Idle : State()
        object Working : State()
        data class ExportOk(val pointCount: Int) : State()
        data class ImportOk(val pointCount: Int, val reminderCount: Int, val visitCount: Int) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

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

    fun resetState() { _state.value = State.Idle }
}
