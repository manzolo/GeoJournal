package it.manzolo.geojournal.domain.repository

import android.net.Uri
import it.manzolo.geojournal.domain.model.PointKml
import kotlinx.coroutines.flow.Flow

interface PointKmlRepository {
    fun observeByGeoPointId(geoPointId: String): Flow<List<PointKml>>
    suspend fun getByGeoPointId(geoPointId: String): List<PointKml>
    suspend fun getAll(): List<PointKml>
    suspend fun importKml(uri: Uri, geoPointId: String, displayName: String): PointKml
    suspend fun importTrackContent(geoPointId: String, name: String, content: ByteArray): PointKml
    suspend fun deleteKml(kml: PointKml)
    suspend fun deleteByGeoPointId(geoPointId: String)
    /** Restore a KML file from raw bytes (used by BackupManager import). id is preserved for dedup. */
    suspend fun restoreFromBackup(id: String, geoPointId: String, name: String, bytes: ByteArray): PointKml
    suspend fun renameKml(kml: PointKml, newName: String)
    /** Insert an already-prepared PointKml record into the DB (used after a new point is saved). */
    suspend fun insertKml(kml: PointKml)
    /** Save a KML from generated string content (used by LocationTrackingService). */
    suspend fun saveKml(geoPointId: String, name: String, content: String): PointKml
}
