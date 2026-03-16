package it.manzolo.geojournal.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE geo_point_id = :geoPointId ORDER BY start_date ASC")
    fun observeByGeoPointId(geoPointId: String): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE is_active = 1")
    suspend fun getActiveReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE is_active = 1")
    fun observeAllActive(): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE start_date BETWEEN :startEpoch AND :endEpoch OR (end_date IS NOT NULL AND end_date BETWEEN :startEpoch AND :endEpoch) OR (start_date <= :startEpoch AND end_date >= :endEpoch)")
    fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<ReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM reminders WHERE geo_point_id = :geoPointId")
    suspend fun deleteByGeoPointId(geoPointId: String)
}
