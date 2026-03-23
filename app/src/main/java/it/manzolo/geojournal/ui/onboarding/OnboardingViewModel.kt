package it.manzolo.geojournal.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    fun accept() {
        viewModelScope.launch {
            userPrefs.setHasSeenDataOnboarding(true)
        }
    }
}
