package it.manzolo.geojournal.data.photo

import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

object ExifReader {

    data class PhotoExifInfo(
        val dateTaken: String?,
        val cameraModel: String?
    )

    /** Returns null if filePath is not a local file path or file doesn't exist. */
    fun readExif(filePath: String): PhotoExifInfo? {
        if (!filePath.startsWith("/")) return null
        val file = File(filePath)
        if (!file.exists()) return null
        return try {
            val exif = ExifInterface(filePath)
            val rawDate = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
                ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
            val dateTaken = rawDate?.let { parseExifDate(it) }
            val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim()?.takeIf { it.isNotBlank() }
            if (dateTaken == null && model == null) null
            else PhotoExifInfo(dateTaken, model)
        } catch (_: Exception) {
            null
        }
    }

    private fun parseExifDate(raw: String): String? = runCatching {
        val inFmt = SimpleDateFormat("yyyy:MM:dd HH:mm:ss", Locale.getDefault())
        val outFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        outFmt.format(inFmt.parse(raw)!!)
    }.getOrNull()
}
