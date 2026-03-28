package it.manzolo.geojournal.ui.list

import io.mockk.every
import io.mockk.mockk
import it.manzolo.geojournal.data.backup.GeoPointExporter
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.fakes.FakeGeoPointRepository
import kotlinx.coroutines.flow.emptyFlow
import it.manzolo.geojournal.util.MainDispatcherRule
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.Date

class ListViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeGeoPointRepository
    private lateinit var viewModel: ListViewModel

    private val exporter: GeoPointExporter = mockk(relaxed = true)
    private val reminderRepository: ReminderRepository = mockk(relaxed = true) {
        every { observeByGeoPointId(any()) } returns emptyFlow()
    }
    private val kmlRepository: PointKmlRepository = mockk(relaxed = true)

    @Before
    fun setup() {
        fakeRepo = FakeGeoPointRepository()
        viewModel = ListViewModel(fakeRepo, exporter, reminderRepository, kmlRepository)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun makePoint(
        id: String,
        title: String,
        description: String = "",
        tags: List<String> = emptyList(),
        createdAt: Date = Date()
    ) = GeoPoint(
        id = id,
        title = title,
        description = description,
        latitude = 45.0,
        longitude = 9.0,
        tags = tags,
        createdAt = createdAt,
        updatedAt = Date()
    )

    /** Attiva il flow WhileSubscribed per tutta la durata del test. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun kotlinx.coroutines.test.TestScope.collectUiState() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    // ── Test ──────────────────────────────────────────────────────────────────

    @Test
    fun `stato iniziale - lista vuota, isLoading false`() = runTest {
        collectUiState()

        val state = viewModel.uiState.value
        assertTrue(state.points.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `updateQuery filtra punti per titolo`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "Duomo di Milano"))
        fakeRepo.save(makePoint("2", "Parco Sempione"))

        viewModel.updateQuery("Duomo")

        val state = viewModel.uiState.value
        assertEquals(1, state.points.size)
        assertEquals("Duomo di Milano", state.points[0].title)
    }

    @Test
    fun `updateQuery filtra per descrizione`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "Posto A", description = "ottimo aperitivo"))
        fakeRepo.save(makePoint("2", "Posto B", description = "bella passeggiata"))

        viewModel.updateQuery("aperitivo")

        val state = viewModel.uiState.value
        assertEquals(1, state.points.size)
        assertEquals("Posto A", state.points[0].title)
    }

    @Test
    fun `toggleTag aggiunge tag a selectedTags`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "A", tags = listOf("arte")))

        viewModel.toggleTag("arte")

        val state = viewModel.uiState.value
        assertTrue("arte" in state.selectedTags)
    }

    @Test
    fun `toggleTag deseleziona tag gia selezionato`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "A", tags = listOf("arte")))

        viewModel.toggleTag("arte")
        viewModel.toggleTag("arte")

        val state = viewModel.uiState.value
        assertFalse("arte" in state.selectedTags)
    }

    @Test
    fun `filtro tag mostra solo punti con quel tag`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "Museo", tags = listOf("arte", "cultura")))
        fakeRepo.save(makePoint("2", "Parco", tags = listOf("natura")))
        fakeRepo.save(makePoint("3", "Galleria", tags = listOf("arte")))

        viewModel.toggleTag("arte")

        val state = viewModel.uiState.value
        assertEquals(2, state.points.size)
        assertTrue(state.points.all { "arte" in it.tags })
    }

    @Test
    fun `setSortOrder NEWEST ordina per data decrescente`() = runTest {
        collectUiState()
        val t1 = Date(1000L)
        val t2 = Date(2000L)
        fakeRepo.save(makePoint("1", "Vecchio", createdAt = t1))
        fakeRepo.save(makePoint("2", "Nuovo", createdAt = t2))

        viewModel.setSortOrder(SortOrder.NEWEST)

        val points = viewModel.uiState.value.points
        assertEquals("Nuovo", points[0].title)
        assertEquals("Vecchio", points[1].title)
    }

    @Test
    fun `setSortOrder TITLE_AZ ordina alfabeticamente`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "Zara"))
        fakeRepo.save(makePoint("2", "Alfa"))
        fakeRepo.save(makePoint("3", "Milano"))

        viewModel.setSortOrder(SortOrder.TITLE_AZ)

        val points = viewModel.uiState.value.points
        assertEquals("Alfa", points[0].title)
        assertEquals("Milano", points[1].title)
        assertEquals("Zara", points[2].title)
    }

    @Test
    fun `deletePoint rimuove il punto dalla lista`() = runTest {
        collectUiState()
        val point = makePoint("1", "Da eliminare")
        fakeRepo.save(point)
        assertEquals(1, viewModel.uiState.value.points.size)

        viewModel.deletePoint(point)

        assertTrue(viewModel.uiState.value.points.isEmpty())
    }

    @Test
    fun `deleteTag rimuove il tag da tutti i punti`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "A", tags = listOf("arte", "cultura")))
        fakeRepo.save(makePoint("2", "B", tags = listOf("arte")))

        viewModel.deleteTag("arte")

        val points = viewModel.uiState.value.points
        assertTrue(points.none { "arte" in it.tags })
        assertTrue(points.any { "cultura" in it.tags })
    }

    @Test
    fun `allTags aggrega tutti i tag distinti`() = runTest {
        collectUiState()
        fakeRepo.save(makePoint("1", "A", tags = listOf("arte", "natura")))
        fakeRepo.save(makePoint("2", "B", tags = listOf("arte", "cibo")))

        val tags = viewModel.uiState.value.allTags
        assertEquals(listOf("arte", "cibo", "natura"), tags)
    }
}
