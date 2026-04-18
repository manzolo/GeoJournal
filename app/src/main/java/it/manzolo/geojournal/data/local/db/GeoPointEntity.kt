package it.manzolo.geojournal.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import it.manzolo.geojournal.domain.model.GeoPoint
import java.util.Date

@Entity(tableName = "geo_points")
data class GeoPointEntity(
    @PrimaryKey
    @ColumnInfo(name = "id") val id: String,

    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "description") val description: String = "",
    @ColumnInfo(name = "latitude") val latitude: Double,
    @ColumnInfo(name = "longitude") val longitude: Double,

    // Liste serializzate con separatore "|"
    @ColumnInfo(name = "tags") val tags: String = "",
    @ColumnInfo(name = "photo_urls") val photoUrls: String = "",

    @ColumnInfo(name = "emoji") val emoji: String = "📍",

    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "owner_id") val ownerId: String = "",
    @ColumnInfo(name = "is_shared") val isShared: Boolean = false,

    // Flag per sincronizzazione Firestore (Phase 11)
    @ColumnInfo(name = "synced_to_firestore") val syncedToFirestore: Boolean = false,

    @ColumnInfo(name = "rating") val rating: Int = 0,

    @ColumnInfo(name = "is_archived") val isArchived: Boolean = false,

    @ColumnInfo(name = "notes") val notes: String = "",

    @ColumnInfo(name = "is_favorite") val isFavorite: Boolean = false
)

// ─── Mapper Domain ↔ Entity ──────────────────────────────────────────────────

fun GeoPointEntity.toDomain(): GeoPoint = GeoPoint(
    id = id,
    title = title,
    description = description,
    latitude = latitude,
    longitude = longitude,
    tags = if (tags.isEmpty()) emptyList() else tags.split("|").filter { it.isNotEmpty() },
    photoUrls = if (photoUrls.isEmpty()) emptyList() else photoUrls.split("|").filter { it.isNotEmpty() },
    emoji = emoji,
    createdAt = Date(createdAt),
    updatedAt = Date(updatedAt),
    ownerId = ownerId,
    isShared = isShared,
    rating = rating,
    isArchived = isArchived,
    notes = notes,
    isFavorite = isFavorite
)

fun GeoPoint.toEntity(syncedToFirestore: Boolean = false): GeoPointEntity = GeoPointEntity(
    id = id,
    title = title,
    description = description,
    latitude = latitude,
    longitude = longitude,
    tags = tags.joinToString("|"),
    photoUrls = photoUrls.joinToString("|"),
    emoji = emoji,
    createdAt = createdAt.time,
    updatedAt = updatedAt.time,
    ownerId = ownerId,
    isShared = isShared,
    syncedToFirestore = syncedToFirestore,
    rating = rating,
    isArchived = isArchived,
    notes = notes,
    isFavorite = isFavorite
)
