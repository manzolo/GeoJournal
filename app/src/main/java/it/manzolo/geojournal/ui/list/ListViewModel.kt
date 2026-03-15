package it.manzolo.geojournal.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject

enum class SortOrder(val label: String) {
    NEWEST("Più recenti"),
    OLDEST("Più vecchi"),
    TITLE_AZ("Titolo A-Z")
}

data class ListUiState(
    val points: List<GeoPoint> = emptyList(),
    val allTags: List<String> = emptyList(),
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val isLoading: Boolean = true
)

@HiltViewModel
class ListViewModel @Inject constructor(
    repository: GeoPointRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)

    val uiState: StateFlow<ListUiState> = combine(
        repository.observeAll(),
        _query,
        _selectedTags,
        _sortOrder
    ) { all, query, selectedTags, sortOrder ->
        val allTags = all.flatMap { it.tags }.distinct().sorted()

        val filtered = all
            .filter { point ->
                val matchesQuery = query.isBlank() ||
                    point.title.contains(query, ignoreCase = true) ||
                    point.description.contains(query, ignoreCase = true) ||
                    point.tags.any { it.contains(query, ignoreCase = true) }
                val matchesTags = selectedTags.isEmpty() ||
                    point.tags.any { it in selectedTags }
                matchesQuery && matchesTags
            }
            .let { list ->
                when (sortOrder) {
                    SortOrder.NEWEST -> list.sortedByDescending { it.createdAt }
                    SortOrder.OLDEST -> list.sortedBy { it.createdAt }
                    SortOrder.TITLE_AZ -> list.sortedBy { it.title.lowercase() }
                }
            }

        ListUiState(
            points = filtered,
            allTags = allTags,
            query = query,
            selectedTags = selectedTags,
            sortOrder = sortOrder,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ListUiState()
    )

    fun updateQuery(q: String) = _query.update { q }
    fun toggleTag(tag: String) = _selectedTags.update { if (tag in it) it - tag else it + tag }
    fun setSortOrder(order: SortOrder) = _sortOrder.update { order }
}
