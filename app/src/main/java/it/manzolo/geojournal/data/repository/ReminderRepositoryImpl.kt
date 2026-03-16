package it.manzolo.geojournal.data.repository

import it.manzolo.geojournal.data.local.db.ReminderDao
import it.manzolo.geojournal.data.local.db.toDomain
import it.manzolo.geojournal.data.local.db.toEntity
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao
) : ReminderRepository {

    override fun observeByGeoPointId(geoPointId: String): Flow<List<Reminder>> =
        dao.observeByGeoPointId(geoPointId).map { list -> list.map { it.toDomain() } }

    override fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<Reminder>> =
        dao.observeForDateRange(startEpoch, endEpoch).map { list -> list.map { it.toDomain() } }

    override fun observeAllActive(): Flow<List<Reminder>> =
        dao.observeAllActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Reminder> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getActiveReminders(): List<Reminder> =
        dao.getActiveReminders().map { it.toDomain() }

    override suspend fun save(reminder: Reminder) =
        dao.insert(reminder.toEntity())

    override suspend fun delete(reminder: Reminder) =
        dao.deleteById(reminder.id)

    override suspend fun deleteByGeoPointId(geoPointId: String) =
        dao.deleteByGeoPointId(geoPointId)
}
