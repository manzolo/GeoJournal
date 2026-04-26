package it.manzolo.geojournal.data.kml

import android.content.ContentResolver
import android.net.Uri
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream

class TrackImportConverterTest {

    private lateinit var contentResolver: ContentResolver
    private lateinit var uri: Uri

    @Before
    fun setup() {
        contentResolver = mockk()
        uri = mockk()
    }

    @Test
    fun `convert KML simply returns bytes and forces kml extension`() {
        val kmlContent = "<kml>test</kml>"
        val inputStream = ByteArrayInputStream(kmlContent.toByteArray())
        every { contentResolver.openInputStream(uri) } returns inputStream

        val result = TrackImportConverter.convert(uri, "test.KML", contentResolver)

        assertEquals("test.kml", result.newName)
        assertEquals(kmlContent, String(result.kmlContent))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `convert KML with null stream throws exception`() {
        every { contentResolver.openInputStream(uri) } returns null
        TrackImportConverter.convert(uri, "test.kml", contentResolver)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `convert GPX with null stream throws exception`() {
        every { contentResolver.openInputStream(uri) } returns null
        TrackImportConverter.convert(uri, "my_track.gpx", contentResolver)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `convert FIT with null stream throws exception`() {
        every { contentResolver.openInputStream(uri) } returns null
        TrackImportConverter.convert(uri, "my_track.fit", contentResolver)
    }
}
