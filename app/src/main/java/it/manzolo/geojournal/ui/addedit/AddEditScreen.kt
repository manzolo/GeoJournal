package it.manzolo.geojournal.ui.addedit

import android.Manifest
import android.content.Context
import android.location.LocationManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import kotlinx.coroutines.launch

private val EMOJI_LIST = listOf(
    "📍", "🗺️", "🏔️", "🌊", "🌳", "🏠", "🏰", "🍕", "☕", "🍷",
    "🎨", "🏛️", "⛪", "🏖️", "🌆", "🌄", "🌉", "🛤️", "🏕️", "⛺",
    "🎭", "🎪", "🎡", "🌸", "🌺", "🌻", "🌼", "🌞", "⭐", "🌟",
    "💫", "🔥", "💎", "🎯", "🎵", "📸", "🧗", "🚴", "🏊", "⛷️",
    "🐘", "🦁", "🐬", "🦋", "🌮", "🏺", "🗿", "🏟️", "🛕", "🕌"
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class,
    ExperimentalLayoutApi::class)
@Composable
fun AddEditScreen(
    navController: NavController,
    viewModel: AddEditViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

    // Navigazione dopo salvataggio o eliminazione
    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) {
            viewModel.onNavigated()
            navController.popBackStack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    // Emoji picker dialog
    if (uiState.showEmojiPicker) {
        EmojiPickerDialog(
            onEmojiSelected = viewModel::selectEmoji,
            onDismiss = viewModel::toggleEmojiPicker
        )
    }

    // Conferma eliminazione
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::toggleDeleteConfirm,
            title = { Text("Elimina punto") },
            text = { Text("Sei sicuro di voler eliminare questo punto? L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::delete,
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::toggleDeleteConfirm) { Text("Annulla") }
            }
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Text(if (viewModel.isEditMode) "Modifica punto" else "Nuovo punto")
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp).padding(end = 8.dp))
                    } else {
                        IconButton(onClick = {
                            keyboardController?.hide()
                            viewModel.save()
                        }) {
                            Icon(Icons.Filled.Check, contentDescription = "Salva",
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (viewModel.isEditMode) {
                        IconButton(onClick = viewModel::toggleDeleteConfirm) {
                            Icon(Icons.Filled.Delete, contentDescription = "Elimina",
                                tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        if (uiState.isLoading && viewModel.isEditMode) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // --- Emoji ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier
                        .size(56.dp)
                        .clickable { viewModel.toggleEmojiPicker() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(uiState.emoji, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Tocca per cambiare emoji",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // --- Titolo ---
            OutlinedTextField(
                value = uiState.title,
                onValueChange = viewModel::updateTitle,
                label = { Text("Titolo *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            // --- Descrizione ---
            OutlinedTextField(
                value = uiState.description,
                onValueChange = viewModel::updateDescription,
                label = { Text("Descrizione") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )

            // --- GPS ---
            Text(
                text = "Posizione",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (uiState.latitude != 0.0 || uiState.longitude != 0.0) {
                        Text(
                            text = "%.5f, %.5f".format(uiState.latitude, uiState.longitude),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    } else {
                        Text(
                            text = "Posizione non impostata",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = {
                        if (locationPermission.status.isGranted) {
                            getLastLocation(context)?.let { (lat, lon) ->
                                viewModel.updateLocation(lat, lon)
                            }
                        } else {
                            locationPermission.launchPermissionRequest()
                        }
                    }
                ) {
                    Icon(
                        Icons.Filled.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rileva GPS")
                }
            }

            // Rileva automaticamente dopo permesso concesso (se posizione non impostata)
            LaunchedEffect(locationPermission.status.isGranted) {
                if (locationPermission.status.isGranted && uiState.latitude == 0.0 && uiState.longitude == 0.0) {
                    getLastLocation(context)?.let { (lat, lon) ->
                        viewModel.updateLocation(lat, lon)
                    }
                }
            }

            // --- Tag ---
            Text(
                text = "Tag",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = uiState.tagInput,
                onValueChange = viewModel::updateTagInput,
                label = { Text("Aggiungi tag") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    IconButton(onClick = viewModel::addTag) {
                        Icon(Icons.Filled.Add, contentDescription = "Aggiungi")
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { viewModel.addTag() })
            )
            if (uiState.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    uiState.tags.forEach { tag ->
                        InputChip(
                            selected = false,
                            onClick = {},
                            label = { Text(tag) },
                            trailingIcon = {
                                IconButton(
                                    onClick = { viewModel.removeTag(tag) },
                                    modifier = Modifier.size(18.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Rimuovi",
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // --- Foto (placeholder Fase successiva) ---
            Text(
                text = "Foto",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(
                onClick = {
                    scope.launch {
                        snackbarHostState.showSnackbar("Funzione foto disponibile prossimamente")
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null,
                    modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Aggiungi foto")
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun EmojiPickerDialog(
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Scegli emoji") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                modifier = Modifier.height(280.dp)
            ) {
                items(EMOJI_LIST) { emoji ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(52.dp)
                            .clickable { onEmojiSelected(emoji) }
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

private fun getLastLocation(context: Context): Pair<Double, Double>? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        location?.let { it.latitude to it.longitude }
    } catch (_: SecurityException) {
        null
    }
}
