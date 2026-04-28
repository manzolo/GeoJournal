package it.manzolo.geojournal.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.manzolo.geojournal.data.backup.AutoBackupScheduler
import it.manzolo.geojournal.data.backup.BackupManager
import it.manzolo.geojournal.data.backup.DriveApiClient
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout
import android.app.NotificationManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import it.manzolo.geojournal.R

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val userPrefsRepository: UserPreferencesRepository,
    private val autoBackupScheduler: AutoBackupScheduler
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "backup_channel"
        private const val NOTIF_ID_PROGRESS = 1001
        private const val NOTIF_ID_ERROR = 1002
        private const val NOTIFICATION_TIMEOUT_MS = 15 * 60 * 1000L
        private const val BACKUP_OPERATION_TIMEOUT_MS = 10 * 60 * 1000L
    }

    private fun safUpload(uriString: String, localFile: java.io.File): kotlin.Result<Unit> = runCatching {
        val uri = Uri.parse(uriString)
        val pfd = applicationContext.contentResolver.openFileDescriptor(uri, "wt")
            ?: error("openFileDescriptor returned null for SAF URI")
        pfd.use { descriptor ->
            java.io.FileOutputStream(descriptor.fileDescriptor).use { out ->
                localFile.inputStream().use { inp -> inp.copyTo(out) }
                out.flush()
                descriptor.fileDescriptor.sync()
            }
        }
        applicationContext.contentResolver.notifyChange(
            uri, null, android.content.ContentResolver.NOTIFY_SYNC_TO_NETWORK
        )
        runCatching {
            applicationContext.contentResolver.query(uri, null, null, null, null)?.use { it.moveToFirst() }
        }
        Unit
    }

    override suspend fun getForegroundInfo(): androidx.work.ForegroundInfo {
        val progressNotif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.backup_notif_title))
            .setContentText(applicationContext.getString(R.string.backup_notif_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(0, 0, true)
            .setTimeoutAfter(NOTIFICATION_TIMEOUT_MS)
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            androidx.work.ForegroundInfo(
                NOTIF_ID_PROGRESS,
                progressNotif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            androidx.work.ForegroundInfo(NOTIF_ID_PROGRESS, progressNotif)
        }
    }

    override suspend fun doWork(): Result {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        return try {
            val currentFingerprint = backupManager.computeFingerprint()
            val prefs = userPrefsRepository.preferences.first()

            if (currentFingerprint == prefs.lastBackupFingerprint && prefs.lastBackupFingerprint.isNotEmpty()) {
                userPrefsRepository.setLastBackupChecked(System.currentTimeMillis())
                Result.success()
            } else {
                try {
                    setForeground(getForegroundInfo())
                } catch (e: Exception) {
                    // setForeground può fallire su Android 12+ se avviato dal deep background
                }

                val result = runCatching {
                    withTimeout(BACKUP_OPERATION_TIMEOUT_MS) {
                        val localFile = backupManager.exportToFile()
                        backupManager.pruneOldBackups()

                        // Cloud: Drive REST API (primario) oppure SAF (fallback)
                        val driveEmail = prefs.driveAccountEmail
                        val driveResult: kotlin.Result<Unit> = when {
                            driveEmail.isNotBlank() -> {
                                val apiResult = runCatching {
                                    DriveApiClient(applicationContext, driveEmail)
                                        .uploadOrReplaceBackup(localFile)
                                    Unit
                                }
                                if (apiResult.isFailure && prefs.driveBackupUri.isNotEmpty()) {
                                    safUpload(prefs.driveBackupUri, localFile)
                                } else {
                                    apiResult
                                }
                            }
                            prefs.driveBackupUri.isNotEmpty() -> safUpload(prefs.driveBackupUri, localFile)
                            else -> kotlin.Result.success(Unit)
                        }

                        val hasCloudTarget = driveEmail.isNotBlank() || prefs.driveBackupUri.isNotEmpty()
                        if (hasCloudTarget) {
                            userPrefsRepository.setLastDriveBackup(
                                timestamp = System.currentTimeMillis(),
                                success = driveResult.isSuccess
                            )
                            if (driveResult.isFailure) {
                                val ex = driveResult.exceptionOrNull()
                                val detail = ex?.localizedMessage ?: ex?.message
                                val userMsg = if (detail != null) {
                                    applicationContext.getString(R.string.backup_notif_error_body, detail)
                                } else {
                                    applicationContext.getString(R.string.backup_notif_error_body_generic)
                                }
                                val errorNotif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                                    .setContentTitle(applicationContext.getString(R.string.backup_notif_error_title))
                                    .setContentText(applicationContext.getString(R.string.backup_notif_error_body_generic))
                                    .setSmallIcon(android.R.drawable.stat_notify_error)
                                    .setStyle(NotificationCompat.BigTextStyle().bigText(userMsg))
                                    .setAutoCancel(true)
                                    .build()
                                nm.notify(NOTIF_ID_ERROR, errorNotif)
                            }
                        }

                        userPrefsRepository.setBackupSuccess(System.currentTimeMillis(), currentFingerprint)
                    }
                }
                if (result.isSuccess) Result.success() else Result.failure()
            }
        } finally {
            nm.cancel(NOTIF_ID_PROGRESS)
            runCatching {
                if (userPrefsRepository.preferences.first().autoBackupEnabled) {
                    autoBackupScheduler.schedule()
                }
            }
        }
    }
}
