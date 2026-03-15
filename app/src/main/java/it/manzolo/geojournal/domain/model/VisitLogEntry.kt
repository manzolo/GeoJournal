package it.manzolo.geojournal.domain.model

import java.util.UUID

data class VisitLogEntry(
    val id: String = UUID.randomUUID().toString(),
    val geoPointId: String,
    val visitedAt: Long,   // epoch millis
    val note: String = ""
)
