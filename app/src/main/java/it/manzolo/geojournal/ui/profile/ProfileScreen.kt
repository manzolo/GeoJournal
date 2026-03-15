package it.manzolo.geojournal.ui.profile

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.runtime.collectAsState
import androidx.navigation.NavController
import it.manzolo.geojournal.ui.navigation.Routes

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.navigateToLogin) {
        if (uiState.navigateToLogin) {
            viewModel.onNavigated()
            navController.navigate(Routes.Login.route) {
                popUpTo(0) { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth()
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // Banner avviso dati locali (solo guest)
            if (uiState.isGuest) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "⚠️ I tuoi dati non sono al sicuro",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Stai usando la modalità locale. Se disinstalli l'app perderai tutti i tuoi punti. Accedi per salvarli sul cloud.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                navController.navigate(Routes.Login.route) {
                                    popUpTo(0) { inclusive = true }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Accedi ora")
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Avatar e info utente
            Text(
                text = if (uiState.isLoggedIn) "👤" else "🕵️",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoggedIn) {
                Text(
                    text = uiState.displayName.ifBlank { "Utente" },
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = uiState.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (uiState.isGuest) {
                Text(
                    text = "Modalità locale",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Nessun account collegato",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "Profilo",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Accedi per sincronizzare il tuo diario\nsu tutti i dispositivi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Bottone principale (login vs sign-out)
            if (uiState.isLoggedIn) {
                OutlinedButton(
                    onClick = { viewModel.signOut() },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Esci dall'account")
                }
            } else {
                Button(
                    onClick = {
                        navController.navigate(Routes.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Text("Accedi")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pro upgrade (Fase 7)
            Button(
                onClick = { /* Fase 7: avviare billing flow */ },
                modifier = Modifier.fillMaxWidth(0.7f),
                enabled = !uiState.isPro,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text(if (uiState.isPro) "Pro attivo ⭐" else "Passa a Pro ⭐")
            }
        }
    }
}
