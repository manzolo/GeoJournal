package it.manzolo.geojournal.domain.model

import java.util.Date
import java.util.UUID

data class GeoPoint(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val description: String = "",
    val latitude: Double,
    val longitude: Double,
    val tags: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val emoji: String = "📍",
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val ownerId: String = "",
    val isShared: Boolean = false,
    val rating: Int = 0,  // 0 = non valutato, 1-5 stelle
    val isArchived: Boolean = false,
    val notes: String = "",  // note personali private, mai esportate in .geoj
    val isFavorite: Boolean = false  // preferito locale, mai esportato in .geoj o Firestore
)
