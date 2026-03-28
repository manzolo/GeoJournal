package it.manzolo.geojournal.domain.repository

import android.net.Uri
import it.manzolo.geojournal.domain.model.PointKml
import kotlinx.coroutines.flow.Flow

interface PointKmlRepository {
    fun observeByGeoPointId(geoPointId: String): Flow<List<PointKml>>
    suspend fun getByGeoPointId(geoPointId: String): List<PointKml>
    suspend fun getAll(): List<PointKml>
    suspend fun importKml(uri: Uri, geoPointId: String, displayName: String): PointKml
    suspend fun deleteKml(kml: PointKml)
    suspend fun deleteByGeoPointId(geoPointId: String)
    /** Restore a KML file from raw bytes (used by BackupManager import). */
    suspend fun restoreFromBackup(geoPointId: String, name: String, bytes: ByteArray): PointKml
}
