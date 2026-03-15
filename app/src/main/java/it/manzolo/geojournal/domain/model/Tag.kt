package it.manzolo.geojournal.domain.model

import java.util.UUID

data class Tag(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val colorArgb: Long = 0xFF4CAF50L  // ForestGreenLight di default
)
