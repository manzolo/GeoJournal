package it.manzolo.geojournal.data.backup

data class ShareOptions(
    val includePhotos: Boolean = true,
    val includeTags: Boolean = false,
    val includeKml: Boolean = false,
    val includeNotes: Boolean = false,
    val includeReminders: Boolean = false
)

/** Indica quali contenuti sono effettivamente presenti nel punto da condividere. */
data class ShareAvailability(
    val hasPhotos: Boolean = true,
    val hasTags: Boolean = true,
    val hasKml: Boolean = true,
    val hasNotes: Boolean = true,
    val hasReminders: Boolean = true
)
