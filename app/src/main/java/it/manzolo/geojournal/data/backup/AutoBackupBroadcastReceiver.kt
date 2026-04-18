package it.manzolo.geojournal.data.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.AndroidEntryPoint
import it.manzolo.geojournal.data.worker.AutoBackupWorker

/**
 * Riceve l'alarm esatto schedulato da [AutoBackupScheduler] e avvia
 * [AutoBackupWorker] con vincolo di rete (senza requiresCharging, troppo restrittivo
 * per un job notturno).
 */
@AndroidEntryPoint
class AutoBackupBroadcastReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != AutoBackupScheduler.ACTION_AUTO_BACKUP_FIRE) return
        val request = OneTimeWorkRequestBuilder<AutoBackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    companion object {
        const val WORK_NAME = "geojournal_auto_backup_daily"
    }
}
