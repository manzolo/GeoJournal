package it.manzolo.geojournal.ui.onboarding

import io.mockk.coVerify
import io.mockk.mockk
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.util.MainDispatcherRule
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class OnboardingViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var userPrefs: UserPreferencesRepository
    private lateinit var viewModel: OnboardingViewModel

    @Before
    fun setup() {
        userPrefs = mockk(relaxed = true)
        viewModel = OnboardingViewModel(userPrefs)
    }

    @Test
    fun `accept chiama setHasSeenDataOnboarding true`() = runTest {
        viewModel.accept()

        coVerify(exactly = 1) { userPrefs.setHasSeenDataOnboarding(true) }
    }
}
