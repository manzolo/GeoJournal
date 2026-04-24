package it.manzolo.geojournal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.backup.AutoBackupScheduler
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.data.notification.ReminderBroadcastReceiver
import it.manzolo.geojournal.data.tracking.LocationTrackingService
import it.manzolo.geojournal.data.worker.AutoBackupWorker
import it.manzolo.geojournal.data.worker.RescheduleWorker
import it.manzolo.geojournal.data.worker.SyncUnsyncedPointsWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class MainApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var autoBackupScheduler: AutoBackupScheduler
    @Inject lateinit var userPrefsRepository: UserPreferencesRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        // MapLibre bootstrap: deve essere la prima chiamata prima di qualsiasi MapView
        org.maplibre.android.MapLibre.getInstance(this)
        createNotificationChannels()
        ensureRemindersScheduled()
        ensureAutoBackupScheduled()
        scheduleSyncUnsyncedPoints()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(
                    ReminderBroadcastReceiver.CHANNEL_ID,
                    getString(R.string.notif_channel_reminders_name),
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = getString(R.string.notif_channel_reminders_desc)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    LocationTrackingService.CHANNEL_ID,
                    getString(R.string.notif_channel_tracking_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_tracking_desc)
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(
                    AutoBackupWorker.CHANNEL_ID,
                    getString(R.string.notif_channel_backup_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = getString(R.string.notif_channel_backup_desc)
                }
            )
        }
    }

    /** Rischedula tutti gli alarm attivi ad ogni avvio app (non solo al boot). */
    private fun ensureRemindersScheduled() {
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<RescheduleWorker>().build()
        )
    }

    /** Rilancia la sync dei punti non ancora sincronizzati su Firestore, quando c'è rete. */
    private fun scheduleSyncUnsyncedPoints() {
        val request = OneTimeWorkRequestBuilder<SyncUnsyncedPointsWorker>()
            .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
            .build()
        WorkManager.getInstance(this).enqueueUniqueWork(
            "sync_unsynced_points",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /** Se il backup automatico è abilitato, assicura che il periodic work sia attivo. */
    private fun ensureAutoBackupScheduled() {
        CoroutineScope(Dispatchers.IO).launch {
            if (userPrefsRepository.preferences.first().autoBackupEnabled) {
                autoBackupScheduler.schedule()
            }
        }
    }
}
