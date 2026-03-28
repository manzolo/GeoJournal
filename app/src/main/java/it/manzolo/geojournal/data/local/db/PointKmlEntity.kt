package it.manzolo.geojournal.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.manzolo.geojournal.domain.model.PointKml

@Entity(
    tableName = "point_kmls",
    foreignKeys = [ForeignKey(
        entity = GeoPointEntity::class,
        parentColumns = ["id"],
        childColumns = ["geo_point_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("geo_point_id")]
)
data class PointKmlEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "geo_point_id") val geoPointId: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "file_path") val filePath: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long = System.currentTimeMillis()
)

fun PointKmlEntity.toDomain() = PointKml(id = id, geoPointId = geoPointId, name = name, filePath = filePath, importedAt = importedAt)
fun PointKml.toEntity() = PointKmlEntity(id = id, geoPointId = geoPointId, name = name, filePath = filePath, importedAt = importedAt)
