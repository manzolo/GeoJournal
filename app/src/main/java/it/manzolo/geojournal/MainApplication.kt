package it.manzolo.geojournal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.HiltAndroidApp
import it.manzolo.geojournal.data.backup.AutoBackupScheduler
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.data.notification.ReminderBroadcastReceiver
import it.manzolo.geojournal.data.worker.RescheduleWorker
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
        createNotificationChannels()
        ensureRemindersScheduled()
        ensureAutoBackupScheduled()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ReminderBroadcastReceiver.CHANNEL_ID,
                "Promemoria luoghi",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Notifiche per i tuoi luoghi speciali"
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /** Rischedula tutti gli alarm attivi ad ogni avvio app (non solo al boot). */
    private fun ensureRemindersScheduled() {
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<RescheduleWorker>().build()
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
