package it.manzolo.geojournal.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
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


    private val _geojImportMessage = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val geojImportMessage: SharedFlow<String> = _geojImportMessage.asSharedFlow()

    fun setPendingGeojUri(uri: Uri) { _pendingGeojUri.value = uri }
    fun clearPendingGeojUri() { _pendingGeojUri.value = null }

    fun importGeojPoint(uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val point = geojExporter.importFromUri(uri)
                geoPointRepository.save(point)
                "✓ Punto \"${point.title}\" importato"
            }.onSuccess { _geojImportMessage.emit(it) }
             .onFailure { _geojImportMessage.emit("Errore importazione: ${it.message}") }
        }
    }

    private val _navigateToPoint = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToPoint: SharedFlow<String> = _navigateToPoint.asSharedFlow()

    fun onNotificationOpenPoint(geoPointId: String) {
        viewModelScope.launch { _navigateToPoint.emit(geoPointId) }
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
                prefs.isGuest -> Routes.Map.route
                else -> Routes.Login.route
            }
        }
    }
}
