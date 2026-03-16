package it.manzolo.geojournal.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.BuildConfig
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.domain.repository.VisitLogRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.util.Date
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class ImportResult(val pointCount: Int, val reminderCount: Int, val visitCount: Int)

@Singleton
class BackupManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val geoPointRepository: GeoPointRepository,
    private val reminderRepository: ReminderRepository,
    private val visitLogRepository: VisitLogRepository
) {
    companion object {
        const val SCHEMA_VERSION = 1
        // Prefisso usato nel JSON per le foto locali nel backup ("backup://photos/...")
        private const val BACKUP_PHOTO_PREFIX = "backup://photos/"
    }

    // ─── Export ───────────────────────────────────────────────────────────────

    suspend fun exportToUri(uri: Uri): Int {
        val points    = geoPointRepository.observeAll().first()
        val reminders = reminderRepository.getAll()
        val visits    = visitLogRepository.getAll()

        context.contentResolver.openOutputStream(uri)?.use { out ->
            ZipOutputStream(BufferedOutputStream(out)).use { zip ->

                // 1. backup.json
                val json = buildJson(points, reminders, visits)
                zip.putNextEntry(ZipEntry("backup.json"))
                zip.write(json.toByteArray(Charsets.UTF_8))
                zip.closeEntry()

                // 2. Foto locali (le URL Firebase vengono saltate — sono già nel cloud)
                points.forEach { point ->
                    point.photoUrls.forEach { url ->
                        if (!url.startsWith("https://") && !url.startsWith("content://")) {
                            val file = File(url)
                            if (file.exists()) {
                                zip.putNextEntry(ZipEntry("photos/${point.id}/${file.name}"))
                                file.inputStream().use { it.copyTo(zip) }
                                zip.closeEntry()
                            }
                        }
                    }
                }
            }
        }
        return points.size
    }

    private fun buildJson(
        points: List<GeoPoint>,
        reminders: List<Reminder>,
        visits: List<VisitLogEntry>
    ): String {
        val root = JSONObject().apply {
            put("schemaVersion", SCHEMA_VERSION)
            put("exportedAt", System.currentTimeMillis())
            put("appVersion", BuildConfig.VERSION_NAME)

            put("geoPoints", JSONArray().also { arr ->
                points.forEach { p ->
                    arr.put(JSONObject().apply {
                        put("id", p.id)
                        put("title", p.title)
                        put("description", p.description)
                        put("latitude", p.latitude)
                        put("longitude", p.longitude)
                        put("emoji", p.emoji)
                        put("tags", JSONArray(p.tags))
                        put("ownerId", p.ownerId)
                        put("isShared", p.isShared)
                        put("createdAt", p.createdAt.time)
                        put("updatedAt", p.updatedAt.time)
                        // Foto: URL Firebase intatte, file locali → percorso relativo nel backup
                        put("photoUrls", JSONArray(p.photoUrls.map { url ->
                            if (url.startsWith("https://") || url.startsWith("content://")) url
                            else "$BACKUP_PHOTO_PREFIX${p.id}/${File(url).name}"
                        }))
                        p.audioUrl?.let { put("audioUrl", it) }
                    })
                }
            })

            put("reminders", JSONArray().also { arr ->
                reminders.forEach { r ->
                    arr.put(JSONObject().apply {
                        put("id", r.id)
                        put("geoPointId", r.geoPointId)
                        put("title", r.title)
                        put("startDate", r.startDate)
                        r.endDate?.let { put("endDate", it) }
                        put("type", r.type.name)
                        put("isActive", r.isActive)
                        put("notificationId", r.notificationId)
                    })
                }
            })

            put("visitLogs", JSONArray().also { arr ->
                visits.forEach { v ->
                    arr.put(JSONObject().apply {
                        put("id", v.id)
                        put("geoPointId", v.geoPointId)
                        put("visitedAt", v.visitedAt)
                        put("note", v.note)
                    })
                }
            })
        }
        return root.toString(2)
    }

    // ─── Import ───────────────────────────────────────────────────────────────

    suspend fun importFromUri(uri: Uri): ImportResult {
        // Prima lettura: estrai le foto dal ZIP
        val photoEntries = mutableMapOf<String, ByteArray>() // "photos/pointId/file.jpg" → bytes
        var jsonContent: String? = null

        context.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "backup.json" ->
                            jsonContent = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("photos/") ->
                            photoEntries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val json = JSONObject(jsonContent ?: throw IllegalArgumentException("backup.json non trovato"))

        // Salva le foto sul filesDir e costruisce mappa zipPath → absolutePath
        val photoPathMap = mutableMapOf<String, String>()
        photoEntries.forEach { (zipPath, bytes) ->
            // zipPath = "photos/{pointId}/{filename}"
            val parts = zipPath.split("/")
            if (parts.size == 3) {
                val pointId = parts[1]
                val filename = parts[2]
                val dir = File(context.filesDir, "photos/$pointId").apply { mkdirs() }
                val dest = File(dir, filename)
                dest.writeBytes(bytes)
                photoPathMap["$BACKUP_PHOTO_PREFIX$pointId/$filename"] = dest.absolutePath
            }
        }

        // GeoPoints
        val pointsArr = json.getJSONArray("geoPoints")
        var pointCount = 0
        for (i in 0 until pointsArr.length()) {
            val obj = pointsArr.getJSONObject(i)
            val photoUrlsArr = obj.getJSONArray("photoUrls")
            val resolvedPhotos = (0 until photoUrlsArr.length()).map { j ->
                val url = photoUrlsArr.getString(j)
                photoPathMap[url] ?: url  // sostituisce backup:// con path assoluto
            }
            val point = GeoPoint(
                id          = obj.getString("id"),
                title       = obj.getString("title"),
                description = obj.optString("description", ""),
                latitude    = obj.getDouble("latitude"),
                longitude   = obj.getDouble("longitude"),
                emoji       = obj.optString("emoji", "📍"),
                tags        = obj.getJSONArray("tags").let { a ->
                    (0 until a.length()).map { a.getString(it) }
                },
                photoUrls   = resolvedPhotos,
                audioUrl    = if (obj.has("audioUrl")) obj.getString("audioUrl") else null,
                ownerId     = obj.optString("ownerId", ""),
                isShared    = obj.optBoolean("isShared", false),
                createdAt   = Date(obj.getLong("createdAt")),
                updatedAt   = Date(obj.getLong("updatedAt"))
            )
            geoPointRepository.save(point)
            pointCount++
        }

        // Reminders
        val remArr = json.optJSONArray("reminders") ?: JSONArray()
        var reminderCount = 0
        for (i in 0 until remArr.length()) {
            val obj = remArr.getJSONObject(i)
            val reminder = Reminder(
                id             = obj.getString("id"),
                geoPointId     = obj.getString("geoPointId"),
                title          = obj.getString("title"),
                startDate      = obj.getLong("startDate"),
                endDate        = if (obj.has("endDate")) obj.getLong("endDate") else null,
                type           = runCatching { ReminderType.valueOf(obj.getString("type")) }
                                    .getOrDefault(ReminderType.SINGLE),
                isActive       = obj.optBoolean("isActive", true),
                notificationId = obj.optInt("notificationId", UUID.randomUUID().hashCode())
            )
            reminderRepository.save(reminder)
            reminderCount++
        }

        // VisitLogs
        val visitArr = json.optJSONArray("visitLogs") ?: JSONArray()
        var visitCount = 0
        for (i in 0 until visitArr.length()) {
            val obj = visitArr.getJSONObject(i)
            val entry = VisitLogEntry(
                id          = obj.getString("id"),
                geoPointId  = obj.getString("geoPointId"),
                visitedAt   = obj.getLong("visitedAt"),
                note        = obj.optString("note", "")
            )
            visitLogRepository.saveEntry(entry)
            visitCount++
        }

        return ImportResult(pointCount, reminderCount, visitCount)
    }
}
