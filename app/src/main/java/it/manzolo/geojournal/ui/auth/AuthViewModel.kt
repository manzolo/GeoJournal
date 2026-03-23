package it.manzolo.geojournal.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.manzolo.geojournal.data.local.datastore.UserPreferencesRepository
import it.manzolo.geojournal.domain.repository.AuthRepository
import it.manzolo.geojournal.domain.repository.GeoPointRepository
import it.manzolo.geojournal.domain.repository.ReminderRepository
import it.manzolo.geojournal.domain.repository.VisitLogRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "GeoJournal_Auth"
private const val FIRESTORE_TIMEOUT_MS = 10_000L

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToMain: Boolean = false
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val userPrefs: UserPreferencesRepository,
    private val geoPointRepository: GeoPointRepository,
    private val reminderRepository: ReminderRepository,
    private val visitLogRepository: VisitLogRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun signInWithGoogle(idToken: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            Log.d(TAG, "signInWithGoogle: avvio Firebase Auth")
            authRepository.signInWithGoogle(idToken)
                .onSuccess { user ->
                    Log.d(TAG, "Firebase Auth OK → uid=${user.uid}")

                    userPrefs.setUserId(user.uid)
                    userPrefs.setIsGuest(false)

                    Log.d(TAG, "migrateGuestPoints: avvio")
                    val migrated = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                        geoPointRepository.migrateGuestPointsToUser(user.uid)
                    }
                    Log.d(TAG, "migrateGuestPoints: completato (migrati=${migrated})")

                    Log.d(TAG, "pullFromFirestore: avvio")
                    val pulled = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                        geoPointRepository.pullFromFirestore()
                    }
                    Log.d(TAG, "pullFromFirestore: completato (pulled=${pulled})")

                    val pulledReminders = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                        reminderRepository.pullFromFirestore()
                    }
                    Log.d(TAG, "pullReminders: completato (pulled=${pulledReminders})")

                    val pulledVisits = withTimeoutOrNull(FIRESTORE_TIMEOUT_MS) {
                        visitLogRepository.pullFromFirestore()
                    }
                    Log.d(TAG, "pullVisitLogs: completato (pulled=${pulledVisits})")

                    _uiState.update { it.copy(isLoading = false, navigateToMain = true) }
                }
                .onFailure { e ->
                    Log.e(TAG, "signInWithGoogle fallito: ${e.message}", e)
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
