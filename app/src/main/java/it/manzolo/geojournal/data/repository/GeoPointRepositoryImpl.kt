package it.manzolo.geojournal.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.data.local.db.GeoPointDao
import it.manzolo.geojournal.data.local.db.GeoPointEntity
import it.manzolo.geojournal.data.local.db.toEntity
import it.manzolo.geojournal.data.local.db.toDomain
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GeoPointRepositoryImpl @Inject constructor(
    private val dao: GeoPointDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val storage: FirebaseStorage,
    private val userPrefs: UserPreferencesRepository
) : GeoPointRepository {

    // Room è la source of truth — la UI osserva sempre il DB locale
    override fun observeAll(): Flow<List<GeoPoint>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override fun observeActive(): Flow<List<GeoPoint>> =
        dao.observeActive().map { entities -> entities.map { it.toDomain() } }

    override fun observeArchived(): Flow<List<GeoPoint>> =
        dao.observeArchived().map { entities -> entities.map { it.toDomain() } }

    override suspend fun archivePoint(id: String) {
        dao.setArchived(id, true)
        val point = dao.getById(id)?.toDomain() ?: return
        syncPointToFirestore(point)
    }

    override suspend fun unarchivePoint(id: String) {
        dao.setArchived(id, false)
        val point = dao.getById(id)?.toDomain() ?: return
        syncPointToFirestore(point)
    }

    override fun getAllUsedTags(): Flow<List<String>> =
        dao.observeAllTagStrings().map { tagStrings ->
            tagStrings.flatMap { it.split("|") }
                .filter { it.isNotEmpty() && !it.startsWith("_") }
                .distinct()
                .sorted()
        }

    override suspend fun getById(id: String): GeoPoint? =
        dao.getById(id)?.toDomain()

    override suspend fun count(): Int = dao.count()

    override suspend fun save(point: GeoPoint) {
        val entity = point.toEntity()
        if (dao.getById(point.id) != null) dao.update(entity) else dao.insert(entity)
        syncPointToFirestore(point)
    }

    override suspend fun delete(point: GeoPoint) {
        // Cleanup Storage best-effort (no-op se le foto non erano mai state caricate)
        deleteStoragePhotos(point.photoUrls)
        dao.delete(point.toEntity())
        deletePointFromFirestore(point.id)
    }

    override suspend fun deleteById(id: String) {
        val point = dao.getById(id)?.toDomain()
        point?.let {
            deleteStoragePhotos(it.photoUrls)
        }
        dao.deleteById(id)
        deletePointFromFirestore(id)
    }

    // ─── Firestore sync (best-effort, non-fatal) ─────────────────────────────
    // Phase 11: verrà gestito da WorkManager per retry offline

    private suspend fun syncPointToFirestore(point: GeoPoint) {
        val prefs = userPrefs.preferences.first()
        if (!prefs.syncGeoPointsEnabled) return
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
                "emoji" to point.emoji,
                "createdAt" to point.createdAt.time,
                "updatedAt" to point.updatedAt.time,
                "isShared" to point.isShared,
                "ownerId" to uid,
                "rating" to point.rating,
                "isArchived" to point.isArchived
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

    // ─── Task 10b: retry punti non sincronizzati ─────────────────────────────

    override suspend fun syncUnsyncedPoints(): Int {
        if (auth.currentUser == null) return 0
        if (!userPrefs.preferences.first().syncGeoPointsEnabled) return 0
        val unsynced = dao.getUnsyncedPoints()
        var count = 0
        unsynced.forEach { entity ->
            val before = dao.getById(entity.id)?.syncedToFirestore ?: true
            syncPointToFirestore(entity.toDomain())
            val after = dao.getById(entity.id)?.syncedToFirestore ?: false
            if (!before && after) count++
        }
        return count
    }

    // ─── Task 9: migrazione punti guest → cloud ───────────────────────────────

    override suspend fun migrateGuestPointsToUser(userId: String): Int {
        val guestPoints = dao.getGuestPoints()
        if (guestPoints.isEmpty()) return 0
        dao.claimGuestPoints(userId)
        val prefs = userPrefs.preferences.first()
        guestPoints.forEach { entity ->
            val domain = entity.toDomain().copy(ownerId = userId)
            if (prefs.syncPhotosEnabled) {
                // Carica le foto locali su Storage e aggiorna il punto
                val resolvedPhotos = domain.photoUrls.map { url ->
                    if (url.startsWith("https://")) url
                    else uploadLocalPhotoToStorage(url, userId, domain.id) ?: url
                }
                val updated = domain.copy(photoUrls = resolvedPhotos)
                if (resolvedPhotos != domain.photoUrls) {
                    dao.update(updated.toEntity().copy(ownerId = userId))
                }
                syncPointToFirestore(updated)
            } else {
                syncPointToFirestore(domain)
            }
        }
        return guestPoints.size
    }

    // ─── Task 10a: pull da Firestore al login ────────────────────────────────

    override suspend fun pullFromFirestore(): Int {
        val uid = auth.currentUser?.uid ?: return 0
        return try {
            val snapshot = firestore
                .collection("users")
                .document(uid)
                .collection("geo_points")
                .get()
                .await()
            var count = 0
            for (doc in snapshot.documents) {
                val remote = doc.toGeoPointEntity(uid) ?: continue
                val local = dao.getById(remote.id)
                when {
                    local == null -> { dao.insert(remote); count++ }
                    remote.updatedAt > local.updatedAt -> { dao.update(remote); count++ }
                    // local è uguale o più recente: niente da fare
                }
            }
            count
        } catch (_: Exception) { 0 }
    }

    private fun DocumentSnapshot.toGeoPointEntity(uid: String): GeoPointEntity? {
        return try {
            val title = getString("title") ?: return null
            val latitude = getDouble("latitude") ?: return null
            val longitude = getDouble("longitude") ?: return null
            GeoPointEntity(
                id = getString("id") ?: id,
                title = title,
                description = getString("description") ?: "",
                latitude = latitude,
                longitude = longitude,
                tags = (get("tags") as? List<*>)?.joinToString("|") ?: "",
                photoUrls = (get("photoUrls") as? List<*>)?.joinToString("|") ?: "",
                emoji = getString("emoji") ?: "📍",
                createdAt = getLong("createdAt") ?: System.currentTimeMillis(),
                updatedAt = getLong("updatedAt") ?: System.currentTimeMillis(),
                ownerId = uid,
                isShared = getBoolean("isShared") ?: false,
                syncedToFirestore = true,
                rating = getLong("rating")?.toInt() ?: 0,
                isArchived = getBoolean("isArchived") ?: false
            )
        } catch (_: Exception) { null }
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

    // ─── Eliminazione account: cancella tutti i dati locali e remoti ─────────

    override suspend fun deleteAllLocalData() {
        dao.deleteAll() // CASCADE elimina anche reminders e visit_logs
    }

    override suspend fun deleteAllFirestoreData(userId: String) {
        // Cancella geo_points
        try {
            val snapshot = firestore
                .collection("users").document(userId)
                .collection("geo_points").get().await()
            for (doc in snapshot.documents) { doc.reference.delete().await() }
        } catch (_: Exception) { }

        // Cancella reminders
        try {
            val snapshot = firestore
                .collection("users").document(userId)
                .collection("reminders").get().await()
            for (doc in snapshot.documents) { doc.reference.delete().await() }
        } catch (_: Exception) { }

        // Cancella visit_logs
        try {
            val snapshot = firestore
                .collection("users").document(userId)
                .collection("visit_logs").get().await()
            for (doc in snapshot.documents) { doc.reference.delete().await() }
        } catch (_: Exception) { }

        // Cancella il documento utente radice
        try {
            firestore.collection("users").document(userId).delete().await()
        } catch (_: Exception) { }

        // Cancella foto su Firebase Storage (best-effort)
        try {
            val photosRef = storage.reference.child("users/$userId/photos")
            val photoItems = photosRef.listAll().await()
            for (prefix in photoItems.prefixes) {
                val items = prefix.listAll().await()
                for (item in items.items) { item.delete().await() }
            }
        } catch (_: Exception) { }
    }

    // ─── Storage helpers (best-effort) ───────────────────────────────────────

    private suspend fun uploadLocalPhotoToStorage(localPath: String, uid: String, pointId: String): String? {
        return try {
            val file = File(localPath)
            if (!file.exists()) return null
            val filename = file.name
            val ref = storage.reference.child("users/$uid/photos/$pointId/$filename")
            ref.putBytes(file.readBytes()).await()
            ref.downloadUrl.await().toString()
        } catch (_: Exception) { null }
    }

    private suspend fun deleteStoragePhotos(photoUrls: List<String>) {
        photoUrls.filter { it.startsWith("https://") }.forEach { url ->
            deleteStorageFile(url)
        }
    }

    private suspend fun deleteStorageFile(url: String) {
        if (!url.startsWith("https://")) return
        try {
            storage.getReferenceFromUrl(url).delete().await()
        } catch (_: Exception) { }
    }

    override suspend fun removeTagFromAllPoints(tag: String) {
        val allPoints = dao.getAll()
        allPoints
            .filter { entity -> entity.tags.split("|").any { it == tag } }
            .forEach { entity ->
                val updatedTags = entity.tags.split("|")
                    .filter { it.isNotEmpty() && it != tag }
                    .joinToString("|")
                val updated = entity.copy(tags = updatedTags, updatedAt = System.currentTimeMillis())
                dao.update(updated)
                syncPointToFirestore(updated.toDomain())
            }
    }
}
