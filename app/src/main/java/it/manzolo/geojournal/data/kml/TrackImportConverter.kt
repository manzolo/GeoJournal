package it.manzolo.geojournal.data.kml

import android.content.ContentResolver
import android.net.Uri
import android.util.Xml
import com.garmin.fit.Decode
import com.garmin.fit.MesgBroadcaster
import com.garmin.fit.RecordMesg
import com.garmin.fit.RecordMesgListener
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.util.Date

data class TrackImportResult(
    val newName: String,
    val kmlContent: ByteArray
)

class TrackImportConverter {

    fun convert(uri: Uri, displayName: String, contentResolver: ContentResolver): TrackImportResult {
        val lowerName = displayName.lowercase()
        val extension = lowerName.substringAfterLast('.', "")

        return when (extension) {
            "gpx" -> parseGpx(uri, displayName, contentResolver)
            "fit" -> parseFit(uri, displayName, contentResolver)
            else -> parseKml(uri, displayName, contentResolver) // Pass-through for KML or fallback
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
                    val latStr = parser.getAttributeValue(null, "lat")
                    val lonStr = parser.getAttributeValue(null, "lon")
                    if (latStr != null && lonStr != null) {
                        val lat = latStr.toDoubleOrNull()
                        val lon = lonStr.toDoubleOrNull()
                        if (lat != null && lon != null) {
                            trackPoints.add(lat to lon)
                        }
                    }
                }
                eventType = parser.next()
            }
        } ?: throw IllegalArgumentException("Impossibile leggere il file GPX")

        if (trackPoints.isEmpty()) {
            throw IllegalArgumentException("Nessun punto traccia trovato nel file GPX")
        }

        val baseName = displayName.substringBeforeLast('.')
        val kmlString = KmlWriter.buildTrackKml(baseName, trackPoints)
            ?: throw IllegalArgumentException("Impossibile generare la traccia KML (sono necessari almeno 2 punti)")

        return TrackImportResult("$baseName.kml", kmlString.toByteArray(Charsets.UTF_8))
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
                    val lat = latSemicircles * (180.0 / Math.pow(2.0, 31.0))
                    val lon = lonSemicircles * (180.0 / Math.pow(2.0, 31.0))
                    trackPoints.add(lat to lon)
                }
            })
            
            try {
                // The Garmin FIT SDK handles decoding the stream and broadcasting messages
                mesgBroadcaster.run(stream)
            } catch (e: Exception) {
                throw IllegalArgumentException("Errore durante il parsing del file FIT: ${e.message}")
            }
        } ?: throw IllegalArgumentException("Impossibile leggere il file FIT")

        if (trackPoints.isEmpty()) {
            throw IllegalArgumentException("Nessun punto traccia (GPS) trovato nel file FIT")
        }

        val baseName = displayName.substringBeforeLast('.')
        val kmlString = KmlWriter.buildTrackKml(baseName, trackPoints)
            ?: throw IllegalArgumentException("Impossibile generare la traccia KML (sono necessari almeno 2 punti)")

        return TrackImportResult("$baseName.kml", kmlString.toByteArray(Charsets.UTF_8))
    }
}
