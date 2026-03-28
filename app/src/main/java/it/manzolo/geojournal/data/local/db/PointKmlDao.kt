package it.manzolo.geojournal.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PointKmlDao {

    @Query("SELECT * FROM point_kmls WHERE geo_point_id = :geoPointId ORDER BY imported_at ASC")
    fun observeByGeoPointId(geoPointId: String): Flow<List<PointKmlEntity>>

    @Query("SELECT * FROM point_kmls WHERE geo_point_id = :geoPointId ORDER BY imported_at ASC")
    suspend fun getByGeoPointId(geoPointId: String): List<PointKmlEntity>

    @Query("SELECT * FROM point_kmls ORDER BY imported_at ASC")
    suspend fun getAll(): List<PointKmlEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(kml: PointKmlEntity)

    @Query("DELETE FROM point_kmls WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM point_kmls WHERE geo_point_id = :geoPointId")
    suspend fun deleteByGeoPointId(geoPointId: String)
}
