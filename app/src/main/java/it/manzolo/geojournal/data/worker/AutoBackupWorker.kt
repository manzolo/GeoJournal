package it.manzolo.geojournal.data.worker

import android.content.Context
import android.net.Uri
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.manzolo.geojournal.data.backup.BackupManager
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.flow.first

@HiltWorker
class AutoBackupWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val backupManager: BackupManager,
    private val userPrefsRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            // 1. Backup locale
            val localFile = backupManager.exportToFile()
            backupManager.pruneOldBackups()

            // 2. Backup su Drive (SAF) se l'URI è impostato e valido
            val prefs = userPrefsRepository.preferences.first()
            if (prefs.driveBackupUri.isNotEmpty()) {
                val driveResult = runCatching {
                    val uri = Uri.parse(prefs.driveBackupUri)
                    val pfd = applicationContext.contentResolver.openFileDescriptor(uri, "wt")
                        ?: error("openFileDescriptor returned null for Drive URI")
                    pfd.use { descriptor ->
                        java.io.FileOutputStream(descriptor.fileDescriptor).use { out ->
                            localFile.inputStream().use { inp -> inp.copyTo(out) }
                            out.flush()
                            // Forza a livello di file system il provider (Drive) a consolidare su disco
                            descriptor.fileDescriptor.sync()
                        }
                    }
                    // Notifica il sistema per forzare la sincronizzazione su Drive, usando il flag NOTIFY_SYNC_TO_NETWORK (API 24+)
                    applicationContext.contentResolver.notifyChange(uri, null, android.content.ContentResolver.NOTIFY_SYNC_TO_NETWORK)
                    
                    // Forza Drive a rinfrescare lo stato del file interrogandolo e richiedendo l'aggiornamento
                    runCatching {
                        applicationContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            cursor.moveToFirst()
                        }
                    }
                }
                userPrefsRepository.setLastDriveBackup(
                    timestamp = System.currentTimeMillis(),
                    success = driveResult.isSuccess
                )
            }

            // 3. Salva timestamp ultimo backup
            userPrefsRepository.setLastLocalBackup(System.currentTimeMillis())

            Result.success()
        }.getOrElse { Result.failure() }
    }
}
