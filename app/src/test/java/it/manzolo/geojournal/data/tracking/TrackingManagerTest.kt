package it.manzolo.geojournal.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TrackingManagerTest {

    private lateinit var manager: TrackingManager

    @Before
    fun setup() {
        manager = TrackingManager()
    }

    @Test
    fun `stato iniziale non sta tracciando`() {
        val state = manager.state.value
        assertFalse(state.isTracking)
        assertNull(state.geoPointId)
        assertEquals(0, state.pointCount)
    }

    @Test
    fun `startTracking imposta isTracking true e geoPointId`() {
        manager.startTracking("point-1")

        val state = manager.state.value
        assertTrue(state.isTracking)
        assertEquals("point-1", state.geoPointId)
        assertEquals(0, state.pointCount)
    }

    @Test
    fun `startTracking azzera coordinate precedenti`() {
        manager.startTracking("point-1")
        manager.addCoordinate(45.0, 9.0)
        manager.addCoordinate(45.1, 9.1)

        // Nuovo tracking sullo stesso punto
        manager.startTracking("point-2")
        assertEquals(0, manager.state.value.pointCount)
    }

    @Test
    fun `addCoordinate incrementa pointCount`() {
        manager.startTracking("point-1")

        manager.addCoordinate(45.0, 9.0)
        assertEquals(1, manager.state.value.pointCount)

        manager.addCoordinate(45.1, 9.1)
        assertEquals(2, manager.state.value.pointCount)

        manager.addCoordinate(45.2, 9.2)
        assertEquals(3, manager.state.value.pointCount)
    }

    @Test
    fun `stopTrackingAndCollect restituisce geoPointId e snapshot coordinate`() {
        manager.startTracking("point-1")
        manager.addCoordinate(45.0, 9.0)
        manager.addCoordinate(45.1, 9.1)
        manager.addCoordinate(45.2, 9.2)

        val (geoPointId, coords) = manager.stopTrackingAndCollect()

        assertEquals("point-1", geoPointId)
        assertEquals(3, coords.size)
        assertEquals(45.0 to 9.0, coords[0])
        assertEquals(45.2 to 9.2, coords[2])
    }

    @Test
    fun `stopTrackingAndCollect azzera lo stato`() {
        manager.startTracking("point-1")
        manager.addCoordinate(45.0, 9.0)

        manager.stopTrackingAndCollect()

        val state = manager.state.value
        assertFalse(state.isTracking)
        assertNull(state.geoPointId)
        assertEquals(0, state.pointCount)
    }

    @Test
    fun `stopTrackingAndCollect senza avvio restituisce null e lista vuota`() {
        val (geoPointId, coords) = manager.stopTrackingAndCollect()
        assertNull(geoPointId)
        assertTrue(coords.isEmpty())
    }

    @Test
    fun `isTrackingFor restituisce true solo per il punto attivo`() {
        manager.startTracking("point-1")

        assertTrue(manager.isTrackingFor("point-1"))
        assertFalse(manager.isTrackingFor("point-2"))
    }

    @Test
    fun `isTrackingFor restituisce false quando non sta tracciando`() {
        assertFalse(manager.isTrackingFor("point-1"))
    }

    @Test
    fun `snapshot di stopTrackingAndCollect e' immutabile rispetto a modifiche successive`() {
        manager.startTracking("point-1")
        manager.addCoordinate(45.0, 9.0)

        val (_, coords) = manager.stopTrackingAndCollect()

        // Riavvia tracking e aggiungi altri punti: lo snapshot precedente non deve cambiare
        manager.startTracking("point-2")
        manager.addCoordinate(46.0, 10.0)

        assertEquals(1, coords.size)
    }
}
