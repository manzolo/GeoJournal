package it.manzolo.geojournal.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.manzolo.geojournal.domain.model.VisitLogEntry

@Entity(
    tableName = "visit_logs",
    foreignKeys = [ForeignKey(
        entity = GeoPointEntity::class,
        parentColumns = ["id"],
        childColumns = ["geo_point_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("geo_point_id"), Index("visited_at")]
)
data class VisitLogEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "geo_point_id") val geoPointId: String,
    @ColumnInfo(name = "visited_at") val visitedAt: Long,
    @ColumnInfo(name = "note") val note: String = ""
)

fun VisitLogEntity.toDomain() = VisitLogEntry(
    id = id,
    geoPointId = geoPointId,
    visitedAt = visitedAt,
    note = note
)

fun VisitLogEntry.toEntity() = VisitLogEntity(
    id = id,
    geoPointId = geoPointId,
    visitedAt = visitedAt,
    note = note
)
