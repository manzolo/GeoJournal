package it.manzolo.geojournal.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GeoPointDao {

    @Query("SELECT * FROM geo_points ORDER BY created_at DESC")
    fun observeAll(): Flow<List<GeoPointEntity>>

    @Query("SELECT * FROM geo_points WHERE is_archived = 0 ORDER BY created_at DESC")
    fun observeActive(): Flow<List<GeoPointEntity>>

    @Query("SELECT * FROM geo_points WHERE is_archived = 1 ORDER BY created_at DESC")
    fun observeArchived(): Flow<List<GeoPointEntity>>

    @Query("UPDATE geo_points SET is_archived = :archived WHERE id = :id")
    suspend fun setArchived(id: String, archived: Boolean)

    @Query("SELECT * FROM geo_points ORDER BY created_at DESC")
    suspend fun getAll(): List<GeoPointEntity>

    @Query("SELECT * FROM geo_points WHERE id = :id")
    suspend fun getById(id: String): GeoPointEntity?

    @Query("SELECT COUNT(*) FROM geo_points")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: GeoPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<GeoPointEntity>)

    @Update
    suspend fun update(point: GeoPointEntity)

    @Delete
    suspend fun delete(point: GeoPointEntity)

    @Query("DELETE FROM geo_points WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM geo_points")
    suspend fun deleteAll()

    // Per sync WorkManager (Phase 11)
    @Query("SELECT * FROM geo_points WHERE synced_to_firestore = 0")
    suspend fun getUnsyncedPoints(): List<GeoPointEntity>

    @Query("UPDATE geo_points SET synced_to_firestore = 1 WHERE id = :id")
    suspend fun markAsSynced(id: String)

    // Migrazione guest → cloud (Task 9)
    @Query("SELECT * FROM geo_points WHERE owner_id = ''")
    suspend fun getGuestPoints(): List<GeoPointEntity>

    @Query("UPDATE geo_points SET owner_id = :userId, synced_to_firestore = 0 WHERE owner_id = ''")
    suspend fun claimGuestPoints(userId: String)

    // Tag suggeriti (Feature 3)
    @Query("SELECT tags FROM geo_points WHERE tags != ''")
    fun observeAllTagStrings(): Flow<List<String>>

    @Query("SELECT * FROM geo_points WHERE is_favorite = 1 AND is_archived = 0 ORDER BY created_at DESC")
    fun observeFavorites(): Flow<List<GeoPointEntity>>

    @Query("SELECT COUNT(*) FROM geo_points WHERE is_favorite = 1 AND is_archived = 0")
    fun countFavorites(): Flow<Int>

    @Query("UPDATE geo_points SET is_favorite = :value, updated_at = :ts WHERE id = :id")
    suspend fun setFavorite(id: String, value: Boolean, ts: Long)
}
