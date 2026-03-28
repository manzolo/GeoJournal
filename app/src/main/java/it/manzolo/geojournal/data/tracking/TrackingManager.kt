package it.manzolo.geojournal.data.tracking

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class TrackingState(
    val isTracking: Boolean = false,
    val geoPointId: String? = null,
    val pointCount: Int = 0
)

@Singleton
class TrackingManager @Inject constructor() {

    private val _state = MutableStateFlow(TrackingState())
    val state: StateFlow<TrackingState> = _state.asStateFlow()

    private val _coordinates = mutableListOf<Pair<Double, Double>>()

    fun startTracking(geoPointId: String) {
        _coordinates.clear()
        _state.update { TrackingState(isTracking = true, geoPointId = geoPointId, pointCount = 0) }
    }

    fun addCoordinate(lat: Double, lon: Double) {
        _coordinates.add(lat to lon)
        _state.update { it.copy(pointCount = _coordinates.size) }
    }

    /** Azzera stato e restituisce (geoPointId, coordinate snapshot). */
    fun stopTrackingAndCollect(): Pair<String?, List<Pair<Double, Double>>> {
        val geoPointId = _state.value.geoPointId
        val snapshot = _coordinates.toList()
        _coordinates.clear()
        _state.update { TrackingState() }
        return geoPointId to snapshot
    }

    fun isTrackingFor(geoPointId: String): Boolean =
        _state.value.isTracking && _state.value.geoPointId == geoPointId
}
