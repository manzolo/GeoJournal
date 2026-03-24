package it.manzolo.geojournal.data.backup

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.BuildConfig
import it.manzolo.geojournal.domain.model.GeoPoint
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

data class GeojImportResult(val point: GeoPoint, val senderMessage: String?)

@Singleton
class GeoPointExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val SCHEMA_VERSION = 2
        private const val BACKUP_PHOTO_PREFIX = "geoj://photos/"
    }

    // ─── Export singolo punto → file .geoj in cacheDir ───────────────────────

    fun exportPointToCache(point: GeoPoint, senderMessage: String? = null): File {
        val dir = File(context.cacheDir, "geoj").also { it.mkdirs() }
        val safeName = point.title.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(40)
        val file = File(dir, "geojournal_$safeName.geoj")

        ZipOutputStream(BufferedOutputStream(FileOutputStream(file))).use { zip ->
            // point.json (reminder e visite non inclusi: sono dati personali dell'utente)
            val json = buildPointJson(point, senderMessage)
            zip.putNextEntry(ZipEntry("point.json"))
            zip.write(json.toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // Foto locali (URL Firebase saltate)
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
        return file
    }

    private fun buildPointJson(point: GeoPoint, senderMessage: String? = null): String =
        JSONObject().apply {
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
            put("tags", JSONArray(point.tags))
            put("rating", point.rating)
            put("ownerId", point.ownerId)
            put("isShared", point.isShared)
            put("createdAt", point.createdAt.time)
            put("updatedAt", point.updatedAt.time)
            put("photoUrls", JSONArray(point.photoUrls.map { url ->
                if (url.startsWith("https://") || url.startsWith("content://")) url
                else "$BACKUP_PHOTO_PREFIX${File(url).name}"
            }))
            point.audioUrl?.let { put("audioUrl", it) }
        }.toString(2)

    // ─── Import singolo punto da URI .geoj ───────────────────────────────────

    suspend fun importFromUri(uri: Uri): GeojImportResult {
        val photoEntries = mutableMapOf<String, ByteArray>()
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
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        val json = JSONObject(jsonContent ?: throw IllegalArgumentException("point.json non trovato"))

        // Salva le foto nel filesDir del punto
        val newPointId = UUID.randomUUID().toString()
        val photoPathMap = mutableMapOf<String, String>()
        photoEntries.forEach { (zipPath, bytes) ->
            val filename = zipPath.removePrefix("photos/")
            val dir = File(context.filesDir, "photos/$newPointId").apply { mkdirs() }
            val dest = File(dir, filename)
            dest.writeBytes(bytes)
            photoPathMap["$BACKUP_PHOTO_PREFIX$filename"] = dest.absolutePath
        }

        val photoUrlsArr = json.getJSONArray("photoUrls")
        val resolvedPhotos = (0 until photoUrlsArr.length()).map { i ->
            val url = photoUrlsArr.getString(i)
            photoPathMap[url] ?: url
        }

        val point = GeoPoint(
            id          = newPointId, // nuovo ID per evitare conflitti
            title       = json.getString("title"),
            description = json.optString("description", ""),
            latitude    = json.getDouble("latitude"),
            longitude   = json.getDouble("longitude"),
            emoji       = json.optString("emoji", "📍"),
            tags        = json.getJSONArray("tags").let { a ->
                (0 until a.length()).map { a.getString(it) }
            },
            photoUrls   = resolvedPhotos,
            audioUrl    = if (json.has("audioUrl")) json.getString("audioUrl") else null,
            ownerId     = "",
            isShared    = false,
            rating      = json.optInt("rating", 0),
            createdAt   = Date(json.getLong("createdAt")),
            updatedAt   = Date()
        )
        val senderMessage = json.optString("senderMessage").takeIf { it.isNotBlank() }
        return GeojImportResult(point, senderMessage)
    }
}
