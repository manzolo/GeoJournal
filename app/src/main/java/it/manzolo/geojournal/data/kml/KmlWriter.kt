package it.manzolo.geojournal.data.kml

object KmlWriter {

    /**
     * Genera un KML con:
     * - LineString con tutti i punti registrati
     * - Point "partenza" al primo punto (→ icona verde in KmlOverlayManager)
     * - Point "arrivo" all'ultimo punto (→ icona rossa in KmlOverlayManager)
     *
     * Ritorna null se [coordinates] ha meno di 2 punti.
     * [coordinates] = lista di (lat, lon).
     */
    fun buildTrackKml(
        name: String,
        coordinates: List<Pair<Double, Double>>
    ): String? {
        if (coordinates.size < 2) return null

        val (startLat, startLon) = coordinates.first()
        val (endLat, endLon) = coordinates.last()
        // KML usa lon,lat,altitude
        val coordsString = coordinates.joinToString(" ") { (lat, lon) -> "$lon,$lat,0" }

        return buildString {
            appendLine("""<?xml version="1.0" encoding="UTF-8"?>""")
            appendLine("""<kml xmlns="http://www.opengis.net/kml/2.2">""")
            appendLine("""  <Document>""")
            appendLine("""    <name>${escapeXml(name)}</name>""")
            appendLine("""    <Placemark>""")
            appendLine("""      <name>${escapeXml(name)}</name>""")
            appendLine("""      <LineString>""")
            appendLine("""        <coordinates>$coordsString</coordinates>""")
            appendLine("""      </LineString>""")
            appendLine("""    </Placemark>""")
            appendLine("""    <Placemark>""")
            appendLine("""      <name>partenza</name>""")
            appendLine("""      <Point>""")
            appendLine("""        <coordinates>$startLon,$startLat,0</coordinates>""")
            appendLine("""      </Point>""")
            appendLine("""    </Placemark>""")
            appendLine("""    <Placemark>""")
            appendLine("""      <name>arrivo</name>""")
            appendLine("""      <Point>""")
            appendLine("""        <coordinates>$endLon,$endLat,0</coordinates>""")
            appendLine("""      </Point>""")
            appendLine("""    </Placemark>""")
            appendLine("""  </Document>""")
            append("""</kml>""")
        }
    }

    private fun escapeXml(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
