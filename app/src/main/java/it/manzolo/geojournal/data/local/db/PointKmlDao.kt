package it.manzolo.geojournal.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PointKmlDao {

    @Query("SELECT * FROM point_kmls WHERE geo_point_id = :geoPointId ORDER BY imported_at ASC")
    fun observeByGeoPointId(geoPointId: String): Flow<List<PointKmlEntity>>

    @Query("SELECT * FROM point_kmls WHERE geo_point_id = :geoPointId ORDER BY imported_at ASC")
    suspend fun getByGeoPointId(geoPointId: String): List<PointKmlEntity>

    @Query("SELECT * FROM point_kmls ORDER BY imported_at ASC")
    suspend fun getAll(): List<PointKmlEntity>

    @Query("SELECT * FROM point_kmls WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PointKmlEntity?

    @Query("SELECT * FROM point_kmls WHERE geo_point_id = :geoPointId AND name = :name LIMIT 1")
    suspend fun findByName(geoPointId: String, name: String): PointKmlEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(kml: PointKmlEntity)

    @Update
    suspend fun update(kml: PointKmlEntity)

    @Query("UPDATE point_kmls SET name = :name WHERE id = :id")
    suspend fun updateName(id: String, name: String)

    @Query("DELETE FROM point_kmls WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM point_kmls WHERE geo_point_id = :geoPointId")
    suspend fun deleteByGeoPointId(geoPointId: String)
}
