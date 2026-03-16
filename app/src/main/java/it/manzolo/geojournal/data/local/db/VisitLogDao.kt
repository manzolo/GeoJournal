package it.manzolo.geojournal.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface VisitLogDao {
    @Query("SELECT * FROM visit_logs WHERE geo_point_id = :geoPointId ORDER BY visited_at DESC")
    fun observeByGeoPointId(geoPointId: String): Flow<List<VisitLogEntity>>

    @Query("SELECT * FROM visit_logs WHERE visited_at BETWEEN :startEpoch AND :endEpoch ORDER BY visited_at ASC")
    fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<VisitLogEntity>>

    @Query("SELECT * FROM visit_logs ORDER BY visited_at ASC")
    suspend fun getAll(): List<VisitLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VisitLogEntity)

    @Query("DELETE FROM visit_logs WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM visit_logs WHERE geo_point_id = :geoPointId")
    suspend fun deleteByGeoPointId(geoPointId: String)
}
