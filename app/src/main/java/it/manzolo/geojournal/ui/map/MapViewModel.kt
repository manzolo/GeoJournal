package it.manzolo.geojournal.ui.map

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.backup.ShareOptions
import it.manzolo.geojournal.data.kml.KmlParser
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.PointKml
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import javax.inject.Inject

/** KML visibile nella sessione corrente, con titolo del punto associato */
data class KmlSessionItem(
    val kml: PointKml,
    val pointTitle: String,
    val isActive: Boolean
)

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
    /** Punto in attesa di condivisione: non-null = mostra dialog messaggio */
    val pendingSharePoint: GeoPoint? = null,
    /** Messaggio snackbar per conferma parcheggio (@StringRes, risolto nella UI) */
    @StringRes val parkingSnackbarRes: Int? = null,
    /** Mostra dialog quando esiste già un parcheggio salvato */
    val showParkingOptions: Boolean = false,
    /** Posizione corrente in attesa di conferma aggiornamento parcheggio */
    val pendingParkingLat: Double = 0.0,
    val pendingParkingLon: Double = 0.0,
    /** KML caricati per i punti nel viewport corrente, con stato on/off di sessione */
    val kmlItems: List<KmlSessionItem> = emptyList(),
    /** True = mostra bottom sheet con lista KML */
    val showKmlPanel: Boolean = false,
    /** ID dei punti con header espanso nel pannello KML */
    val expandedKmlPointIds: Set<String> = emptySet()
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: GeoPointRepository,
    private val exporter: GeoPointExporter,
    private val userPrefs: UserPreferencesRepository,
    private val kmlRepository: PointKmlRepository
) : ViewModel() {

    private val _shareFileEvent = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val shareFileEvent: SharedFlow<File> = _shareFileEvent.asSharedFlow()

    fun onShareRequested(point: GeoPoint) {
        _uiState.update { it.copy(pendingSharePoint = point) }
    }

    fun onShareConfirmed(message: String?, options: ShareOptions = ShareOptions()) {
        val point = _uiState.value.pendingSharePoint ?: return
        _uiState.update { it.copy(pendingSharePoint = null) }
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { exporter.exportPointToCache(point, message, options) }
                .onSuccess { _shareFileEvent.emit(it) }
        }
    }

    fun onShareDismissed() {
        _uiState.update { it.copy(pendingSharePoint = null) }
    }

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    private var savePositionJob: Job? = null

    init {
        restoreMapPosition()
        observePoints()
        seedSampleDataIfEmpty()
        observeFocusRequests()
    }

    private fun restoreMapPosition() {
        viewModelScope.launch {
            val prefs = userPrefs.preferences.first()
            if (prefs.mapLat != 0.0 || prefs.mapLon != 0.0) {
                _uiState.update {
                    it.copy(
                        userLatitude = prefs.mapLat,
                        userLongitude = prefs.mapLon,
                        zoomLevel = prefs.mapZoom,
                        hasAppliedInitialZoom = true  // salta il fit automatico sui punti
                    )
                }
            }
        }
    }

    // ID del punto da selezionare (bottom sheet) quando arriva da notifica o da lista,
    // mantenuto finché i punti non sono caricati da Room
    private var pendingSelectPointId: String? = null

    /**
     * Collects focus requests emitted via [FocusRequest.send] da qualsiasi schermata.
     * Salva il pointId in pendingSelectPointId: se i punti non sono ancora caricati
     * quando il focus arriva, la selezione viene ritentata in observePoints().
     */
    private fun observeFocusRequests() {
        viewModelScope.launch {
            FocusRequest.pending.collect { target ->
                if (target != null) {
                    pendingSelectPointId = target.pointId
                    _uiState.update { it.copy(focusTarget = target) }
                    FocusRequest.consume()
                    trySelectPendingPoint()
                }
            }
        }
    }

    private fun trySelectPendingPoint() {
        val id = pendingSelectPointId ?: return
        val point = _uiState.value.points.find { it.id == id } ?: return
        pendingSelectPointId = null
        _uiState.update { it.copy(selectedPoint = point, isBottomSheetVisible = true) }
    }

    /** Canale singleton per richiedere il focus sulla mappa da qualsiasi schermata.
     *  Usa StateFlow per garantire la consegna anche se MapViewModel
     *  viene creato dopo l'emissione (es. cold start da notifica). */
    object FocusRequest {
        private val _pending = MutableStateFlow<FocusTarget?>(null)
        val pending: StateFlow<FocusTarget?> = _pending.asStateFlow()

        fun send(lat: Double, lon: Double, pointId: String? = null) {
            _pending.value = FocusTarget(lat, lon, pointId)
        }

        fun consume() { _pending.value = null }
    }

    private fun observePoints() {
        viewModelScope.launch {
            repository.observeActive()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { points ->
                    _uiState.update { it.copy(points = points, isLoading = false) }
                    trySelectPendingPoint()
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
        savePositionJob?.cancel()
        savePositionJob = viewModelScope.launch {
            delay(1000)
            userPrefs.setMapPosition(lat, lon, zoom)
        }
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

    // ─── KML session management ─────────────────────────────────────────────

    fun toggleKmlPanel() {
        val showing = !_uiState.value.showKmlPanel
        if (showing) loadKmlsForCurrentPoints()
        _uiState.update { it.copy(showKmlPanel = showing) }
    }

    fun dismissKmlPanel() = _uiState.update { it.copy(showKmlPanel = false) }

    /** Calcola l'half-span in gradi lat/lon per il viewport corrente */
    private fun viewportHalfSpan(): Double =
        3.0 * 360.0 / Math.pow(2.0, _uiState.value.zoomLevel)

    /** Filtra i punti attualmente visibili nel viewport della mappa */
    private fun pointsInViewport(): List<it.manzolo.geojournal.domain.model.GeoPoint> {
        val state = _uiState.value
        val half = viewportHalfSpan()
        return state.points.filter { p ->
            p.latitude in (state.userLatitude - half)..(state.userLatitude + half) &&
            p.longitude in (state.userLongitude - half * 1.5)..(state.userLongitude + half * 1.5)
        }
    }

    private fun loadKmlsForCurrentPoints() {
        val points = pointsInViewport()
        viewModelScope.launch(Dispatchers.IO) {
            val currentActiveIds = _uiState.value.kmlItems
                .filter { it.isActive }.map { it.kml.id }.toSet()
            val items = points.flatMap { point ->
                kmlRepository.getByGeoPointId(point.id).map { kml ->
                    KmlSessionItem(
                        kml = kml,
                        pointTitle = point.title,
                        isActive = kml.id in currentActiveIds
                    )
                }
            }
            // Espandi di default tutti i punti che hanno KML
            val expandedIds = items.map { it.kml.geoPointId }.toSet()
            _uiState.update { it.copy(kmlItems = items, expandedKmlPointIds = expandedIds) }
        }
    }

    fun toggleKmlPointGroup(geoPointId: String) {
        _uiState.update { state ->
            val expanded = state.expandedKmlPointIds.toMutableSet()
            if (geoPointId in expanded) expanded.remove(geoPointId) else expanded.add(geoPointId)
            state.copy(expandedKmlPointIds = expanded)
        }
    }

    fun toggleKml(kmlId: String) {
        _uiState.update { state ->
            state.copy(
                kmlItems = state.kmlItems.map { item ->
                    if (item.kml.id == kmlId) item.copy(isActive = !item.isActive) else item
                }
            )
        }
    }

    /** Parsing del KML: eseguito IO, restituisce le geometrie per l'overlay manager */
    suspend fun parseKml(kmlId: String) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        val item = _uiState.value.kmlItems.find { it.kml.id == kmlId } ?: return@withContext emptyList()
        KmlParser.parse(java.io.File(item.kml.filePath))
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
