package it.manzolo.geojournal.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import it.manzolo.geojournal.data.local.db.GeoPointDao
import it.manzolo.geojournal.data.local.db.toEntity
import it.manzolo.geojournal.data.local.db.toDomain
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoPointRepositoryImpl @Inject constructor(
    private val dao: GeoPointDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : GeoPointRepository {

    // Room è la source of truth — la UI osserva sempre il DB locale
    override fun observeAll(): Flow<List<GeoPoint>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun getById(id: String): GeoPoint? =
        dao.getById(id)?.toDomain()

    override suspend fun count(): Int = dao.count()

    override suspend fun save(point: GeoPoint) {
        val entity = point.toEntity()
        if (dao.getById(point.id) != null) dao.update(entity) else dao.insert(entity)
        syncPointToFirestore(point)
    }

    override suspend fun delete(point: GeoPoint) {
        dao.delete(point.toEntity())
        deletePointFromFirestore(point.id)
    }

    override suspend fun deleteById(id: String) {
        dao.deleteById(id)
        deletePointFromFirestore(id)
    }

    // ─── Firestore sync (best-effort, non-fatal) ─────────────────────────────
    // Phase 11: verrà gestito da WorkManager per retry offline

    private suspend fun syncPointToFirestore(point: GeoPoint) {
        val uid = auth.currentUser?.uid ?: return
        try {
            val data = mapOf(
                "id" to point.id,
                "title" to point.title,
                "description" to point.description,
                "latitude" to point.latitude,
                "longitude" to point.longitude,
                "tags" to point.tags,
                "photoUrls" to point.photoUrls,
                "audioUrl" to point.audioUrl,
                "emoji" to point.emoji,
                "createdAt" to point.createdAt.time,
                "updatedAt" to point.updatedAt.time,
                "isShared" to point.isShared,
                "ownerId" to uid
            )
            firestore
                .collection("users")
                .document(uid)
                .collection("geo_points")
                .document(point.id)
                .set(data)
                .await()
            dao.markAsSynced(point.id)
        } catch (_: Exception) {
            // Sync fallita: il punto rimane con syncedToFirestore=false
            // WorkManager lo riproverà in Phase 11
        }
    }

    // ─── Task 9: migrazione punti guest → cloud ───────────────────────────────

    override suspend fun migrateGuestPointsToUser(userId: String): Int {
        val guestPoints = dao.getGuestPoints()
        if (guestPoints.isEmpty()) return 0
        dao.claimGuestPoints(userId)
        guestPoints.forEach { entity ->
            syncPointToFirestore(entity.toDomain().copy(ownerId = userId))
        }
        return guestPoints.size
    }

    private suspend fun deletePointFromFirestore(pointId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore
                .collection("users")
                .document(uid)
                .collection("geo_points")
                .document(pointId)
                .delete()
                .await()
        } catch (_: Exception) {
            // Non fatale
        }
    }
}
