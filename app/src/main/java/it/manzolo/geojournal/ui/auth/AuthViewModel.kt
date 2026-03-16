package it.manzolo.geojournal.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.AuthRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToMain: Boolean = false,
    val showEmailForm: Boolean = false,
    val isSignUpMode: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferencesRepository,
    private val geoPointRepository: GeoPointRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    userPrefs.setUserId(user.uid)
                    userPrefs.setIsGuest(false)
                    geoPointRepository.migrateGuestPointsToUser(user.uid)
                    _uiState.update { it.copy(isLoading = false, navigateToMain = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun signInWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.signInWithEmail(email, password)
                .onSuccess { user ->
                    userPrefs.setUserId(user.uid)
                    userPrefs.setIsGuest(false)
                    geoPointRepository.migrateGuestPointsToUser(user.uid)
                    _uiState.update { it.copy(isLoading = false, navigateToMain = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun createUserWithEmail(email: String, password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            authRepository.createUserWithEmail(email, password)
                .onSuccess { user ->
                    userPrefs.setUserId(user.uid)
                    userPrefs.setIsGuest(false)
                    geoPointRepository.migrateGuestPointsToUser(user.uid)
                    _uiState.update { it.copy(isLoading = false, navigateToMain = true) }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(isLoading = false, error = e.message) }
                }
        }
    }

    fun continueAsGuest() {
        viewModelScope.launch {
            userPrefs.setIsGuest(true)
            _uiState.update { it.copy(navigateToMain = true) }
        }
    }

    fun toggleEmailForm() {
        _uiState.update { it.copy(showEmailForm = !it.showEmailForm, error = null) }
    }

    fun toggleSignUpMode() {
        _uiState.update { it.copy(isSignUpMode = !it.isSignUpMode, error = null) }
    }

    fun setError(message: String) {
        _uiState.update { it.copy(error = message) }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun onNavigated() {
        _uiState.update { it.copy(navigateToMain = false) }
    }
}
