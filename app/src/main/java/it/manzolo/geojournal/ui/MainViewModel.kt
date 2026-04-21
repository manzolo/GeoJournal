package it.manzolo.geojournal.ui

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.ui.map.MapViewModel
import it.manzolo.geojournal.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val userPrefs: UserPreferencesRepository,
    private val geoPointRepository: GeoPointRepository,
    private val geojExporter: GeoPointExporter,
    private val reminderRepository: ReminderRepository,
    private val kmlRepository: PointKmlRepository
) : ViewModel() {

    // ─── Import .geoj da intent esterno ──────────────────────────────────────
    private val _pendingGeojUri = MutableStateFlow<Uri?>(null)
    val pendingGeojUri: StateFlow<Uri?> = _pendingGeojUri.asStateFlow()

    // Mostrato DOPO l'import se il file contiene un messaggio del mittente
    private val _pendingGeojSenderMessage = MutableStateFlow<String?>(null)
    val pendingGeojSenderMessage: StateFlow<String?> = _pendingGeojSenderMessage.asStateFlow()

    private val _geojImportMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val geojImportMessage: SharedFlow<String> = _geojImportMessage.asSharedFlow()

    fun setPendingGeojUri(uri: Uri) {
        Log.d(TAG, "setPendingGeojUri: uri=$uri")
        _pendingGeojUri.value = uri
    }

    fun clearPendingGeojUri() {
        _pendingGeojUri.value = null
    }

    fun clearPendingGeojSenderMessage() {
        _pendingGeojSenderMessage.value = null
    }

    fun importGeojPoint(uri: Uri) {
        viewModelScope.launch {
            Log.d(TAG, "importGeojPoint: parsing uri=$uri")
            runCatching { geojExporter.importFromUri(uri) }
                .onSuccess { result ->
                    Log.d(TAG, "importGeojPoint: parsed OK title=${result.point.title}")
                    runCatching {
                        geoPointRepository.save(result.point)
                        result.reminders.forEach { reminderRepository.save(it) }
                        result.kmlFiles.forEach { (name, bytes) ->
                            kmlRepository.restoreFromBackup(java.util.UUID.randomUUID().toString(), result.point.id, name, bytes)
                        }
                    }
                        .onSuccess {
                            _geojImportMessage.emit(
                                context.getString(R.string.import_geoj_success, result.point.title)
                            )
                            if (!result.senderMessage.isNullOrBlank()) {
                                _pendingGeojSenderMessage.value = result.senderMessage
                            }
                        }
                        .onFailure {
                            _geojImportMessage.emit(
                                context.getString(
                                    R.string.import_geoj_save_error,
                                    it.message ?: context.getString(R.string.error_unknown)
                                )
                            )
                        }
                }
                .onFailure {
                    Log.e(TAG, "importGeojPoint: FAILED", it)
                    _geojImportMessage.emit(
                        context.getString(
                            R.string.import_geoj_error,
                            it.message ?: context.getString(R.string.error_unknown)
                        )
                    )
                }
        }
    }

    private val _navigateToMap = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val navigateToMap: SharedFlow<Unit> = _navigateToMap.asSharedFlow()

    fun onNotificationOpenPoint(geoPointId: String) {
        viewModelScope.launch {
            val point = geoPointRepository.getById(geoPointId) ?: return@launch
            MapViewModel.FocusRequest.send(point.latitude, point.longitude, point.id)
            _navigateToMap.emit(Unit)
        }
    }

    private val _startDestination = MutableStateFlow<String?>(null)
    val startDestination: StateFlow<String?> = _startDestination.asStateFlow()

    val isDarkTheme: StateFlow<Boolean> = userPrefs.preferences
        .map { it.isDarkTheme }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            val prefs = userPrefs.preferences.first()
            _startDestination.value = when {
                prefs.userId.isNotEmpty() -> Routes.Map.route
                prefs.isGuest            -> Routes.Map.route
                !prefs.hasSeenDataOnboarding -> Routes.Onboarding.createRoute(fromProfile = false)
                else                     -> Routes.Login.route
            }
        }
    }

    companion object {
        private const val TAG = "GeoJournal"
    }
}
