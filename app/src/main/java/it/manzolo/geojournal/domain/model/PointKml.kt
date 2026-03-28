package it.manzolo.geojournal.domain.model

import java.util.UUID

data class PointKml(
    val id: String = UUID.randomUUID().toString(),
    val geoPointId: String,
    val name: String,
    val filePath: String,
    val importedAt: Long = System.currentTimeMillis()
)
