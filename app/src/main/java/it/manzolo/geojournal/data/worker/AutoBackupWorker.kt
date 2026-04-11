package it.manzolo.geojournal.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.manzolo.geojournal.data.backup.BackupManager
import it.manzolo.geojournal.data.backup.DriveApiClient
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.first
import android.app.NotificationManager
import androidx.core.app.NotificationCompat
import it.manzolo.geojournal.R

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val userPrefsRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, params) {

    companion object {
        const val CHANNEL_ID = "backup_channel"
        private const val NOTIF_ID_PROGRESS = 1001
        private const val NOTIF_ID_ERROR = 1002
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

    override suspend fun doWork(): Result {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Show progress notification like WhatsApp does
        val progressNotif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(applicationContext.getString(R.string.backup_notif_title))
            .setContentText(applicationContext.getString(R.string.backup_notif_text))
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()

        nm.notify(NOTIF_ID_PROGRESS, progressNotif)

        return try {
            val result = runCatching {
                // 1. Backup locale
                val localFile = backupManager.exportToFile()
                backupManager.pruneOldBackups()

                // 2. Backup su cloud: Drive REST API (primario) oppure SAF (fallback)
                val prefs = userPrefsRepository.preferences.first()
                val driveEmail = prefs.driveAccountEmail
                val driveResult: kotlin.Result<Unit> = when {
                    driveEmail.isNotBlank() -> {
                        // Percorso Drive REST API — upload immediato
                        val apiResult = runCatching {
                            DriveApiClient(applicationContext, driveEmail)
                                .uploadOrReplaceBackup(localFile)
                            Unit
                        }
                        // Fallback SAF se Drive API fallisce e SAF è configurato
                        if (apiResult.isFailure && prefs.driveBackupUri.isNotEmpty()) {
                            safUpload(prefs.driveBackupUri, localFile)
                        } else {
                            apiResult
                        }
                    }
                    prefs.driveBackupUri.isNotEmpty() -> {
                        // Nessuna Drive API configurata: solo SAF
                        safUpload(prefs.driveBackupUri, localFile)
                    }
                    else -> kotlin.Result.success(Unit) // nessun cloud configurato, solo backup locale
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

                // 3. Salva timestamp ultimo backup
                userPrefsRepository.setLastLocalBackup(System.currentTimeMillis())
            }
            if (result.isSuccess) Result.success() else Result.failure()
        } finally {
            // Rimuove la notifica di progresso al termine del worker (con successo o fail)
            nm.cancel(NOTIF_ID_PROGRESS)
        }
    }
}
