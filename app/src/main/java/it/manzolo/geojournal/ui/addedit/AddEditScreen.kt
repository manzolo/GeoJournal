package it.manzolo.geojournal.ui.addedit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import android.view.MotionEvent
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Card
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val EMOJI_LIST = listOf(
    "📍", "🗺️", "🏔️", "🌊", "🌳", "🏠", "🏰", "🍕", "☕", "🍷",
    "🎨", "🏛️", "⛪", "🏖️", "🌆", "🌄", "🌉", "🛤️", "🏕️", "⛺",
    "🎭", "🎪", "🎡", "🌸", "🌺", "🌻", "🌼", "🌞", "⭐", "🌟",
    "💫", "🔥", "💎", "🎯", "🎵", "📸", "🧗", "🚴", "🏊", "⛷️",
    "🐘", "🦁", "🐬", "🦋", "🌮", "🏺", "🗿", "🏟️", "🛕", "🕌"
)

private fun createCameraUri(context: Context): Uri {
    val dir = context.externalCacheDir ?: context.cacheDir
    val tempFile = File.createTempFile("photo_", ".jpg", dir)
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", tempFile)
}

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
    var showGpsPreview by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }

    // Location permission — apre il dialog preview se concesso
    val locationPermission = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION,
        onPermissionResult = { granted -> if (granted) showGpsPreview = true }
    )

    // Camera
    var cameraUri by remember { mutableStateOf<Uri?>(null) }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success -> if (success) cameraUri?.toString()?.let { viewModel.addPhotoUri(it) } }
    val cameraPermission = rememberPermissionState(
        Manifest.permission.CAMERA,
        onPermissionResult = { granted ->
            if (granted) {
                cameraUri = createCameraUri(context)
                cameraLauncher.launch(cameraUri!!)
            }
        }
    )

    // Gallery
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        uris.forEach { uri ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.addPhotoUri(uri.toString())
        }
    }

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

    // GPS preview dialog
    if (showGpsPreview) {
        GpsPreviewDialog(
            onLocationConfirmed = { lat, lon ->
                viewModel.updateLocation(lat, lon)
                showGpsPreview = false
            },
            onDismiss = { showGpsPreview = false }
        )
    }

    if (uiState.showEmojiPicker) {
        EmojiPickerDialog(
            onEmojiSelected = viewModel::selectEmoji,
            onDismiss = viewModel::toggleEmojiPicker
        )
    }

    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::toggleDeleteConfirm,
            title = { Text("Elimina punto") },
            text = { Text("Sei sicuro di voler eliminare questo punto? L'operazione non è reversibile.") },
            confirmButton = {
                TextButton(
                    onClick = viewModel::delete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Elimina") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::toggleDeleteConfirm) { Text("Annulla") }
            }
        )
    }

    if (showPhotoSourceDialog) {
        ModalBottomSheet(
            onDismissRequest = { showPhotoSourceDialog = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "Aggiungi foto",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                OutlinedButton(
                    onClick = {
                        showPhotoSourceDialog = false
                        if (cameraPermission.status.isGranted) {
                            cameraUri = createCameraUri(context)
                            cameraLauncher.launch(cameraUri!!)
                        } else {
                            cameraPermission.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Icon(Icons.Filled.Camera, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Fotocamera", style = MaterialTheme.typography.bodyLarge)
                }
                OutlinedButton(
                    onClick = {
                        showPhotoSourceDialog = false
                        galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                ) {
                    Icon(Icons.Filled.Image, contentDescription = null)
                    Spacer(Modifier.width(12.dp))
                    Text("Galleria foto", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) "Modifica punto" else "Nuovo punto") },
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier
                        .size(72.dp)
                        .clickable { viewModel.toggleEmojiPicker() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(uiState.emoji, style = MaterialTheme.typography.displaySmall)
                    }
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        "Scegli un simbolo",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        "Tocca per cambiare l'icona",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // --- Titolo ---
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { v ->
                    viewModel.updateTitle(
                        if (v.isNotEmpty()) v[0].uppercaseChar() + v.drop(1) else v
                    )
                },
                label = { Text("Titolo *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            // --- Descrizione ---
            OutlinedTextField(
                value = uiState.description,
                onValueChange = { v ->
                    viewModel.updateDescription(
                        if (v.isNotEmpty()) v[0].uppercaseChar() + v.drop(1) else v
                    )
                },
                label = { Text("Descrizione") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next,
                    capitalization = KeyboardCapitalization.Sentences
                )
            )

            // --- GPS ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Posizione",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    if (uiState.latitude != 0.0 || uiState.longitude != 0.0) {
                        Text("%.5f, %.5f".format(uiState.latitude, uiState.longitude),
                            style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("Posizione non impostata",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Vedi sulla mappa (solo in edit mode con coordinate valide)
                if (viewModel.isEditMode &&
                    (uiState.latitude != 0.0 || uiState.longitude != 0.0)) {
                    IconButton(
                        onClick = {
                            navigateToMapFocus(navController, uiState.latitude, uiState.longitude)
                        }
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = "Vedi sulla mappa",
                            tint = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }
                OutlinedButton(
                    onClick = {
                        if (locationPermission.status.isGranted) showGpsPreview = true
                        else locationPermission.launchPermissionRequest()
                    }
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null,
                        modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Rileva GPS")
                }
            }

            // --- Tag ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Label,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Tag",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
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
                                    Icon(Icons.Filled.Close, contentDescription = "Rimuovi",
                                        modifier = Modifier.size(14.dp))
                                }
                            }
                        )
                    }
                }
            }

            // --- Foto ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Foto",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (uiState.photoUris.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    uiState.photoUris.forEach { uri ->
                        Box(modifier = Modifier.size(90.dp)) {
                            AsyncImage(
                                model = when {
                                    uri.startsWith("/") -> File(uri)
                                    uri.startsWith("content://") -> android.net.Uri.parse(uri)
                                    else -> uri
                                },
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(4.dp)
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.55f))
                                    .clickable { viewModel.removePhotoUri(uri) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Rimuovi foto",
                                    tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }
            OutlinedButton(
                onClick = { showPhotoSourceDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Aggiungi foto")
            }

            // --- Promemoria ---
            run {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Notifications,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Promemoria",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                if (uiState.reminders.isNotEmpty()) {
                    val reminderDateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.ITALIAN) }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        uiState.reminders.forEach { reminder ->
                            val dateStr = when (reminder.type) {
                                ReminderType.DATE_RANGE -> reminder.endDate?.let {
                                    "${reminderDateFormat.format(Date(reminder.startDate))} → ${reminderDateFormat.format(Date(it))}"
                                } ?: reminderDateFormat.format(Date(reminder.startDate))
                                ReminderType.ANNUAL_RECURRING -> "${reminderDateFormat.format(Date(reminder.startDate))} · ogni anno"
                                ReminderType.SINGLE -> reminderDateFormat.format(Date(reminder.startDate))
                            }
                            InputChip(
                                selected = false,
                                onClick = {},
                                label = { Text("🔔 ${reminder.title} · $dateStr") },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.deleteReminder(reminder) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = "Rimuovi",
                                            modifier = Modifier.size(14.dp))
                                    }
                                }
                            )
                        }
                    }
                }
                OutlinedButton(
                    onClick = viewModel::toggleReminderSheet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Aggiungi promemoria")
                }
            }

            // --- Valutazione ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Valutazione",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { star ->
                    IconButton(
                        onClick = { viewModel.updateRating(star) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = if (star <= uiState.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = "$star stelle",
                            tint = if (star <= uiState.rating) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                if (uiState.rating > 0) {
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "${uiState.rating}/5",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // --- Add Reminder Dialog ---
    if (uiState.showReminderSheet) {
        AddReminderDialog(
            geoPointId = viewModel.getPointIdForReminder(),
            defaultTitle = uiState.title,
            onConfirm = { reminder ->
                viewModel.addReminder(reminder)
                viewModel.toggleReminderSheet()
            },
            onDismiss = viewModel::toggleReminderSheet
        )
    }
}

// ─── Navigazione verso la mappa con focus ────────────────────────────────────

private fun navigateToMapFocus(navController: NavController, lat: Double, lon: Double) {
    it.manzolo.geojournal.ui.map.MapViewModel.FocusRequest.send(lat, lon)
    navController.navigate(it.manzolo.geojournal.ui.navigation.Routes.Map.route) {
        popUpTo(it.manzolo.geojournal.ui.navigation.Routes.Map.route) { inclusive = false }
        launchSingleTop = true
    }
}

// ─── GPS Live Preview Dialog ──────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun GpsPreviewDialog(
    onLocationConfirmed: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var location by remember { mutableStateOf<android.location.Location?>(null) }
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Posizione selezionata manualmente toccando la mappa (ha priorità sul GPS)
    val manualTapState = remember { mutableStateOf<OsmGeoPoint?>(null) }
    var manualTapPosition by manualTapState

    // Avvia aggiornamenti continui — si fermano al dismiss (onDispose)
    DisposableEffect(Unit) {
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
            .setMinUpdateIntervalMillis(500L)
            .build()
        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location = it }
            }
        }
        fusedClient.requestLocationUpdates(request, callback, Looper.getMainLooper())
        onDispose { fusedClient.removeLocationUpdates(callback) }
    }

    // Overlay tap: qualsiasi tocco sulla mappa imposta la posizione manuale
    val tapOverlay = remember {
        object : org.osmdroid.views.overlay.Overlay() {
            override fun onSingleTapConfirmed(e: MotionEvent, mapView: MapView): Boolean {
                val gp = mapView.projection.fromPixels(e.x.toInt(), e.y.toInt())
                manualTapState.value = OsmGeoPoint(gp.latitude, gp.longitude)
                return true
            }
        }
    }

    // MapView OSMDroid — tapOverlay aggiunto una sola volta
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            overlays.add(tapOverlay)   // index 0; i marker vengono inseriti a index 0 → tapOverlay sempre a index superiore → priorità eventi
        }
    }
    var firstFix by remember { mutableStateOf(true) }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    // Posizione effettiva: manuale > GPS
    val effectivePos: OsmGeoPoint? = manualTapPosition
        ?: location?.let { OsmGeoPoint(it.latitude, it.longitude) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Mappa con suggerimento sovrapposto
                Box {
                    AndroidView(
                        factory = { mapView },
                        update = { mv ->
                            if (manualTapPosition != null) {
                                // Modalità manuale: marker fisso, non seguire il GPS
                                mv.overlays.removeAll { it is Marker }
                                mv.overlays.add(0, Marker(mv).apply {
                                    position = manualTapPosition!!
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    infoWindow = null
                                })
                                mv.invalidate()
                            } else {
                                // Modalità GPS: marker segue la posizione
                                location?.let { loc ->
                                    val gp = OsmGeoPoint(loc.latitude, loc.longitude)
                                    if (firstFix) {
                                        mv.controller.setZoom(17.0)
                                        mv.controller.setCenter(gp)
                                        firstFix = false
                                    } else {
                                        mv.controller.setCenter(gp)
                                    }
                                    mv.overlays.removeAll { it is Marker }
                                    mv.overlays.add(0, Marker(mv).apply {
                                        position = gp
                                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                        infoWindow = null
                                    })
                                    mv.invalidate()
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    // Suggerimento in basso sulla mappa
                    Text(
                        text = "Tocca la mappa per spostare il punto",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                            .background(Color(0x99000000), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }

                // Info posizione + bottoni
                Column(modifier = Modifier.padding(16.dp)) {
                    when {
                        manualTapPosition != null -> {
                            // Posizione manuale
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    "Posizione selezionata sulla mappa",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "%.5f, %.5f".format(
                                    manualTapPosition!!.latitude,
                                    manualTapPosition!!.longitude
                                ),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        location != null -> {
                            // Posizione GPS
                            val acc = location!!.accuracy
                            val accColor = when {
                                acc <= 10f -> Color(0xFF4CAF50)
                                acc <= 50f -> Color(0xFFFFC107)
                                else -> Color(0xFFF44336)
                            }
                            Text(
                                "%.5f, %.5f".format(location!!.latitude, location!!.longitude),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accColor)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    "Precisione: ±${acc.toInt()} m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accColor
                                )
                            }
                        }
                        else -> {
                            // In attesa del primo fix GPS
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Acquisizione GPS…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // "Usa GPS" visibile solo in modalità manuale
                        if (manualTapPosition != null) {
                            TextButton(onClick = { manualTapPosition = null }) {
                                Icon(
                                    Icons.Filled.GpsFixed,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text("Usa GPS")
                            }
                            Spacer(Modifier.width(4.dp))
                        }
                        TextButton(onClick = onDismiss) { Text("Annulla") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                effectivePos?.let { onLocationConfirmed(it.latitude, it.longitude) }
                            },
                            enabled = effectivePos != null
                        ) { Text("Usa posizione") }
                    }
                }
            }
        }
    }
}

// ─── Add Reminder Dialog ──────────────────────────────────────────────────────

@Composable
private fun AddReminderDialog(
    geoPointId: String,
    defaultTitle: String,
    onConfirm: (Reminder) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(defaultTitle) }
    var selectedType by remember { mutableStateOf(ReminderType.SINGLE) }
    // startDate as millis — default to today
    val todayMillis = remember { Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis }
    var startDateMillis by remember { mutableLongStateOf(todayMillis) }
    var endDateMillis by remember { mutableLongStateOf(todayMillis + 86400000L * 7) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.ITALIAN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiungi promemoria") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Titolo promemoria") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Type selector
                Text("Tipo", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        onClick = { selectedType = ReminderType.SINGLE },
                        selected = selectedType == ReminderType.SINGLE,
                        label = { Text("Una volta") }
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        onClick = { selectedType = ReminderType.ANNUAL_RECURRING },
                        selected = selectedType == ReminderType.ANNUAL_RECURRING,
                        label = { Text("Ogni anno") }
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        onClick = { selectedType = ReminderType.DATE_RANGE },
                        selected = selectedType == ReminderType.DATE_RANGE,
                        label = { Text("Periodo") }
                    )
                }

                // Date picker — simplified: show current and allow increment/decrement days
                Text("Data inizio", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { startDateMillis -= 86400000L }) { Text("‹") }
                    Text(dateFormat.format(Date(startDateMillis)),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { startDateMillis += 86400000L }) { Text("›") }
                }

                if (selectedType == ReminderType.DATE_RANGE) {
                    Text("Data fine", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { if (endDateMillis > startDateMillis + 86400000L) endDateMillis -= 86400000L }) { Text("‹") }
                        Text(dateFormat.format(Date(endDateMillis)),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { endDateMillis += 86400000L }) { Text("›") }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        onConfirm(
                            Reminder(
                                id = UUID.randomUUID().toString(),
                                geoPointId = geoPointId,
                                title = title.trim(),
                                startDate = startDateMillis,
                                endDate = if (selectedType == ReminderType.DATE_RANGE) endDateMillis else null,
                                type = selectedType
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) { Text("Aggiungi") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}

// ─── Emoji Picker ─────────────────────────────────────────────────────────────

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
                        modifier = Modifier.size(52.dp).clickable { onEmojiSelected(emoji) }
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Annulla") }
        }
    )
}
