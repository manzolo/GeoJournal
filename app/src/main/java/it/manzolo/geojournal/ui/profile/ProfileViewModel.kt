package it.manzolo.geojournal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfileUiState(
    val displayName: String = "",
    val email: String = "",
    val photoUrl: String? = null,
    val isGuest: Boolean = false,
    val isPro: Boolean = false,
    val isLoggedIn: Boolean = false,
    val isDarkTheme: Boolean = false,
    val navigateToLogin: Boolean = false
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                authRepository.currentUser,
                userPrefs.preferences
            ) { user, prefs ->
                ProfileUiState(
                    displayName = user?.displayName ?: "",
                    email = user?.email ?: "",
                    photoUrl = user?.photoUrl?.toString(),
                    isGuest = user == null && prefs.isGuest,
                    isPro = prefs.isPro,
                    isLoggedIn = user != null,
                    isDarkTheme = prefs.isDarkTheme
                )
            }.collect { state ->
                _uiState.update { state }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            authRepository.signOut()
            userPrefs.setUserId("")
            userPrefs.setIsGuest(false)
            _uiState.update { it.copy(navigateToLogin = true) }
        }
    }

    fun setDarkTheme(isDark: Boolean) {
        viewModelScope.launch { userPrefs.setDarkTheme(isDark) }
    }

    fun onNavigated() {
        _uiState.update { it.copy(navigateToLogin = false) }
    }
}
