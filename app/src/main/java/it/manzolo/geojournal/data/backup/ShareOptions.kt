package it.manzolo.geojournal.data.backup

data class ShareOptions(
    val includePhotos: Boolean = true,
    val includeTags: Boolean = false,
    val includeKml: Boolean = false,
    val includeNotes: Boolean = false,
    val includeReminders: Boolean = false
)
