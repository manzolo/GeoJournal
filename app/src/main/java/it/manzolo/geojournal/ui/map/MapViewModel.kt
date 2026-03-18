package it.manzolo.geojournal.ui.map

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import javax.inject.Inject

data class FocusTarget(val lat: Double, val lon: Double, val pointId: String? = null, val zoom: Double = 17.0)

data class MapUiState(
    val points: List<GeoPoint> = emptyList(),
    val selectedPoint: GeoPoint? = null,
    val isLoading: Boolean = true,
    val userLatitude: Double = 45.4654219,
    val userLongitude: Double = 9.1859243,
    val zoomLevel: Double = 13.0,
    val isBottomSheetVisible: Boolean = false,
    val error: String? = null,
    /** Focus one-shot su coordinate + eventuale punto da auto-selezionare */
    val focusTarget: FocusTarget? = null,
    /** True dopo il primo fit automatico su tutti i punti */
    val hasAppliedInitialZoom: Boolean = false,
    /** Messaggio snackbar per conferma parcheggio (@StringRes, risolto nella UI) */
    @StringRes val parkingSnackbarRes: Int? = null,
    /** Mostra dialog quando esiste già un parcheggio salvato */
    val showParkingOptions: Boolean = false,
    /** Posizione corrente in attesa di conferma aggiornamento parcheggio */
    val pendingParkingLat: Double = 0.0,
    val pendingParkingLon: Double = 0.0
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: GeoPointRepository,
    private val exporter: GeoPointExporter
) : ViewModel() {

    private val _shareFileEvent = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val shareFileEvent: SharedFlow<File> = _shareFileEvent.asSharedFlow()

    fun prepareShare(point: GeoPoint) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { exporter.exportPointToCache(point) }
                .onSuccess { _shareFileEvent.emit(it) }
        }
    }

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observePoints()
        seedSampleDataIfEmpty()
        observeFocusRequests()
    }

    /**
     * Collects focus requests emitted via [FocusRequest.send] da qualsiasi schermata.
     * Non dipende dal back stack o dal savedStateHandle: nessuna race condition possibile.
     */
    private fun observeFocusRequests() {
        viewModelScope.launch {
            FocusRequest.events.collect { target ->
                _uiState.update { it.copy(focusTarget = target) }
            }
        }
    }

    /** Canale singleton per richiedere il focus sulla mappa da qualsiasi schermata. */
    object FocusRequest {
        private val _events = MutableSharedFlow<FocusTarget>(
            extraBufferCapacity = 1,
            onBufferOverflow = BufferOverflow.DROP_OLDEST
        )
        val events: SharedFlow<FocusTarget> = _events.asSharedFlow()

        fun send(lat: Double, lon: Double, pointId: String? = null) {
            _events.tryEmit(FocusTarget(lat, lon, pointId))
        }
    }

    private fun observePoints() {
        viewModelScope.launch {
            repository.observeAll()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { points ->
                    _uiState.update { it.copy(points = points, isLoading = false) }
                }
        }
    }

    private fun seedSampleDataIfEmpty() {
        viewModelScope.launch {
            if (repository.count() == 0) {
                samplePoints.forEach { repository.save(it) }
            }
        }
    }

    fun onPointSelected(point: GeoPoint) {
        _uiState.update { it.copy(selectedPoint = point, isBottomSheetVisible = true) }
    }

    fun onBottomSheetDismiss() {
        _uiState.update { it.copy(isBottomSheetVisible = false, selectedPoint = null) }
    }

    fun onMapMoved(lat: Double, lon: Double, zoom: Double) {
        _uiState.update { it.copy(userLatitude = lat, userLongitude = lon, zoomLevel = zoom) }
    }

    fun clearFocusTarget() = _uiState.update { it.copy(focusTarget = null) }

    fun markInitialFitDone() = _uiState.update { it.copy(hasAppliedInitialZoom = true) }

    fun clearParkingSnackbar() = _uiState.update { it.copy(parkingSnackbarRes = null) }

    // ID del punto parcheggio esistente, cached per evitare doppia ricerca
    private var pendingParkingPointId: String? = null

    fun saveParkingPoint(lat: Double, lon: Double, pointTitle: String) {
        val existing = _uiState.value.points.find { PARKING_TAG in it.tags }
        if (existing != null) {
            pendingParkingPointId = existing.id
            _uiState.update { it.copy(showParkingOptions = true, pendingParkingLat = lat, pendingParkingLon = lon) }
        } else {
            viewModelScope.launch {
                repository.save(
                    GeoPoint(
                        title = pointTitle,
                        emoji = "🚗",
                        tags = listOf(PARKING_TAG),
                        latitude = lat,
                        longitude = lon
                    )
                )
                _uiState.update { it.copy(parkingSnackbarRes = R.string.map_parking_saved) }
            }
        }
    }

    fun confirmUpdateParking() {
        val id = pendingParkingPointId ?: return
        val state = _uiState.value
        val existing = state.points.find { it.id == id } ?: return
        viewModelScope.launch {
            repository.save(existing.copy(latitude = state.pendingParkingLat, longitude = state.pendingParkingLon, updatedAt = Date()))
            _uiState.update { it.copy(showParkingOptions = false, parkingSnackbarRes = R.string.map_parking_updated) }
        }
    }

    fun navigateToParking() {
        val id = pendingParkingPointId ?: return
        val existing = _uiState.value.points.find { it.id == id } ?: return
        _uiState.update { it.copy(showParkingOptions = false, focusTarget = FocusTarget(existing.latitude, existing.longitude, pointId = existing.id, zoom = 19.0)) }
    }

    fun dismissParkingOptions() {
        pendingParkingPointId = null
        _uiState.update { it.copy(showParkingOptions = false) }
    }

    companion object {
        const val PARKING_TAG = "_parking"
        private val now = Date()
        private val samplePoints = listOf(
            GeoPoint(
                id = "sample_1",
                title = "Duomo di Milano",
                description = "La magnifica cattedrale gotica di Milano. Un luogo che non stanca mai.",
                latitude = 45.4641, longitude = 9.1919,
                emoji = "⛪", tags = listOf("chiesa", "storia", "arte"),
                createdAt = now, updatedAt = now
            ),
            GeoPoint(
                id = "sample_2",
                title = "Navigli",
                description = "I romantici canali di Milano, perfetti per un aperitivo al tramonto.",
                latitude = 45.4488, longitude = 9.1701,
                emoji = "🌊", tags = listOf("aperitivo", "sera", "romantico"),
                createdAt = now, updatedAt = now
            ),
            GeoPoint(
                id = "sample_3",
                title = "Parco Sempione",
                description = "Un'oasi verde nel cuore della città, ideale per picnic e relax.",
                latitude = 45.4726, longitude = 9.1766,
                emoji = "🌳", tags = listOf("natura", "relax", "picnic"),
                createdAt = now, updatedAt = now
            ),
            GeoPoint(
                id = "sample_4",
                title = "Brera",
                description = "Il quartiere degli artisti e della movida milanese.",
                latitude = 45.4720, longitude = 9.1870,
                emoji = "🎨", tags = listOf("arte", "cultura", "aperitivo"),
                createdAt = now, updatedAt = now
            )
        )
    }
}
