package it.manzolo.geojournal.ui.addedit

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Date
import java.util.UUID
import javax.inject.Inject

data class AddEditUiState(
    val title: String = "",
    val description: String = "",
    val emoji: String = "📍",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoUris: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null,
    val showEmojiPicker: Boolean = false,
    val showDeleteConfirm: Boolean = false
)

@HiltViewModel
class AddEditViewModel @Inject constructor(
    application: Application,
    private val repository: GeoPointRepository,
    private val userPrefs: UserPreferencesRepository,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    private val pointId: String = savedStateHandle.get<String>("pointId") ?: "new"
    val isEditMode: Boolean = pointId != "new"

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var existingId: String = UUID.randomUUID().toString()
    private var existingCreatedAt: Date = Date()

    init {
        if (isEditMode) loadPoint() else _uiState.update { it.copy(isLoading = false) }
    }

    private fun loadPoint() {
        viewModelScope.launch {
            val point = repository.getById(pointId)
            if (point != null) {
                existingId = point.id
                existingCreatedAt = point.createdAt
                _uiState.update {
                    it.copy(
                        title = point.title,
                        description = point.description,
                        emoji = point.emoji,
                        tags = point.tags,
                        latitude = point.latitude,
                        longitude = point.longitude,
                        photoUris = point.photoUrls,
                        isLoading = false
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = "Punto non trovato") }
            }
        }
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun updateTagInput(value: String) = _uiState.update { it.copy(tagInput = value) }
    fun selectEmoji(emoji: String) = _uiState.update { it.copy(emoji = emoji, showEmojiPicker = false) }
    fun toggleEmojiPicker() = _uiState.update { it.copy(showEmojiPicker = !it.showEmojiPicker) }
    fun updateLocation(lat: Double, lon: Double) = _uiState.update { it.copy(latitude = lat, longitude = lon) }

    fun addTag() {
        val tag = _uiState.value.tagInput.trim().lowercase()
        if (tag.isNotBlank() && !_uiState.value.tags.contains(tag)) {
            _uiState.update { it.copy(tags = it.tags + tag, tagInput = "") }
        }
    }

    fun removeTag(tag: String) = _uiState.update { it.copy(tags = it.tags - tag) }
    fun toggleDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = !it.showDeleteConfirm) }

    fun addPhotoUri(uri: String) = _uiState.update { it.copy(photoUris = it.photoUris + uri) }
    fun removePhotoUri(uri: String) = _uiState.update { it.copy(photoUris = it.photoUris - uri) }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "Il titolo è obbligatorio") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val prefs = userPrefs.preferences.first()
            val resolvedId = if (isEditMode) existingId else UUID.randomUUID().toString()
            val resolvedPhotos = resolvePhotos(state.photoUris, resolvedId)
            val point = GeoPoint(
                id = resolvedId,
                title = state.title.trim(),
                description = state.description.trim(),
                emoji = state.emoji,
                tags = state.tags,
                latitude = state.latitude,
                longitude = state.longitude,
                photoUrls = resolvedPhotos,
                createdAt = if (isEditMode) existingCreatedAt else Date(),
                updatedAt = Date(),
                ownerId = prefs.userId
            )
            repository.save(point)
            _uiState.update { it.copy(isLoading = false, isSaved = true) }
        }
    }

    fun delete() {
        if (!isEditMode) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, showDeleteConfirm = false) }
            repository.deleteById(existingId)
            _uiState.update { it.copy(isLoading = false, isDeleted = true) }
        }
    }

    fun clearError() = _uiState.update { it.copy(error = null) }
    fun onNavigated() = _uiState.update { it.copy(isSaved = false, isDeleted = false) }

    // ─── Photo resolution ────────────────────────────────────────────────────

    private suspend fun resolvePhotos(uris: List<String>, pointId: String): List<String> =
        uris.mapNotNull { uri ->
            when {
                uri.startsWith("https://") -> uri          // già su Firebase
                uri.startsWith("content://") -> {          // nuovo dal picker/camera
                    if (auth.currentUser != null) uploadToFirebase(uri, pointId)
                    else copyToInternalStorage(uri, pointId)
                }
                else -> uri                                 // path locale già salvato
            }
        }

    private suspend fun uploadToFirebase(uriStr: String, pointId: String): String? {
        return try {
            val uid = auth.currentUser!!.uid
            val filename = "${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child("users/$uid/photos/$pointId/$filename")
            val bytes = context.contentResolver
                .openInputStream(Uri.parse(uriStr))
                ?.use { it.readBytes() } ?: return null
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        } catch (_: Exception) { null }
    }

    private fun copyToInternalStorage(uriStr: String, pointId: String): String? {
        return try {
            val dir = File(context.filesDir, "photos/$pointId").apply { mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { input ->
                dest.outputStream().use { out -> input.copyTo(out) }
            } ?: return null
            dest.absolutePath
        } catch (_: Exception) { null }
    }
}
