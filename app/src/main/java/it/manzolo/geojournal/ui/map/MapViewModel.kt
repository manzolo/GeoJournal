package it.manzolo.geojournal.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Date
import javax.inject.Inject

data class MapUiState(
    val points: List<GeoPoint> = emptyList(),
    val selectedPoint: GeoPoint? = null,
    val isLoading: Boolean = true,
    val userLatitude: Double = 45.4654219,
    val userLongitude: Double = 9.1859243,
    val zoomLevel: Double = 13.0,
    val isBottomSheetVisible: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: GeoPointRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        observePoints()
        seedSampleDataIfEmpty()
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

    companion object {
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
