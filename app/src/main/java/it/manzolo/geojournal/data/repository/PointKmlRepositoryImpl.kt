package it.manzolo.geojournal.data.repository

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.manzolo.geojournal.data.local.db.PointKmlDao
import it.manzolo.geojournal.data.local.db.toDomain
import it.manzolo.geojournal.data.local.db.toEntity
import it.manzolo.geojournal.domain.model.PointKml
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PointKmlRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: PointKmlDao
) : PointKmlRepository {

    override fun observeByGeoPointId(geoPointId: String): Flow<List<PointKml>> =
        dao.observeByGeoPointId(geoPointId).map { list -> list.map { it.toDomain() } }

    override suspend fun getByGeoPointId(geoPointId: String): List<PointKml> =
        dao.getByGeoPointId(geoPointId).map { it.toDomain() }

    override suspend fun getAll(): List<PointKml> =
        dao.getAll().map { it.toDomain() }

    override suspend fun importKml(uri: Uri, geoPointId: String, displayName: String): PointKml =
        upsertKml(geoPointId, displayName) { dest ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }

    override suspend fun importTrackContent(geoPointId: String, name: String, content: ByteArray): PointKml =
        upsertKml(geoPointId, name) { dest -> dest.writeBytes(content) }

    // Deduplication by name: overwrites the existing file if a KML with the same name exists for this point.
    private suspend fun upsertKml(geoPointId: String, name: String, write: (File) -> Unit): PointKml {
        val existing = dao.findByName(geoPointId, name)
        val dest = if (existing != null) {
            File(existing.filePath)
        } else {
            File(context.filesDir, "kmls/$geoPointId").apply { mkdirs() }
                .let { File(it, "${UUID.randomUUID()}.kml") }
        }
        write(dest)
        return if (existing != null) {
            existing.toDomain()
        } else {
            val kml = PointKml(geoPointId = geoPointId, name = name, filePath = dest.absolutePath)
            dao.insert(kml.toEntity())
            kml
        }
    }

    override suspend fun deleteKml(kml: PointKml) {
        File(kml.filePath).delete()
        dao.deleteById(kml.id)
    }

    override suspend fun deleteByGeoPointId(geoPointId: String) {
        val kmls = dao.getByGeoPointId(geoPointId)
        kmls.forEach { File(it.filePath).delete() }
        dao.deleteByGeoPointId(geoPointId)
    }

    override suspend fun restoreFromBackup(id: String, geoPointId: String, name: String, bytes: ByteArray): PointKml {
        val dir = File(context.filesDir, "kmls/$geoPointId").apply { mkdirs() }
        val existing = dao.getById(id)
        return if (existing != null) {
            // Overwrite the existing file, keep the same DB record
            File(existing.filePath).writeBytes(bytes)
            existing.toDomain()
        } else {
            val dest = File(dir, "${UUID.randomUUID()}.kml")
            dest.writeBytes(bytes)
            val kml = PointKml(id = id, geoPointId = geoPointId, name = name, filePath = dest.absolutePath)
            dao.insert(kml.toEntity())
            kml
        }
    }

    override suspend fun renameKml(kml: PointKml, newName: String) {
        dao.updateName(kml.id, newName)
    }

    override suspend fun insertKml(kml: PointKml) {
        dao.insert(kml.toEntity())
    }

    override suspend fun saveKml(geoPointId: String, name: String, content: String): PointKml {
        val dir = File(context.filesDir, "kmls/$geoPointId").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.kml")
        dest.writeText(content, Charsets.UTF_8)
        val kml = PointKml(geoPointId = geoPointId, name = name, filePath = dest.absolutePath)
        dao.insert(kml.toEntity())
        return kml
    }
}
