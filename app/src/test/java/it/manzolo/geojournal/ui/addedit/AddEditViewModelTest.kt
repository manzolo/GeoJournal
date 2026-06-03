package it.manzolo.geojournal.ui.addedit

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import io.mockk.every
import io.mockk.mockk
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.data.notification.ReminderScheduler
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.repository.PointKmlRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.fakes.FakeGeoPointRepository
import it.manzolo.geojournal.util.MainDispatcherRule
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.emptyFlow
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

class AddEditViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var fakeRepo: FakeGeoPointRepository
    private val application: Application = mockk(relaxed = true) {
        every { getString(R.string.error_point_not_found) } returns "Point not found"
    }
    private val userPrefs: UserPreferencesRepository = mockk(relaxed = true)
    private val auth: FirebaseAuth = mockk(relaxed = true)
    private val storage: FirebaseStorage = mockk(relaxed = true)
    private val reminderRepository: ReminderRepository = mockk(relaxed = true) {
        every { observeByGeoPointId(any()) } returns emptyFlow()
    }
    private val scheduler: ReminderScheduler = mockk(relaxed = true)
    private val kmlRepository: PointKmlRepository = mockk(relaxed = true) {
        every { observeByGeoPointId(any()) } returns emptyFlow()
    }

    @Before
    fun setup() {
        fakeRepo = FakeGeoPointRepository()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun kotlinx.coroutines.test.TestScope.collectUiState(viewModel: AddEditViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.uiState.collect {}
        }
    }

    @Test
    fun `loadClonePoint clones fields but resets photos rating reminders and kmls`() = runTest {
        val originalPoint = GeoPoint(
            id = "original_123",
            title = "Ciliegio",
            description = "Un bellissimo ciliegio selvatico",
            latitude = 45.0,
            longitude = 9.0,
            emoji = "🍒",
            tags = listOf("Ciliegio", "Frutta"),
            photoUrls = listOf("photo1.jpg"),
            rating = 4,
            notes = "Le note non devono essere clonate",
            createdAt = Date(),
            updatedAt = Date()
        )
        fakeRepo.save(originalPoint)

        val savedStateHandle = SavedStateHandle(mapOf("pointId" to "new", "cloneFromId" to "original_123"))
        val viewModel = AddEditViewModel(
            application, fakeRepo, userPrefs, auth, storage,
            reminderRepository, scheduler, kmlRepository, savedStateHandle
        )
        collectUiState(viewModel)

        val state = viewModel.uiState.value
        assertEquals("Ciliegio", state.title)
        assertEquals("Un bellissimo ciliegio selvatico", state.description)
        assertEquals("🍒", state.emoji)
        assertEquals(listOf("Ciliegio", "Frutta"), state.tags)
        // Check resets/excludes
        assertTrue(state.photoUris.isEmpty())
        assertEquals(0, state.rating)
        assertEquals("", state.notes)
        assertTrue(state.reminders.isEmpty())
        assertTrue(state.kmls.isEmpty())
        // Coordinates should default to cloned point's coordinates if prefill coordinate parameters are empty
        assertEquals(45.0, state.latitude, 0.0001)
        assertEquals(9.0, state.longitude, 0.0001)
    }

    @Test
    fun `loadClonePoint uses prefilled coordinates if present`() = runTest {
        val originalPoint = GeoPoint(
            id = "original_123",
            title = "Ciliegio",
            description = "Un bellissimo ciliegio selvatico",
            latitude = 45.0,
            longitude = 9.0,
            emoji = "🍒",
            tags = listOf("Ciliegio", "Frutta"),
            createdAt = Date(),
            updatedAt = Date()
        )
        fakeRepo.save(originalPoint)

        val savedStateHandle = SavedStateHandle(mapOf(
            "pointId" to "new",
            "cloneFromId" to "original_123",
            "lat" to "46.1234",
            "lon" to "10.5678"
        ))
        val viewModel = AddEditViewModel(
            application, fakeRepo, userPrefs, auth, storage,
            reminderRepository, scheduler, kmlRepository, savedStateHandle
        )
        collectUiState(viewModel)

        val state = viewModel.uiState.value
        assertEquals("Ciliegio", state.title)
        assertEquals(46.1234, state.latitude, 0.0001)
        assertEquals(10.5678, state.longitude, 0.0001)
    }
}
