package it.manzolo.geojournal.domain.repository

import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<FirebaseUser?>
    val isLoggedIn: Boolean
    suspend fun signInWithGoogle(idToken: String): Result<FirebaseUser>
    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser>
    suspend fun signOut()
}
