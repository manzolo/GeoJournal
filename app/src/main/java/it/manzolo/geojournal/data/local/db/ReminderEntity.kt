package it.manzolo.geojournal.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType

@Entity(
    tableName = "reminders",
    foreignKeys = [ForeignKey(
        entity = GeoPointEntity::class,
        parentColumns = ["id"],
        childColumns = ["geo_point_id"],
        onDelete = ForeignKey.CASCADE
    )],
    indices = [Index("geo_point_id")]
)
data class ReminderEntity(
    @PrimaryKey @ColumnInfo(name = "id") val id: String,
    @ColumnInfo(name = "geo_point_id") val geoPointId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "start_date") val startDate: Long,
    @ColumnInfo(name = "end_date") val endDate: Long? = null,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "is_active") val isActive: Boolean = true,
    @ColumnInfo(name = "notification_id") val notificationId: Int
)

fun ReminderEntity.toDomain() = Reminder(
    id = id,
    geoPointId = geoPointId,
    title = title,
    startDate = startDate,
    endDate = endDate,
    type = ReminderType.valueOf(type),
    isActive = isActive,
    notificationId = notificationId
)

fun Reminder.toEntity() = ReminderEntity(
    id = id,
    geoPointId = geoPointId,
    title = title,
    startDate = startDate,
    endDate = endDate,
    type = type.name,
    isActive = isActive,
    notificationId = notificationId
)
