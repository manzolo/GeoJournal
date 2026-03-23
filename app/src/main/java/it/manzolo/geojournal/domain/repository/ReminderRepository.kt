package it.manzolo.geojournal.domain.repository

import it.manzolo.geojournal.domain.model.Reminder
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun observeByGeoPointId(geoPointId: String): Flow<List<Reminder>>
    fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<Reminder>>
    fun observeAllActive(): Flow<List<Reminder>>
    suspend fun getAll(): List<Reminder>
    suspend fun getActiveReminders(): List<Reminder>
    suspend fun save(reminder: Reminder)
    suspend fun delete(reminder: Reminder)
    suspend fun deleteByGeoPointId(geoPointId: String)
    suspend fun pullFromFirestore(): Int
}
