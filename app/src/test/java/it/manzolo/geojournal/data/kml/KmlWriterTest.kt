package it.manzolo.geojournal.data.kml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KmlWriterTest {

    @Test
    fun `buildTrackKml restituisce null con meno di 2 punti`() {
        assertNull(KmlWriter.buildTrackKml("test", emptyList()))
        assertNull(KmlWriter.buildTrackKml("test", listOf(45.0 to 9.0)))
    }

    @Test
    fun `buildTrackKml restituisce stringa non nulla con 2 o piu punti`() {
        val coords = listOf(45.0 to 9.0, 45.1 to 9.1)
        assertNotNull(KmlWriter.buildTrackKml("test", coords))
    }

    @Test
    fun `buildTrackKml produce XML KML valido`() {
        val kml = KmlWriter.buildTrackKml("traccia", listOf(45.0 to 9.0, 45.1 to 9.1))!!

        assertTrue(kml.startsWith("<?xml"))
        assertTrue(kml.contains("<kml xmlns=\"http://www.opengis.net/kml/2.2\">"))
        assertTrue(kml.contains("</kml>"))
    }

    @Test
    fun `buildTrackKml include LineString con le coordinate in formato lon,lat`() {
        val kml = KmlWriter.buildTrackKml("t", listOf(45.0 to 9.0, 46.0 to 10.0))!!

        assertTrue(kml.contains("<LineString>"))
        assertTrue(kml.contains("</LineString>"))
        // KML usa lon,lat,alt
        assertTrue(kml.contains("9.0,45.0,0"))
        assertTrue(kml.contains("10.0,46.0,0"))
    }

    @Test
    fun `buildTrackKml include Placemark partenza al primo punto`() {
        val kml = KmlWriter.buildTrackKml("t", listOf(45.0 to 9.0, 46.0 to 10.0))!!

        assertTrue(kml.contains("<name>partenza</name>"))
        // Il punto di partenza usa lon,lat del primo elemento
        assertTrue(kml.contains("9.0,45.0,0"))
    }

    @Test
    fun `buildTrackKml include Placemark arrivo all ultimo punto`() {
        val kml = KmlWriter.buildTrackKml("t", listOf(45.0 to 9.0, 46.0 to 10.0))!!

        assertTrue(kml.contains("<name>arrivo</name>"))
        // Il punto di arrivo usa lon,lat dell'ultimo elemento
        assertTrue(kml.contains("10.0,46.0,0"))
    }

    @Test
    fun `buildTrackKml include il nome nel documento`() {
        val kml = KmlWriter.buildTrackKml("Mia Traccia", listOf(45.0 to 9.0, 45.1 to 9.1))!!

        assertTrue(kml.contains("<name>Mia Traccia</name>"))
    }

    @Test
    fun `buildTrackKml effettua l escape dei caratteri XML nel nome`() {
        val kml = KmlWriter.buildTrackKml("Traccia <A&B>", listOf(45.0 to 9.0, 45.1 to 9.1))!!

        assertTrue(kml.contains("Traccia &lt;A&amp;B&gt;"))
        // Il nome grezzo non deve apparire senza escape
        assertTrue(!kml.contains("<A&B>"))
    }

    @Test
    fun `buildTrackKml funziona con molti punti`() {
        val coords = (0..99).map { i -> (45.0 + i * 0.001) to (9.0 + i * 0.001) }
        val kml = KmlWriter.buildTrackKml("lungo", coords)!!

        assertNotNull(kml)
        assertTrue(kml.contains("<name>partenza</name>"))
        assertTrue(kml.contains("<name>arrivo</name>"))
        // 100 punti nel LineString
        val coordCount = kml.substringAfter("<coordinates>")
            .substringBefore("</coordinates>")
            .trim().split(" ").size
        assertEquals(100, coordCount)
    }
}
