package it.manzolo.geojournal.data.kml

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesgListener
import org.xmlpull.v1.XmlPullParser

data class TrackImportResult(
    val newName: String,
    val kmlContent: ByteArray
)

object TrackImportConverter {

    // FIT protocol: coordinates stored as semicircles (2^31 per 180°)
    private const val SEMICIRCLES_TO_DEGREES = 180.0 / 2147483648.0

    fun convert(uri: Uri, displayName: String, contentResolver: ContentResolver): TrackImportResult {
        val extension = displayName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "gpx" -> parseGpx(uri, displayName, contentResolver)
            "fit" -> parseFit(uri, displayName, contentResolver)
            else -> parseKml(uri, displayName, contentResolver)
        }
    }

    private fun parseKml(uri: Uri, displayName: String, contentResolver: ContentResolver): TrackImportResult {
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("Impossibile leggere il file")
        val baseName = displayName.substringBeforeLast('.')
        return TrackImportResult("$baseName.kml", bytes)
    }

    private fun parseGpx(uri: Uri, displayName: String, contentResolver: ContentResolver): TrackImportResult {
        val trackPoints = mutableListOf<Pair<Double, Double>>()
        contentResolver.openInputStream(uri)?.use { stream ->
            val parser = Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                setInput(stream, null)
            }
            var eventType = parser.eventType
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG && parser.name == "trkpt") {
                    val lat = parser.getAttributeValue(null, "lat")?.toDoubleOrNull()
                    val lon = parser.getAttributeValue(null, "lon")?.toDoubleOrNull()
                    if (lat != null && lon != null) trackPoints.add(lat to lon)
                }
                eventType = parser.next()
            }
        } ?: throw IllegalArgumentException("Impossibile leggere il file GPX")

        if (trackPoints.isEmpty()) throw IllegalArgumentException("Nessun punto traccia trovato nel file GPX")
        return buildResult(displayName, trackPoints)
    }

    private fun parseFit(uri: Uri, displayName: String, contentResolver: ContentResolver): TrackImportResult {
        val trackPoints = mutableListOf<Pair<Double, Double>>()
        contentResolver.openInputStream(uri)?.use { stream ->
            val decode = Decode()
            val mesgBroadcaster = MesgBroadcaster(decode)
            mesgBroadcaster.addListener(RecordMesgListener { mesg ->
                val latSemicircles = mesg.positionLat
                val lonSemicircles = mesg.positionLong
                if (latSemicircles != null && lonSemicircles != null) {
                    trackPoints.add(
                        latSemicircles * SEMICIRCLES_TO_DEGREES to
                        lonSemicircles * SEMICIRCLES_TO_DEGREES
                    )
                }
            })
            try {
                mesgBroadcaster.run(stream)
            } catch (e: Exception) {
                throw IllegalArgumentException("Errore durante il parsing del file FIT: ${e.message}")
            }
        } ?: throw IllegalArgumentException("Impossibile leggere il file FIT")

        if (trackPoints.isEmpty()) throw IllegalArgumentException("Nessun punto traccia (GPS) trovato nel file FIT")
        return buildResult(displayName, trackPoints)
    }

    private fun buildResult(displayName: String, points: List<Pair<Double, Double>>): TrackImportResult {
        val baseName = displayName.substringBeforeLast('.')
        val kml = KmlWriter.buildTrackKml(baseName, points)
            ?: throw IllegalArgumentException("Impossibile generare la traccia KML (sono necessari almeno 2 punti)")
        return TrackImportResult("$baseName.kml", kml.toByteArray(Charsets.UTF_8))
    }
}
