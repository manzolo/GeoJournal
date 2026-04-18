package it.manzolo.geojournal.domain.repository

import it.manzolo.geojournal.domain.model.GeoPoint
import kotlinx.coroutines.flow.Flow

interface GeoPointRepository {
    fun observeAll(): Flow<List<GeoPoint>>
    fun observeActive(): Flow<List<GeoPoint>>
    fun observeArchived(): Flow<List<GeoPoint>>
    fun observeFavorites(): Flow<List<GeoPoint>>
    fun countFavorites(): Flow<Int>
    suspend fun toggleFavorite(id: String, isFavorite: Boolean)
    suspend fun archivePoint(id: String)
    suspend fun unarchivePoint(id: String)
    fun getAllUsedTags(): Flow<List<String>>
    suspend fun getById(id: String): GeoPoint?
    suspend fun count(): Int
    suspend fun save(point: GeoPoint)
    suspend fun delete(point: GeoPoint)
    suspend fun deleteById(id: String)
    suspend fun migrateGuestPointsToUser(userId: String): Int
    suspend fun pullFromFirestore(): Int
    suspend fun syncUnsyncedPoints(): Int
    suspend fun deleteAllLocalData()
    suspend fun deleteAllFirestoreData(userId: String)
    suspend fun removeTagFromAllPoints(tag: String)
}
