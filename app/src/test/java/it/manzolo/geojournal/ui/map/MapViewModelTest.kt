package it.manzolo.geojournal.ui.map

import io.mockk.coJustRun
import io.mockk.every
import io.mockk.mockk
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.data.local.datastore.UserPreferences
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.fakes.FakeGeoPointRepository
import it.manzolo.geojournal.util.MainDispatcherRule
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class MapViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeGeoPointRepository
    private lateinit var viewModel: MapViewModel

    private val exporter: GeoPointExporter = mockk(relaxed = true)
    private val userPrefs: UserPreferencesRepository = mockk {
        every { preferences } returns flowOf(UserPreferences())
        coJustRun { setMapPosition(any(), any(), any()) }
    }

    @Before
    fun setup() {
        fakeRepo = FakeGeoPointRepository()
        viewModel = MapViewModel(fakeRepo, exporter, userPrefs)
    }

    private fun makePoint(
        id: String,
        title: String,
        tags: List<String> = emptyList(),
        lat: Double = 45.0,
        lon: Double = 9.0
    ) = GeoPoint(
        id = id, title = title, latitude = lat, longitude = lon, tags = tags
    )

    /** Attiva la raccolta del flow per tutta la durata del test. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun kotlinx.coroutines.test.TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    // ── Test ──────────────────────────────────────────────────────────────────

    @Test
    fun `onPointSelected imposta selectedPoint e isBottomSheetVisible true`() = runTest {
        collectUiState()
        val point = makePoint("1", "Duomo")

        viewModel.onPointSelected(point)

        val state = viewModel.uiState.value
        assertEquals(point, state.selectedPoint)
        assertTrue(state.isBottomSheetVisible)
    }

    @Test
    fun `onBottomSheetDismiss azzera selectedPoint`() = runTest {
        collectUiState()
        val point = makePoint("1", "Duomo")
        viewModel.onPointSelected(point)

        viewModel.onBottomSheetDismiss()

        val state = viewModel.uiState.value
        assertNull(state.selectedPoint)
        assertFalse(state.isBottomSheetVisible)
    }

    @Test
    fun `saveParkingPoint crea nuovo punto con tag _parking`() = runTest {
        collectUiState()

        viewModel.saveParkingPoint(45.5, 9.2, "Parcheggio")

        val parkingPoint = viewModel.uiState.value.points.find { MapViewModel.PARKING_TAG in it.tags }
        assertNotNull(parkingPoint)
        assertEquals("🚗", parkingPoint!!.emoji)
    }

    @Test
    fun `saveParkingPoint con parking esistente imposta showParkingOptions true`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("park1", "Mio Parcheggio", tags = listOf(MapViewModel.PARKING_TAG)))

        viewModel.saveParkingPoint(45.6, 9.3, "Parcheggio")

        val state = viewModel.uiState.value
        assertTrue(state.showParkingOptions)
        assertEquals(45.6, state.pendingParkingLat, 0.001)
        assertEquals(9.3, state.pendingParkingLon, 0.001)
    }

    @Test
    fun `confirmUpdateParking aggiorna posizione del parking esistente`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("park1", "Mio Parcheggio", tags = listOf(MapViewModel.PARKING_TAG), lat = 45.0, lon = 9.0))

        viewModel.saveParkingPoint(45.9, 9.9, "Parcheggio")
        viewModel.confirmUpdateParking()

        val updated = fakeRepo.getById("park1")
        assertNotNull(updated)
        assertEquals(45.9, updated!!.latitude, 0.001)
        assertEquals(9.9, updated.longitude, 0.001)
        assertFalse(viewModel.uiState.value.showParkingOptions)
    }

    @Test
    fun `onMapMoved aggiorna userLatitude userLongitude zoomLevel`() = runTest {
        collectUiState()

        viewModel.onMapMoved(44.5, 11.3, 15.0)

        val state = viewModel.uiState.value
        assertEquals(44.5, state.userLatitude, 0.001)
        assertEquals(11.3, state.userLongitude, 0.001)
        assertEquals(15.0, state.zoomLevel, 0.001)
    }
}
