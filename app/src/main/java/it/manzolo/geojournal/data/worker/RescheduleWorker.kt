package it.manzolo.geojournal.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.manzolo.geojournal.data.backup.AutoBackupScheduler
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.data.notification.ReminderScheduler
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.first

@HiltWorker
class RescheduleWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val reminderRepository: ReminderRepository,
    private val scheduler: ReminderScheduler,
    private val autoBackupScheduler: AutoBackupScheduler,
    private val userPrefsRepository: UserPreferencesRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val reminders = reminderRepository.getActiveReminders()
        scheduler.rescheduleAll(reminders)
        // Ri-schedula anche l'alarm del backup giornaliero (perso al reboot)
        if (userPrefsRepository.preferences.first().autoBackupEnabled) {
            autoBackupScheduler.schedule()
        }
        return Result.success()
    }
}
