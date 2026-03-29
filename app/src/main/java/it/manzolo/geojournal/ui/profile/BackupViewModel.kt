package it.manzolo.geojournal.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.data.backup.AutoBackupScheduler
import it.manzolo.geojournal.data.backup.BackupManager
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val backupManager: BackupManager,
    private val userPrefsRepository: UserPreferencesRepository,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val geojExporter: GeoPointExporter,
    private val geoPointRepository: GeoPointRepository,
    private val reminderRepository: ReminderRepository,
    private val kmlRepository: PointKmlRepository
) : ViewModel() {

    enum class Op { EXPORT, IMPORT, IMPORT_POINT, COMPRESS }

    sealed class State {
        object Idle : State()
        data class Working(val op: Op) : State()
        data class ExportOk(val pointCount: Int) : State()
        data class ImportOk(val pointCount: Int, val reminderCount: Int, val visitCount: Int) : State()
        data class ImportPointOk(val title: String) : State()
        data class CompressOk(val savedKb: Long) : State()
        data class Error(val message: String) : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    val autoBackupEnabled: StateFlow<Boolean> = userPrefsRepository.preferences
        .map { it.autoBackupEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val driveBackupUri: StateFlow<String> = userPrefsRepository.preferences
        .map { it.driveBackupUri }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val lastLocalBackupTimestamp: StateFlow<Long> = userPrefsRepository.preferences
        .map { it.lastLocalBackupTimestamp }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val lastDriveBackupTimestamp: StateFlow<Long> = userPrefsRepository.preferences
        .map { it.lastDriveBackupTimestamp }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0L)

    val lastDriveBackupSuccess: StateFlow<Boolean> = userPrefsRepository.preferences
        .map { it.lastDriveBackupSuccess }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        viewModelScope.launch {
            if (userPrefsRepository.preferences.first().autoBackupEnabled) {
                autoBackupScheduler.schedule()
            }
        }
    }

    fun setAutoBackup(enabled: Boolean) {
        viewModelScope.launch {
            userPrefsRepository.setAutoBackupEnabled(enabled)
            if (enabled) autoBackupScheduler.schedule() else autoBackupScheduler.cancel()
        }
    }

    fun setDriveBackupUri(uri: Uri, context: android.content.Context) {
        viewModelScope.launch {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            userPrefsRepository.setDriveBackupUri(uri.toString())
        }
    }

    fun clearDriveBackupUri() {
        viewModelScope.launch {
            userPrefsRepository.setDriveBackupUri("")
        }
    }

    fun export(uri: Uri) {
        viewModelScope.launch {
            _state.value = State.Working(Op.EXPORT)
            _state.value = runCatching {
                val count = backupManager.exportToUri(uri)
                userPrefsRepository.setLastLocalBackup(System.currentTimeMillis())
                State.ExportOk(count)
            }.getOrElse { State.Error(it.message ?: "Errore durante l'esportazione") }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            _state.value = State.Working(Op.IMPORT)
            _state.value = runCatching {
                val r = backupManager.importFromUri(uri)
                State.ImportOk(r.pointCount, r.reminderCount, r.visitCount)
            }.getOrElse { State.Error(it.message ?: "Errore durante l'importazione") }
        }
    }

    fun importGeojPoint(uri: Uri) {
        viewModelScope.launch {
            _state.value = State.Working(Op.IMPORT_POINT)
            _state.value = runCatching {
                val result = geojExporter.importFromUri(uri)
                geoPointRepository.save(result.point)
                result.reminders.forEach { reminderRepository.save(it) }
                result.kmlFiles.forEach { (name, bytes) ->
                    kmlRepository.restoreFromBackup(result.point.id, name, bytes)
                }
                State.ImportPointOk(result.point.title)
            }.getOrElse { State.Error(it.message ?: "Errore durante l'importazione del punto") }
        }
    }

    fun compressExistingPhotos() {
        viewModelScope.launch {
            _state.value = State.Working(Op.COMPRESS)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val photosDir = File(context.filesDir, "photos")
                    var savedBytes = 0L
                    if (photosDir.exists()) {
                        photosDir.walkTopDown()
                            .filter { it.isFile && it.extension.lowercase() == "jpg" && it.length() > 300_000L }
                            .forEach { file ->
                                val before = file.length()
                                compressFileInPlace(file)
                                val after = file.length()
                                if (after < before) savedBytes += (before - after)
                            }
                    }
                    State.CompressOk(savedBytes / 1024)
                }.getOrElse { State.Error(it.message ?: "Errore compressione") }
            }
            _state.value = result
        }
    }

    private fun compressFileInPlace(file: File) = runCatching {
        val maxDim = 1920
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, opts)
        val rawW = opts.outWidth; val rawH = opts.outHeight
        if (rawW <= 0 || rawH <= 0) return@runCatching

        var sampleSize = 1; var halfW = rawW / 2; var halfH = rawH / 2
        while (halfW >= maxDim && halfH >= maxDim) { sampleSize *= 2; halfW /= 2; halfH /= 2 }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, decodeOpts)
            ?: return@runCatching

        val scaled = if (decoded.width > maxDim || decoded.height > maxDim) {
            val r = maxDim.toFloat() / maxOf(decoded.width, decoded.height)
            Bitmap.createScaledBitmap(decoded, (decoded.width * r).toInt(), (decoded.height * r).toInt(), true)
                .also { decoded.recycle() }
        } else decoded

        // Apply EXIF orientation
        val orientation = runCatching {
            ExifInterface(file.absolutePath)
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val oriented = if (orientation != ExifInterface.ORIENTATION_NORMAL &&
            orientation != ExifInterface.ORIENTATION_UNDEFINED) {
            val matrix = Matrix().apply {
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
            Bitmap.createBitmap(scaled, 0, 0, scaled.width, scaled.height, matrix, true)
                .also { scaled.recycle() }
        } else scaled

        val tmp = File(file.parent, "${file.nameWithoutExtension}_tmp.jpg")
        try {
            tmp.outputStream().use { oriented.compress(Bitmap.CompressFormat.JPEG, 80, it) }
            tmp.renameTo(file)
        } finally {
            oriented.recycle()
            if (tmp.exists()) tmp.delete()
        }
        Unit
    }

    fun resetState() { _state.value = State.Idle }
}
