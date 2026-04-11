package it.manzolo.geojournal.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.R
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.data.backup.AutoBackupScheduler
import it.manzolo.geojournal.data.backup.BackupManager
import it.manzolo.geojournal.data.backup.DriveApiClient
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val kmlRepository: PointKmlRepository,
    private val firebaseAuth: FirebaseAuth
) : ViewModel() {

    enum class Op { EXPORT, IMPORT, IMPORT_POINT, COMPRESS, DRIVE_UPLOAD }

    sealed class State {
        object Idle : State()
        data class Working(val op: Op) : State()
        data class ExportOk(val pointCount: Int) : State()
        data class ImportOk(val pointCount: Int, val reminderCount: Int, val visitCount: Int) : State()
        data class ImportPointOk(val title: String) : State()
        data class CompressOk(val savedKb: Long) : State()
        data class Error(val message: String) : State()
        object DriveUploadOk : State()
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Emits a PendingIntent when Drive authorization needs user consent (Activity resolution required). */
    private val _driveAuthEvent = MutableSharedFlow<android.app.PendingIntent>(extraBufferCapacity = 1)
    val driveAuthEvent: SharedFlow<android.app.PendingIntent> = _driveAuthEvent.asSharedFlow()

    val autoBackupEnabled: StateFlow<Boolean> = userPrefsRepository.preferences
        .map { it.autoBackupEnabled }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val driveBackupUri: StateFlow<String> = userPrefsRepository.preferences
        .map { it.driveBackupUri }
        .stateIn(viewModelScope, SharingStarted.Eagerly, "")

    val driveAccountEmail: StateFlow<String> = userPrefsRepository.preferences
        .map { it.driveAccountEmail }
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
            if (enabled) autoBackupScheduler.reschedule() else autoBackupScheduler.cancel()
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

    // ── Drive REST API ────────────────────────────────────────────────────────

    /**
     * Requests authorization for the Drive `drive.file` scope.
     * If user consent is required an Activity launch is needed — the PendingIntent is emitted
     * via [driveAuthEvent] and the caller must handle it via [onDriveAuthResultOk].
     */
    fun connectDriveApi(context: Context) {
        viewModelScope.launch {
            val email = firebaseAuth.currentUser?.email
            if (email.isNullOrBlank()) {
                _state.value = State.Error(context.getString(R.string.backup_drive_requires_google))
                return@launch
            }
            try {
                val authRequest = AuthorizationRequest.builder()
                    .setRequestedScopes(listOf(Scope("https://www.googleapis.com/auth/drive.file")))
                    .build()
                Identity.getAuthorizationClient(context)
                    .authorize(authRequest)
                    .addOnSuccessListener { result ->
                        if (result.hasResolution()) {
                            result.pendingIntent?.let { pi ->
                                viewModelScope.launch { _driveAuthEvent.emit(pi) }
                            }
                        } else {
                            viewModelScope.launch { userPrefsRepository.setDriveAccountEmail(email) }
                        }
                    }
                    .addOnFailureListener { e ->
                        _state.value = State.Error(
                            context.getString(R.string.backup_drive_connect_error, e.localizedMessage ?: "")
                        )
                    }
            } catch (e: Exception) {
                _state.value = State.Error(
                    context.getString(R.string.backup_drive_connect_error, e.localizedMessage ?: "")
                )
            }
        }
    }

    /** Called after a successful Drive auth resolution (Activity result OK). */
    fun onDriveAuthResultOk() {
        viewModelScope.launch {
            val email = firebaseAuth.currentUser?.email ?: return@launch
            userPrefsRepository.setDriveAccountEmail(email)
        }
    }

    fun disconnectDriveApi() {
        viewModelScope.launch {
            userPrefsRepository.setDriveAccountEmail("")
        }
    }

    /** Immediately uploads a fresh backup to Drive via the REST API. */
    fun backupNowViaDriveApi() {
        viewModelScope.launch {
            val email = userPrefsRepository.preferences.first().driveAccountEmail
            if (email.isBlank()) {
                _state.value = State.Error(context.getString(R.string.backup_drive_not_configured))
                return@launch
            }
            _state.value = State.Working(Op.DRIVE_UPLOAD)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val localFile = backupManager.exportToFile()
                    backupManager.pruneOldBackups()
                    DriveApiClient(context, email).uploadOrReplaceBackup(localFile)
                }
            }
            if (result.isSuccess) {
                userPrefsRepository.setLastDriveBackup(System.currentTimeMillis(), true)
                userPrefsRepository.setLastLocalBackup(System.currentTimeMillis())
                _state.value = State.DriveUploadOk
            } else {
                userPrefsRepository.setLastDriveBackup(System.currentTimeMillis(), false)
                _state.value = State.Error(
                    result.exceptionOrNull()?.message
                        ?: context.getString(R.string.backup_notif_error_body_generic)
                )
            }
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
                    kmlRepository.restoreFromBackup(java.util.UUID.randomUUID().toString(), result.point.id, name, bytes)
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

        // Read EXIF before decoding (for orientation + metadata preservation)
        val srcExif = runCatching { ExifInterface(file.absolutePath) }.getOrNull()
        val orientation = srcExif?.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            ?: ExifInterface.ORIENTATION_NORMAL
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
            // Preserve EXIF metadata
            if (srcExif != null) {
                val dst = ExifInterface(tmp.absolutePath)
                listOf(
                    ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME,
                    ExifInterface.TAG_DATETIME_DIGITIZED, ExifInterface.TAG_MODEL,
                    ExifInterface.TAG_MAKE, ExifInterface.TAG_GPS_LATITUDE,
                    ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
                    ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE,
                    ExifInterface.TAG_GPS_ALTITUDE_REF
                ).forEach { tag -> srcExif.getAttribute(tag)?.let { dst.setAttribute(tag, it) } }
                dst.setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL.toString())
                dst.saveAttributes()
            }
            tmp.renameTo(file)
        } finally {
            oriented.recycle()
            if (tmp.exists()) tmp.delete()
        }
        Unit
    }

    fun resetState() { _state.value = State.Idle }
}
