package it.manzolo.geojournal.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PointDetailUiState(
    val point: GeoPoint? = null,
    val isLoading: Boolean = true,
    val error: String? = null
)

@HiltViewModel
class PointDetailViewModel @Inject constructor(
    private val repository: GeoPointRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val pointId: String = savedStateHandle.get<String>("pointId") ?: ""

    private val _uiState = MutableStateFlow(PointDetailUiState())
    val uiState: StateFlow<PointDetailUiState> = _uiState.asStateFlow()

    init {
        loadPoint()
    }

    private fun loadPoint() {
        viewModelScope.launch {
            val point = repository.getById(pointId)
            _uiState.update {
                if (point != null) it.copy(point = point, isLoading = false)
                else it.copy(isLoading = false, error = "Punto non trovato")
            }
        }
    }
}
