package it.manzolo.geojournal.data.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.domain.repository.VisitLogRepository

@HiltWorker
class SyncUnsyncedPointsWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repository: GeoPointRepository,
    private val reminderRepository: ReminderRepository,
    private val visitLogRepository: VisitLogRepository
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        return runCatching {
            repository.syncUnsyncedPoints()
            reminderRepository.syncAllToFirestore()
            visitLogRepository.syncAllToFirestore()
            Result.success()
        }.getOrElse { Result.retry() }
    }
}
