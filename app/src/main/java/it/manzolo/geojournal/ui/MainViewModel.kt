package it.manzolo.geojournal.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
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
    private val userPrefs: UserPreferencesRepository,
    private val geoPointRepository: GeoPointRepository,
    private val geojExporter: GeoPointExporter
) : ViewModel() {

    // ─── Import .geoj da intent esterno ──────────────────────────────────────
    private val _pendingGeojUri = MutableStateFlow<Uri?>(null)
    val pendingGeojUri: StateFlow<Uri?> = _pendingGeojUri.asStateFlow()

    private val _pendingGeojSenderMessage = MutableStateFlow<String?>(null)
    val pendingGeojSenderMessage: StateFlow<String?> = _pendingGeojSenderMessage.asStateFlow()

    private val _geojImportMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val geojImportMessage: SharedFlow<String> = _geojImportMessage.asSharedFlow()

    fun setPendingGeojUri(uri: Uri) {
        _pendingGeojUri.value = uri
        viewModelScope.launch {
            _pendingGeojSenderMessage.value = geojExporter.peekSenderMessage(uri)
        }
    }

    fun clearPendingGeojUri() {
        _pendingGeojUri.value = null
        _pendingGeojSenderMessage.value = null
    }

    fun importGeojPoint(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val result = geojExporter.importFromUri(uri)
                geoPointRepository.save(result.point)
                "✓ Punto \"${result.point.title}\" importato"
            }.onSuccess { _geojImportMessage.emit(it) }
             .onFailure { _geojImportMessage.emit("Errore importazione: ${it.message}") }
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
}
