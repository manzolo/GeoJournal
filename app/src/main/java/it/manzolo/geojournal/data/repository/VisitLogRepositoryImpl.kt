package it.manzolo.geojournal.data.repository

import it.manzolo.geojournal.data.local.db.VisitLogDao
import it.manzolo.geojournal.data.local.db.toDomain
import it.manzolo.geojournal.data.local.db.toEntity
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.domain.repository.VisitLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitLogRepositoryImpl @Inject constructor(
    private val dao: VisitLogDao
) : VisitLogRepository {

    override fun observeByGeoPointId(geoPointId: String): Flow<List<VisitLogEntry>> =
        dao.observeByGeoPointId(geoPointId).map { list -> list.map { it.toDomain() } }

    override fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<VisitLogEntry>> =
        dao.observeForDateRange(startEpoch, endEpoch).map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<VisitLogEntry> =
        dao.getAll().map { it.toDomain() }

    override suspend fun saveEntry(entry: VisitLogEntry) =
        dao.insert(entry.toEntity())

    override suspend fun logVisit(geoPointId: String, note: String) {
        val entry = VisitLogEntry(
            id = UUID.randomUUID().toString(),
            geoPointId = geoPointId,
            visitedAt = System.currentTimeMillis(),
            note = note
        )
        dao.insert(entry.toEntity())
    }

    override suspend fun delete(entry: VisitLogEntry) =
        dao.deleteById(entry.id)
}
