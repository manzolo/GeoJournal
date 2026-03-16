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
            val driveUriStr = userPrefsRepository.preferences.first().driveBackupUri
            if (driveUriStr.isNotEmpty()) {
                runCatching {
                    val uri = Uri.parse(driveUriStr)
                    applicationContext.contentResolver.openOutputStream(uri, "wt")?.use { out ->
                        localFile.inputStream().use { it.copyTo(out) }
                    }
                }
                // Se il Drive write fallisce, non consideriamo il job fallito
            }

            Result.success()
        }.getOrElse { Result.failure() }
    }
}
