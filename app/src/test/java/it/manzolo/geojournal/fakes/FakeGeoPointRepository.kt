package it.manzolo.geojournal.fakes

import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class FakeGeoPointRepository : GeoPointRepository {

    private val _points = MutableStateFlow<List<GeoPoint>>(emptyList())

    override fun observeAll(): Flow<List<GeoPoint>> = _points

    override fun observeActive(): Flow<List<GeoPoint>> =
        _points.map { list -> list.filter { !it.isArchived } }

    override fun observeArchived(): Flow<List<GeoPoint>> =
        _points.map { list -> list.filter { it.isArchived } }

    override suspend fun archivePoint(id: String) {
        _points.update { list -> list.map { if (it.id == id) it.copy(isArchived = true) else it } }
    }

    override suspend fun unarchivePoint(id: String) {
        _points.update { list -> list.map { if (it.id == id) it.copy(isArchived = false) else it } }
    }

    override fun getAllUsedTags(): Flow<List<String>> =
        _points.map { list -> list.flatMap { it.tags }.distinct().sorted() }

    override suspend fun getById(id: String): GeoPoint? =
        _points.value.find { it.id == id }

    override suspend fun count(): Int = _points.value.size

    override suspend fun save(point: GeoPoint) {
        _points.update { list ->
            val existing = list.indexOfFirst { it.id == point.id }
            if (existing >= 0) list.toMutableList().also { it[existing] = point }
            else list + point
        }
    }

    override suspend fun delete(point: GeoPoint) {
        _points.update { list -> list.filter { it.id != point.id } }
    }

    override suspend fun deleteById(id: String) {
        _points.update { list -> list.filter { it.id != id } }
    }

    override suspend fun removeTagFromAllPoints(tag: String) {
        _points.update { list ->
            list.map { point -> point.copy(tags = point.tags.filter { it != tag }) }
        }
    }

    override fun observeFavorites(): Flow<List<GeoPoint>> =
        _points.map { list -> list.filter { it.isFavorite && !it.isArchived } }

    override fun countFavorites(): Flow<Int> =
        _points.map { list -> list.count { it.isFavorite && !it.isArchived } }

    override suspend fun toggleFavorite(id: String, isFavorite: Boolean) {
        _points.update { list -> list.map { if (it.id == id) it.copy(isFavorite = isFavorite) else it } }
    }

    override suspend fun migrateGuestPointsToUser(userId: String): Int = 0
    override suspend fun pullFromFirestore(): Int = 0
    override suspend fun syncUnsyncedPoints(): Int = 0
    override suspend fun deleteAllLocalData() { _points.value = emptyList() }
    override suspend fun deleteAllFirestoreData(userId: String) {}
}
