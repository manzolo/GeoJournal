package it.manzolo.geojournal.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.data.local.db.VisitLogDao
import it.manzolo.geojournal.data.local.db.toDomain
import it.manzolo.geojournal.data.local.db.toEntity
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.domain.repository.VisitLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VisitLogRepositoryImpl @Inject constructor(
    private val dao: VisitLogDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPrefs: UserPreferencesRepository
) : VisitLogRepository {

    override fun observeByGeoPointId(geoPointId: String): Flow<List<VisitLogEntry>> =
        dao.observeByGeoPointId(geoPointId).map { list -> list.map { it.toDomain() } }

    override fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<VisitLogEntry>> =
        dao.observeForDateRange(startEpoch, endEpoch).map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<VisitLogEntry> =
        dao.getAll().map { it.toDomain() }

    override suspend fun saveEntry(entry: VisitLogEntry) {
        dao.insert(entry.toEntity())
        syncToFirestore(entry)
    }

    override suspend fun logVisit(geoPointId: String, note: String) {
        val entry = VisitLogEntry(
            id = UUID.randomUUID().toString(),
            geoPointId = geoPointId,
            visitedAt = System.currentTimeMillis(),
            note = note
        )
        dao.insert(entry.toEntity())
        syncToFirestore(entry)
    }

    override suspend fun delete(entry: VisitLogEntry) {
        dao.deleteById(entry.id)
        deleteFromFirestore(entry.id)
    }

    // ─── Firestore sync (best-effort, non-fatal) ────────────────────────────

    private suspend fun syncToFirestore(entry: VisitLogEntry) {
        if (!userPrefs.preferences.first().syncVisitLogsEnabled) return
        val uid = auth.currentUser?.uid ?: return
        try {
            val data = mapOf(
                "id" to entry.id,
                "geoPointId" to entry.geoPointId,
                "visitedAt" to entry.visitedAt,
                "note" to entry.note
            )
            firestore
                .collection("users").document(uid)
                .collection("visit_logs").document(entry.id)
                .set(data)
                .await()
        } catch (_: Exception) { }
    }

    private suspend fun deleteFromFirestore(entryId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore
                .collection("users").document(uid)
                .collection("visit_logs").document(entryId)
                .delete()
                .await()
        } catch (_: Exception) { }
    }

    // ─── Pull da Firestore al login ─────────────────────────────────────────

    override suspend fun pullFromFirestore(): Int {
        val uid = auth.currentUser?.uid ?: return 0
        return try {
            val snapshot = firestore
                .collection("users").document(uid)
                .collection("visit_logs")
                .get().await()
            var count = 0
            for (doc in snapshot.documents) {
                try {
                    val id = doc.getString("id") ?: doc.id
                    val geoPointId = doc.getString("geoPointId") ?: continue
                    val visitedAt = doc.getLong("visitedAt") ?: continue
                    val note = doc.getString("note") ?: ""

                    val alreadyExists = dao.getAll().any { it.id == id }
                    if (!alreadyExists) {
                        dao.insert(
                            VisitLogEntry(
                                id = id,
                                geoPointId = geoPointId,
                                visitedAt = visitedAt,
                                note = note
                            ).toEntity()
                        )
                        count++
                    }
                } catch (_: Exception) { }
            }
            count
        } catch (_: Exception) { 0 }
    }
}
