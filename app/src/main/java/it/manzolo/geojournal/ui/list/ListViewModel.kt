package it.manzolo.geojournal.ui.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SortOrder(@StringRes val labelRes: Int) {
    NEWEST(R.string.sort_newest),
    OLDEST(R.string.sort_oldest),
    TITLE_AZ(R.string.sort_title_az)
}

data class ListUiState(
    val points: List<GeoPoint> = emptyList(),
    val allTags: List<String> = emptyList(),
    val query: String = "",
    val selectedTags: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.NEWEST,
    val isLoading: Boolean = true,
    val showArchived: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListViewModel @Inject constructor(
    private val repository: GeoPointRepository,
    private val exporter: GeoPointExporter
) : ViewModel() {

    private val _shareFileEvent = MutableSharedFlow<File>(extraBufferCapacity = 1)
    val shareFileEvent: SharedFlow<File> = _shareFileEvent.asSharedFlow()

    private val _pendingSharePoint = MutableStateFlow<GeoPoint?>(null)
    val pendingSharePoint: StateFlow<GeoPoint?> = _pendingSharePoint.asStateFlow()

    fun onShareRequested(point: GeoPoint) {
        _pendingSharePoint.value = point
    }

    fun onShareConfirmed(message: String?) {
        val point = _pendingSharePoint.value ?: return
        _pendingSharePoint.value = null
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { exporter.exportPointToCache(point, message) }
                .onSuccess { _shareFileEvent.emit(it) }
        }
    }

    fun onShareDismissed() {
        _pendingSharePoint.value = null
    }

    private val _query = MutableStateFlow("")
    private val _selectedTags = MutableStateFlow<Set<String>>(emptySet())
    private val _sortOrder = MutableStateFlow(SortOrder.NEWEST)
    private val _showArchived = MutableStateFlow(false)

    val uiState: StateFlow<ListUiState> = combine(
        _showArchived.flatMapLatest { archived ->
            if (archived) repository.observeArchived() else repository.observeActive()
        },
        _query,
        _selectedTags,
        _sortOrder,
        _showArchived
    ) { all, query, selectedTags, sortOrder, showArchived ->
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
            isLoading = false,
            showArchived = showArchived
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ListUiState()
    )

    fun updateQuery(q: String) = _query.update { q }
    fun toggleTag(tag: String) = _selectedTags.update { if (tag in it) it - tag else it + tag }
    fun setSortOrder(order: SortOrder) = _sortOrder.update { order }
    fun toggleArchiveView() = _showArchived.update { !it }
    fun deletePoint(point: GeoPoint) = viewModelScope.launch { repository.delete(point) }
    fun archivePoint(point: GeoPoint) = viewModelScope.launch { repository.archivePoint(point.id) }
    fun unarchivePoint(point: GeoPoint) = viewModelScope.launch { repository.unarchivePoint(point.id) }
    fun deleteTag(tag: String) = viewModelScope.launch {
        _selectedTags.update { it - tag }
        repository.removeTagFromAllPoints(tag)
    }
}
