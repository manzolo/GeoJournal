package it.manzolo.geojournal.ui.auth

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import it.manzolo.geojournal.R
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    onNavigateToMain: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    LaunchedEffect(uiState.navigateToMain) {
        if (uiState.navigateToMain) {
            viewModel.onNavigated()
            onNavigateToMain()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    fun handleCredentialResult(result: GetCredentialResponse) {
        val credential = result.credential
        if (credential is CustomCredential &&
            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
        ) {
            try {
                val tokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                viewModel.signInWithGoogle(tokenCredential.idToken)
            } catch (e: GoogleIdTokenParsingException) {
                Log.e("GeoJournal_Auth", "Token parsing failed", e)
                viewModel.setError("Errore nel token Google: ${e.message}")
            }
        } else {
            viewModel.setError("Credenziale non supportata (${credential.type})")
        }
    }

    fun launchGoogleSignIn() {
        coroutineScope.launch {
            val clientId = context.getString(R.string.default_web_client_id)
            try {
                val googleIdOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(false)
                    .setServerClientId(clientId)
                    .build()
                val result = credentialManager.getCredential(
                    context,
                    GetCredentialRequest.Builder().addCredentialOption(googleIdOption).build()
                )
                handleCredentialResult(result)
            } catch (e: GetCredentialCancellationException) {
                // utente ha annullato
            } catch (e: NoCredentialException) {
                Log.d("GeoJournal_Auth", "No credential via OneTap, trying SignInWithGoogle fallback")
                try {
                    val signInOption = GetSignInWithGoogleOption.Builder(clientId).build()
                    val result = credentialManager.getCredential(
                        context,
                        GetCredentialRequest.Builder().addCredentialOption(signInOption).build()
                    )
                    handleCredentialResult(result)
                } catch (e2: GetCredentialCancellationException) {
                    // utente ha annullato
                } catch (e2: GetCredentialException) {
                    Log.e("GeoJournal_Auth", "Fallback failed: ${e2.message}", e2)
                    viewModel.setError("Accesso Google fallito: ${e2.message ?: e2.type}")
                }
            } catch (e: GetCredentialException) {
                viewModel.setError("Accesso Google fallito: ${e.message}")
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Spacer(modifier = Modifier.height(80.dp))

                // Logo
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .background(MaterialTheme.colorScheme.surface, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "🗺️", style = MaterialTheme.typography.displayLarge)
                }
                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.auth_app_subtitle),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Accedi con Google
                Button(
                    onClick = { launchGoogleSignIn() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(28.dp),
                    enabled = !uiState.isLoading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                ) {
                    Text(stringResource(R.string.login_with_google), style = MaterialTheme.typography.titleMedium)
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (uiState.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Ospite
                TextButton(
                    onClick = { viewModel.continueAsGuest() },
                    enabled = !uiState.isLoading
                ) {
                    Text(
                        text = stringResource(R.string.auth_guest_button),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
                Text(
                    text = stringResource(R.string.auth_guest_note),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
