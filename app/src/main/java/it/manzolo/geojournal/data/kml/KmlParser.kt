package it.manzolo.geojournal.data.kml

import android.util.Xml
import org.xmlpull.v1.XmlPullParser
import java.io.File

enum class KmlGeometryType { POINT, LINE_STRING, POLYGON }

data class KmlGeometry(
    val name: String,
    val type: KmlGeometryType,
    val coordinates: List<Pair<Double, Double>>  // lon, lat pairs
)

object KmlParser {

    fun parse(file: File): List<KmlGeometry> {
        if (!file.exists()) return emptyList()
        return try {
            file.inputStream().use { stream ->
                val parser = Xml.newPullParser().apply {
                    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                    setInput(stream, null)
                }
                parsePlacemarks(parser)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parsePlacemarks(parser: XmlPullParser): List<KmlGeometry> {
        val result = mutableListOf<KmlGeometry>()
        var eventType = parser.eventType
        var currentName = ""
        var inPlacemark = false
        var inName = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "Placemark" -> { inPlacemark = true; currentName = "" }
                    "name" -> if (inPlacemark) inName = true
                    "Point" -> if (inPlacemark) {
                        val coords = readCoordinatesTag(parser)
                        if (coords.isNotEmpty()) result += KmlGeometry(currentName, KmlGeometryType.POINT, coords)
                    }
                    "LineString" -> if (inPlacemark) {
                        val coords = readCoordinatesTag(parser)
                        if (coords.isNotEmpty()) result += KmlGeometry(currentName, KmlGeometryType.LINE_STRING, coords)
                    }
                    "Polygon" -> if (inPlacemark) {
                        val coords = readPolygonCoordinates(parser)
                        if (coords.isNotEmpty()) result += KmlGeometry(currentName, KmlGeometryType.POLYGON, coords)
                    }
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "Placemark" -> inPlacemark = false
                    "name" -> inName = false
                }
                XmlPullParser.TEXT -> if (inName) currentName = parser.text.trim()
            }
            eventType = parser.next()
        }
        return result
    }

    /** Seeks the next <coordinates> tag within the current element and reads its content. */
    private fun readCoordinatesTag(parser: XmlPullParser): List<Pair<Double, Double>> {
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "coordinates") {
                val text = buildString {
                    while (parser.next().also { eventType = it } == XmlPullParser.TEXT) append(parser.text)
                }
                return parseCoordinates(text)
            }
            if (eventType == XmlPullParser.END_TAG &&
                (parser.name == "Point" || parser.name == "LineString")
            ) break
            eventType = parser.next()
        }
        return emptyList()
    }

    private fun readPolygonCoordinates(parser: XmlPullParser): List<Pair<Double, Double>> {
        var eventType = parser.next()
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "coordinates") {
                val text = buildString {
                    while (parser.next().also { eventType = it } == XmlPullParser.TEXT) append(parser.text)
                }
                return parseCoordinates(text)
            }
            if (eventType == XmlPullParser.END_TAG && parser.name == "Polygon") break
            eventType = parser.next()
        }
        return emptyList()
    }

    private fun parseCoordinates(raw: String): List<Pair<Double, Double>> =
        raw.trim().split(Regex("\\s+")).mapNotNull { token ->
            val parts = token.split(",")
            if (parts.size >= 2) {
                runCatching { Pair(parts[0].toDouble(), parts[1].toDouble()) }.getOrNull()
            } else null
        }
}
