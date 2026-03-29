package it.manzolo.geojournal.ui.addedit

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.ui.map.MapViewModel
import it.manzolo.geojournal.data.notification.ReminderScheduler
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.PointKml
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    val suggestedTags: List<String> = emptyList(),
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val photoUris: List<String> = emptyList(),
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val isDeleted: Boolean = false,
    val error: String? = null,
    val showEmojiPicker: Boolean = false,
    val showDeleteConfirm: Boolean = false,
    val reminders: List<Reminder> = emptyList(),
    val showReminderSheet: Boolean = false,
    val rating: Int = 0,
    val notes: String = "",
    val isAdditionalDetailsExpanded: Boolean = false,
    val kmls: List<PointKml> = emptyList()
)

@HiltViewModel
class AddEditViewModel @Inject constructor(
    application: Application,
    private val repository: GeoPointRepository,
    private val userPrefs: UserPreferencesRepository,
    private val auth: FirebaseAuth,
    private val storage: FirebaseStorage,
    private val reminderRepository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val kmlRepository: PointKmlRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private val context get() = getApplication<Application>()

    private val pointId: String = savedStateHandle.get<String>("pointId") ?: "new"
    val isEditMode: Boolean = pointId != "new"
    fun getPointIdForReminder(): String = existingId

    private val prefillTitle: String = savedStateHandle.get<String>("title") ?: ""
    private val prefillLat: Double = savedStateHandle.get<String>("lat")?.toDoubleOrNull() ?: 0.0
    private val prefillLon: Double = savedStateHandle.get<String>("lon")?.toDoubleOrNull() ?: 0.0

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    private var existingId: String = UUID.randomUUID().toString()
    private var existingCreatedAt: Date = Date()
    private var _allUsedTags: List<String> = emptyList()
    private var tagSuggestionsJob: Job? = null

    init {
        if (isEditMode) loadPoint() else _uiState.update {
            it.copy(
                isLoading = false,
                title = prefillTitle,
                latitude = prefillLat,
                longitude = prefillLon
            )
        }
        observeTagSuggestions()
    }

    private fun observeTagSuggestions() {
        viewModelScope.launch {
            repository.getAllUsedTags().collect { allTags ->
                _allUsedTags = allTags
                recomputeSuggestions()
            }
        }
    }

    private fun recomputeSuggestions() {
        // Snapshot atomico: legge lo stato una sola volta per evitare race condition
        val state = _uiState.value
        val input = state.tagInput.trim().lowercase()
        val currentTags = state.tags
        val suggestions = if (input.isEmpty()) {
            _allUsedTags.filter { it !in currentTags }.take(8)
        } else {
            _allUsedTags.filter { it.contains(input, ignoreCase = true) && it !in currentTags }.take(8)
        }
        _uiState.update { it.copy(suggestedTags = suggestions) }
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
                        isLoading = false,
                        rating = point.rating,
                        notes = point.notes
                    )
                }
            } else {
                _uiState.update { it.copy(isLoading = false, error = context.getString(R.string.error_point_not_found)) }
            }
        }
        viewModelScope.launch {
            reminderRepository.observeByGeoPointId(pointId)
                .collect { list ->
                    _uiState.update { state ->
                        val shouldExpand = state.isAdditionalDetailsExpanded ||
                            list.isNotEmpty() || state.tags.isNotEmpty() ||
                            state.rating > 0 || state.notes.isNotBlank() || state.kmls.isNotEmpty()
                        state.copy(reminders = list, isAdditionalDetailsExpanded = shouldExpand)
                    }
                }
        }
        viewModelScope.launch {
            kmlRepository.observeByGeoPointId(pointId)
                .collect { list ->
                    _uiState.update { state ->
                        val shouldExpand = state.isAdditionalDetailsExpanded ||
                            list.isNotEmpty() || state.tags.isNotEmpty() ||
                            state.rating > 0 || state.notes.isNotBlank() || state.reminders.isNotEmpty()
                        state.copy(kmls = list, isAdditionalDetailsExpanded = shouldExpand)
                    }
                }
        }
    }

    fun importFromMapsUrl(url: String, notFoundMsg: String) {
        val isGoogleMaps = Regex("google\\.[a-zA-Z]+/maps").containsMatchIn(url)
            || url.contains("maps.google.")
        if (!isGoogleMaps) {
            _uiState.update { it.copy(error = notFoundMsg) }
            return
        }
        val (name, lat, lon) = parseMapsUrl(url) ?: run {
            _uiState.update { it.copy(error = notFoundMsg) }
            return
        }
        _uiState.update {
            it.copy(
                latitude = lat,
                longitude = lon,
                title = if (it.title.isBlank()) name else it.title,
                error = null
            )
        }
    }

    private data class MapsCoords(val title: String, val lat: Double, val lon: Double)

    private fun parseMapsUrl(url: String): MapsCoords? {
        var name = ""
        val placeMatch = Regex("/maps/place/([^/@?#]+)").find(url)
        if (placeMatch != null) {
            name = Uri.decode(placeMatch.groupValues[1].replace("+", " ")).trim()
        }
        val coordMatches = Regex("!3d([\\-0-9.]+)!4d([\\-0-9.]+)").findAll(url).toList()
        if (coordMatches.isNotEmpty()) {
            val last = coordMatches.last()
            val lat = last.groupValues[1].toDoubleOrNull() ?: return null
            val lon = last.groupValues[2].toDoubleOrNull() ?: return null
            return MapsCoords(name.ifEmpty { "Google Maps" }, lat, lon)
        }
        val atMatch = Regex("@([\\-0-9.]+),([\\-0-9.]+)").find(url)
        if (atMatch != null) {
            val lat = atMatch.groupValues[1].toDoubleOrNull() ?: return null
            val lon = atMatch.groupValues[2].toDoubleOrNull() ?: return null
            return MapsCoords(name.ifEmpty { "Google Maps" }, lat, lon)
        }
        val q = Uri.parse(url).getQueryParameter("q") ?: return null
        val parts = q.split(",")
        if (parts.size >= 2) {
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            return MapsCoords(q, lat, lon)
        }
        return null
    }

    fun updateTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun updateDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun updateTagInput(value: String) {
        _uiState.update { it.copy(tagInput = value) }
        // Debounce: evita filtri su ogni singolo carattere digitato
        tagSuggestionsJob?.cancel()
        tagSuggestionsJob = viewModelScope.launch {
            delay(200)
            recomputeSuggestions()
        }
    }
    fun selectEmoji(emoji: String) = _uiState.update { it.copy(emoji = emoji, showEmojiPicker = false) }
    fun toggleEmojiPicker() = _uiState.update { it.copy(showEmojiPicker = !it.showEmojiPicker) }
    fun updateLocation(lat: Double, lon: Double) = _uiState.update { it.copy(latitude = lat, longitude = lon) }

    fun addTag() {
        val tag = _uiState.value.tagInput.trim().lowercase()
        if (tag.isNotBlank() && !_uiState.value.tags.contains(tag)) {
            _uiState.update { it.copy(tags = it.tags + tag, tagInput = "") }
            recomputeSuggestions()
        }
    }

    fun addTagFromSuggestion(tag: String) {
        if (!_uiState.value.tags.contains(tag)) {
            _uiState.update { it.copy(tags = it.tags + tag) }
            recomputeSuggestions()
        }
    }

    fun removeTag(tag: String) {
        _uiState.update { it.copy(tags = it.tags - tag) }
        recomputeSuggestions()
    }
    fun toggleDeleteConfirm() = _uiState.update { it.copy(showDeleteConfirm = !it.showDeleteConfirm) }

    fun addPhotoUri(uri: String) = _uiState.update { it.copy(photoUris = it.photoUris + uri) }
    fun removePhotoUri(uri: String) = _uiState.update { it.copy(photoUris = it.photoUris - uri) }
    fun movePhotoLeft(uri: String) = _uiState.update {
        val list = it.photoUris.toMutableList()
        val idx = list.indexOf(uri)
        if (idx > 0) { list.add(idx - 1, list.removeAt(idx)) }
        it.copy(photoUris = list)
    }

    fun toggleReminderSheet() = _uiState.update { it.copy(showReminderSheet = !it.showReminderSheet) }
    fun updateRating(stars: Int) = _uiState.update { it.copy(rating = if (it.rating == stars) 0 else stars) }
    fun updateNotes(value: String) = _uiState.update { it.copy(notes = value) }
    fun toggleAdditionalDetails() = _uiState.update { it.copy(isAdditionalDetailsExpanded = !it.isAdditionalDetailsExpanded) }

    fun importKml(uri: Uri, displayName: String) {
        viewModelScope.launch {
            runCatching { kmlRepository.importKml(uri, existingId, displayName) }
        }
    }

    fun deleteKml(kml: PointKml) {
        viewModelScope.launch { kmlRepository.deleteKml(kml) }
    }

    fun addReminder(reminder: Reminder) {
        val r = reminder.copy(geoPointId = existingId)
        if (isEditMode) {
            viewModelScope.launch {
                reminderRepository.save(r)
                scheduler.scheduleReminder(r)
            }
        } else {
            // Nuovo punto: teniamo i reminder in stato locale, li salviamo insieme al punto
            _uiState.update { it.copy(reminders = it.reminders + r) }
        }
    }

    fun deleteReminder(reminder: Reminder) {
        if (isEditMode) {
            viewModelScope.launch {
                reminderRepository.delete(reminder)
                scheduler.cancelReminder(reminder)
            }
        } else {
            _uiState.update { it.copy(reminders = it.reminders - reminder) }
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.latitude == 0.0 && state.longitude == 0.0) {
            _uiState.update { it.copy(error = context.getString(R.string.addedit_error_no_location)) }
            return
        }
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = context.getString(R.string.addedit_error_title_required)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val prefs = userPrefs.preferences.first()
            val resolvedPhotos = resolvePhotos(state.photoUris, existingId)
            val point = GeoPoint(
                id = existingId,
                title = state.title.trim(),
                description = state.description.trim(),
                emoji = state.emoji,
                tags = state.tags,
                latitude = state.latitude,
                longitude = state.longitude,
                photoUrls = resolvedPhotos,
                createdAt = if (isEditMode) existingCreatedAt else Date(),
                updatedAt = Date(),
                ownerId = prefs.userId,
                rating = state.rating,
                notes = state.notes.trim()
            )
            repository.save(point)
            // Salva i reminder pendenti (solo nuovo modo, edit mode li salva subito)
            if (!isEditMode) {
                state.reminders.forEach { r ->
                    reminderRepository.save(r)
                    scheduler.scheduleReminder(r)
                }
                // Nuovo punto: centra la mappa su di esso dopo il salvataggio
                MapViewModel.FocusRequest.send(point.latitude, point.longitude, point.id)
            }
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
    fun showError(msg: String) = _uiState.update { it.copy(error = msg) }
    fun onNavigated() = _uiState.update { it.copy(isSaved = false, isDeleted = false) }

    // ─── Photo resolution ────────────────────────────────────────────────────

    private fun compressedImageBytes(
        uriStr: String,
        maxDim: Int = 1920,
        quality: Int = 80
    ): ByteArray? = runCatching {
        val uri = Uri.parse(uriStr)
        // Pass 1: bounds only, no pixel allocation
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        val rawW = opts.outWidth; val rawH = opts.outHeight
        if (rawW <= 0 || rawH <= 0) return null
        // Calculate inSampleSize (power of 2)
        var sampleSize = 1; var halfW = rawW / 2; var halfH = rawH / 2
        while (halfW >= maxDim && halfH >= maxDim) { sampleSize *= 2; halfW /= 2; halfH /= 2 }
        // Pass 2: decode at reduced resolution
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        } ?: return null
        // Scale down further if still > maxDim
        val scaled = if (decoded.width > maxDim || decoded.height > maxDim) {
            val r = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(decoded, (decoded.width * r).toInt(), (decoded.height * r).toInt(), true)
                .also { decoded.recycle() }
        } else decoded
        // Apply EXIF orientation
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it) }
                ?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val matrix = exifOrientationMatrix(orientation)
        val oriented = if (orientation != ExifInterface.ORIENTATION_NORMAL &&
            orientation != ExifInterface.ORIENTATION_UNDEFINED) {
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                .also { scaled.recycle() }
        } else scaled
        try {
            val baos = java.io.ByteArrayOutputStream()
            oriented.compress(Bitmap.CompressFormat.JPEG, quality, baos)
            baos.toByteArray()
        } finally { oriented.recycle() }
    }.getOrNull()

    private fun exifOrientationMatrix(orientation: Int) = Matrix().apply {
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { postRotate(90f); postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { postRotate(-90f); postScale(-1f, 1f) }
        }
    }

    private suspend fun resolvePhotos(uris: List<String>, pointId: String): List<String> {
        val prefs = userPrefs.preferences.first()
        return uris.mapNotNull { uri ->
            when {
                uri.startsWith("https://") -> uri  // già su Firebase Storage
                uri.startsWith("content://") -> {   // nuovo dal picker/camera
                    if (prefs.syncPhotosEnabled && auth.currentUser != null)
                        uploadToFirebase(uri, pointId) ?: copyToInternalStorage(uri, pointId)
                    else copyToInternalStorage(uri, pointId)
                }
                else -> uri  // path locale già salvato
            }
        }
    }

    private suspend fun uploadToFirebase(uriStr: String, pointId: String): String? {
        return try {
            val uid = auth.currentUser!!.uid
            val filename = "${UUID.randomUUID()}.jpg"
            val ref = storage.reference.child("users/$uid/photos/$pointId/$filename")
            val bytes = compressedImageBytes(uriStr)
                ?: context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
                ?: return null
            ref.putBytes(bytes).await()
            ref.downloadUrl.await().toString()
        } catch (_: Exception) { null }
    }

    private fun copyToInternalStorage(uriStr: String, pointId: String): String? {
        return try {
            val dir = File(context.filesDir, "photos/$pointId").apply { mkdirs() }
            val dest = File(dir, "${UUID.randomUUID()}.jpg")
            val bytes = compressedImageBytes(uriStr)
                ?: context.contentResolver.openInputStream(Uri.parse(uriStr))?.use { it.readBytes() }
                ?: return null
            dest.writeBytes(bytes)
            dest.absolutePath
        } catch (_: Exception) { null }
    }
}
