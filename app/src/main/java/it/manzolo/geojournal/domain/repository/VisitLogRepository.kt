package it.manzolo.geojournal.domain.repository

import it.manzolo.geojournal.domain.model.VisitLogEntry
import kotlinx.coroutines.flow.Flow

interface VisitLogRepository {
    fun observeByGeoPointId(geoPointId: String): Flow<List<VisitLogEntry>>
    fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<VisitLogEntry>>
    suspend fun getAll(): List<VisitLogEntry>
    suspend fun saveEntry(entry: VisitLogEntry)
    suspend fun logVisit(geoPointId: String, note: String = "")
    suspend fun delete(entry: VisitLogEntry)
    suspend fun pullFromFirestore(): Int
    suspend fun syncAllToFirestore()
}
