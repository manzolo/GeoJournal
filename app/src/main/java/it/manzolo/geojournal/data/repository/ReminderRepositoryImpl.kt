package it.manzolo.geojournal.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.data.local.db.ReminderDao
import it.manzolo.geojournal.data.local.db.toDomain
import it.manzolo.geojournal.data.local.db.toEntity
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val dao: ReminderDao,
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore,
    private val userPrefs: UserPreferencesRepository
) : ReminderRepository {

    override fun observeByGeoPointId(geoPointId: String): Flow<List<Reminder>> =
        dao.observeByGeoPointId(geoPointId).map { list -> list.map { it.toDomain() } }

    override fun observeForDateRange(startEpoch: Long, endEpoch: Long): Flow<List<Reminder>> =
        dao.observeForDateRange(startEpoch, endEpoch).map { list -> list.map { it.toDomain() } }

    override fun observeAllActive(): Flow<List<Reminder>> =
        dao.observeAllActive().map { list -> list.map { it.toDomain() } }

    override suspend fun getAll(): List<Reminder> =
        dao.getAll().map { it.toDomain() }

    override suspend fun getActiveReminders(): List<Reminder> =
        dao.getActiveReminders().map { it.toDomain() }

    override suspend fun save(reminder: Reminder) {
        dao.insert(reminder.toEntity())
        syncToFirestore(reminder)
    }

    override suspend fun delete(reminder: Reminder) {
        dao.deleteById(reminder.id)
        deleteFromFirestore(reminder.id)
    }

    override suspend fun deleteByGeoPointId(geoPointId: String) {
        dao.deleteByGeoPointId(geoPointId)
        // La cancellazione Firestore per geo_point viene gestita da GeoPointRepositoryImpl
    }

    // ─── Firestore sync (best-effort, non-fatal) ────────────────────────────

    private suspend fun syncToFirestore(reminder: Reminder) {
        if (!userPrefs.preferences.first().syncRemindersEnabled) return
        val uid = auth.currentUser?.uid ?: return
        try {
            val data = mapOf(
                "id" to reminder.id,
                "geoPointId" to reminder.geoPointId,
                "title" to reminder.title,
                "startDate" to reminder.startDate,
                "endDate" to reminder.endDate,
                "type" to reminder.type.name,
                "isActive" to reminder.isActive,
                "notificationId" to reminder.notificationId
            )
            firestore
                .collection("users").document(uid)
                .collection("reminders").document(reminder.id)
                .set(data)
                .await()
        } catch (_: Exception) { }
    }

    private suspend fun deleteFromFirestore(reminderId: String) {
        val uid = auth.currentUser?.uid ?: return
        try {
            firestore
                .collection("users").document(uid)
                .collection("reminders").document(reminderId)
                .delete()
                .await()
        } catch (_: Exception) { }
    }

    // ─── Sync all locals → Firestore (retry) ───────────────────────────────

    override suspend fun syncAllToFirestore() {
        if (!userPrefs.preferences.first().syncRemindersEnabled) return
        val uid = auth.currentUser?.uid ?: return
        dao.getAll().forEach { entity ->
            try {
                val reminder = entity.toDomain()
                val data = mapOf(
                    "id" to reminder.id,
                    "geoPointId" to reminder.geoPointId,
                    "title" to reminder.title,
                    "startDate" to reminder.startDate,
                    "endDate" to reminder.endDate,
                    "type" to reminder.type.name,
                    "isActive" to reminder.isActive,
                    "notificationId" to reminder.notificationId
                )
                firestore
                    .collection("users").document(uid)
                    .collection("reminders").document(reminder.id)
                    .set(data)
                    .await()
            } catch (_: Exception) { }
        }
    }

    // ─── Pull da Firestore al login ─────────────────────────────────────────

    override suspend fun pullFromFirestore(): Int {
        val uid = auth.currentUser?.uid ?: return 0
        return try {
            val snapshot = firestore
                .collection("users").document(uid)
                .collection("reminders")
                .get().await()
            var count = 0
            for (doc in snapshot.documents) {
                try {
                    val id = doc.getString("id") ?: doc.id
                    val geoPointId = doc.getString("geoPointId") ?: continue
                    val title = doc.getString("title") ?: continue
                    val startDate = doc.getLong("startDate") ?: continue
                    val endDate = doc.getLong("endDate")
                    val type = doc.getString("type")?.let {
                        runCatching { ReminderType.valueOf(it) }.getOrDefault(ReminderType.SINGLE)
                    } ?: ReminderType.SINGLE
                    val isActive = doc.getBoolean("isActive") ?: true
                    val notificationId = doc.getLong("notificationId")?.toInt()
                        ?: kotlin.math.abs(id.hashCode())

                    val remote = Reminder(
                        id = id,
                        geoPointId = geoPointId,
                        title = title,
                        startDate = startDate,
                        endDate = endDate,
                        type = type,
                        isActive = isActive,
                        notificationId = notificationId
                    )
                    val local = dao.getAll().find { it.id == id }
                    when {
                        local == null -> { dao.insert(remote.toEntity()); count++ }
                        remote.startDate > local.startDate -> { dao.insert(remote.toEntity()); count++ }
                    }
                } catch (_: Exception) { }
            }
            count
        } catch (_: Exception) { 0 }
    }
}
