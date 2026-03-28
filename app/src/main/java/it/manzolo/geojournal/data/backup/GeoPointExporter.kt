package it.manzolo.geojournal.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.BuildConfig
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Date
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

data class GeojImportResult(
    val point: GeoPoint,
    val senderMessage: String?,
    val reminders: List<Reminder> = emptyList(),
    val kmlFiles: List<Pair<String, ByteArray>> = emptyList()
)

@Singleton
class GeoPointExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val kmlRepository: PointKmlRepository
) {
    companion object {
        private const val SCHEMA_VERSION = 4
        private const val BACKUP_PHOTO_PREFIX = "geoj://photos/"
    }

    // ─── Export singolo punto → file .geoj in cacheDir ───────────────────────

    suspend fun exportPointToCache(
        point: GeoPoint,
        senderMessage: String? = null,
        options: ShareOptions = ShareOptions()
    ): File {
        val dir = File(context.cacheDir, "geoj").also { it.mkdirs() }
        val safeName = point.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40)
        val file = File(dir, "geojournal_$safeName.geoj")

        val now = System.currentTimeMillis()
        val remindersToInclude = if (options.includeReminders) {
            reminderRepository.observeByGeoPointId(point.id).first().filter { r ->
                r.type == ReminderType.ANNUAL_RECURRING ||
                r.type == ReminderType.DATE_RANGE ||
                (r.type == ReminderType.SINGLE && r.startDate >= now)
            }
        } else emptyList()

        val kmlsToInclude = if (options.includeKml) {
            kmlRepository.getByGeoPointId(point.id)
        } else emptyList()

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            val json = buildPointJson(point, senderMessage, options, remindersToInclude)
            zip.putNextEntry(ZipEntry("point.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            if (options.includePhotos) {
                point.photoUrls.forEach { url ->
                    if (!url.startsWith("https://") && !url.startsWith("content://")) {
                        val photoFile = File(url)
                        if (photoFile.exists()) {
                            zip.putNextEntry(ZipEntry("photos/${photoFile.name}"))
                            photoFile.inputStream().use { it.copyTo(zip) }
                            zip.closeEntry()
                        }
                    }
                }
            }

            kmlsToInclude.forEach { kml ->
                val kmlFile = File(kml.filePath)
                if (kmlFile.exists()) {
                    zip.putNextEntry(ZipEntry("kmls/${kml.name}"))
                    kmlFile.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        return file
    }

    private fun buildPointJson(
        point: GeoPoint,
        senderMessage: String?,
        options: ShareOptions,
        reminders: List<Reminder>
    ): String = JSONObject().apply {
        put("schemaVersion", SCHEMA_VERSION)
        put("exportedAt", System.currentTimeMillis())
        put("appVersion", BuildConfig.VERSION_NAME)
        if (!senderMessage.isNullOrBlank()) put("senderMessage", senderMessage)
        put("id", point.id)
        put("title", point.title)
        put("description", point.description)
        put("latitude", point.latitude)
        put("longitude", point.longitude)
        put("emoji", point.emoji)
        if (options.includeTags) put("tags", JSONArray(point.tags))
        if (options.includeNotes && point.notes.isNotBlank()) put("notes", point.notes)
        put("ownerId", point.ownerId)
        put("isShared", point.isShared)
        put("createdAt", point.createdAt.time)
        put("updatedAt", point.updatedAt.time)
        if (options.includePhotos) {
            put("photoUrls", JSONArray(point.photoUrls.map { url ->
                if (url.startsWith("https://") || url.startsWith("content://")) url
                else "$BACKUP_PHOTO_PREFIX${File(url).name}"
            }))
        }
        if (reminders.isNotEmpty()) {
            put("reminders", JSONArray(reminders.map { r ->
                JSONObject().apply {
                    put("id", r.id)
                    put("title", r.title)
                    put("startDate", r.startDate)
                    r.endDate?.let { put("endDate", it) }
                    put("type", r.type.name)
                    put("isActive", r.isActive)
                }
            }))
        }
    }.toString(2)

    // ─── Import singolo punto da URI .geoj ───────────────────────────────────

    suspend fun importFromUri(uri: Uri): GeojImportResult {
        val photoEntries = mutableMapOf<String, ByteArray>()
        val kmlEntries = mutableMapOf<String, ByteArray>()
        var jsonContent: String? = null

        context.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == "point.json" ->
                            jsonContent = zip.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("photos/") ->
                            photoEntries[entry.name] = zip.readBytes()
                        entry.name.startsWith("kmls/") ->
                            kmlEntries[entry.name] = zip.readBytes()
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val json = JSONObject(jsonContent ?: throw IllegalArgumentException("point.json non trovato"))

        val newPointId = UUID.randomUUID().toString()

        // Salva le foto nel filesDir del punto
        val photoPathMap = mutableMapOf<String, String>()
        photoEntries.forEach { (zipPath, bytes) ->
            val filename = zipPath.removePrefix("photos/")
            val dir = File(context.filesDir, "photos/$newPointId").apply { mkdirs() }
            val dest = File(dir, filename)
            dest.writeBytes(bytes)
            photoPathMap["$BACKUP_PHOTO_PREFIX$filename"] = dest.absolutePath
        }

        val photoUrlsArr = json.optJSONArray("photoUrls")
        val resolvedPhotos = if (photoUrlsArr != null) {
            (0 until photoUrlsArr.length()).map { i ->
                val url = photoUrlsArr.getString(i)
                photoPathMap[url] ?: url
            }
        } else emptyList()

        val tagsArr = json.optJSONArray("tags")
        val tags = if (tagsArr != null) {
            (0 until tagsArr.length()).map { tagsArr.getString(it) }
        } else emptyList()

        val point = GeoPoint(
            id          = newPointId,
            title       = json.getString("title"),
            description = json.optString("description", ""),
            latitude    = json.getDouble("latitude"),
            longitude   = json.getDouble("longitude"),
            emoji       = json.optString("emoji", "📍"),
            tags        = tags,
            photoUrls   = resolvedPhotos,
            notes       = json.optString("notes", ""),
            ownerId     = "",
            isShared    = false,
            rating      = 0,
            createdAt   = Date(json.getLong("createdAt")),
            updatedAt   = Date()
        )

        val remindersArr = json.optJSONArray("reminders")
        val reminders = if (remindersArr != null) {
            (0 until remindersArr.length()).mapNotNull { i ->
                runCatching {
                    val r = remindersArr.getJSONObject(i)
                    Reminder(
                        id         = UUID.randomUUID().toString(), // nuovo ID
                        geoPointId = newPointId,
                        title      = r.getString("title"),
                        startDate  = r.getLong("startDate"),
                        endDate    = r.optLong("endDate").takeIf { r.has("endDate") },
                        type       = ReminderType.valueOf(r.getString("type")),
                        isActive   = r.optBoolean("isActive", true)
                    )
                }.getOrNull()
            }
        } else emptyList()

        val kmlFiles = kmlEntries.map { (zipPath, bytes) ->
            val name = zipPath.removePrefix("kmls/")
            name to bytes
        }

        val senderMessage = json.optString("senderMessage").takeIf { it.isNotBlank() }
        return GeojImportResult(point, senderMessage, reminders, kmlFiles)
    }
}
