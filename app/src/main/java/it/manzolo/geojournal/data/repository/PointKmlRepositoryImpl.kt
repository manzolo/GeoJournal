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

    override suspend fun importKml(uri: Uri, geoPointId: String, displayName: String): PointKml {
        val dir = File(context.filesDir, "kmls/$geoPointId").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.kml")
        context.contentResolver.openInputStream(uri)?.use { input ->
            dest.outputStream().use { output -> input.copyTo(output) }
        }
        val kml = PointKml(geoPointId = geoPointId, name = displayName, filePath = dest.absolutePath)
        dao.insert(kml.toEntity())
        return kml
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

    override suspend fun restoreFromBackup(geoPointId: String, name: String, bytes: ByteArray): PointKml {
        val dir = File(context.filesDir, "kmls/$geoPointId").apply { mkdirs() }
        val dest = File(dir, "${UUID.randomUUID()}.kml")
        dest.writeBytes(bytes)
        val kml = PointKml(geoPointId = geoPointId, name = name, filePath = dest.absolutePath)
        dao.insert(kml.toEntity())
        return kml
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
