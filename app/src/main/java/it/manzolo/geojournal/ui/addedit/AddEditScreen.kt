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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
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
import java.io.File

private val EMOJI_LIST = listOf(
    "📍", "🗺️", "🏔️", "🌊", "🌳", "🏠", "🏰", "🍕", "☕", "🍷",
    "🎨", "🏛️", "⛪", "🏖️", "🌆", "🌄", "🌉", "🛤️", "🏕️", "⛺",
    "🎭", "🎪", "🎡", "🌸", "🌺", "🌻", "🌼", "🌞", "⭐", "🌟",
    "💫", "🔥", "💎", "🎯", "🎵", "📸", "🧗", "🚴", "🏊", "⛷️",
    "🐘", "🦁", "🐬", "🦋", "🌮", "🏺", "🗿", "🏟️", "🛕", "🕌"
)

private fun createCameraUri(context: Context): Uri {
    val tempFile = File.createTempFile("photo_", ".jpg", context.externalCacheDir)
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
    ) { uris -> uris.forEach { viewModel.addPhotoUri(it.toString()) } }

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
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = { Text("Aggiungi foto") },
            text = {
                Column {
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            if (cameraPermission.status.isGranted) {
                                cameraUri = createCameraUri(context)
                                cameraLauncher.launch(cameraUri!!)
                            } else {
                                cameraPermission.launchPermissionRequest()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("📷  Fotocamera") }
                    TextButton(
                        onClick = {
                            showPhotoSourceDialog = false
                            galleryLauncher.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("🖼️  Galleria") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) { Text("Annulla") }
            }
        )
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(56.dp).clickable { viewModel.toggleEmojiPicker() }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(uiState.emoji, style = MaterialTheme.typography.headlineMedium)
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text("Tocca per cambiare emoji",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Posizione", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Tag", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Text("Foto", style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (uiState.photoUris.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxItemsInEachRow = 3
                ) {
                    uiState.photoUris.forEach { uri ->
                        Box(modifier = Modifier.size(90.dp)) {
                            AsyncImage(
                                model = if (uri.startsWith("/")) File(uri) else uri,
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

            Spacer(modifier = Modifier.height(16.dp))
        }
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

    // MapView OSMDroid
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
        }
    }
    var firstFix by remember { mutableStateOf(true) }
    DisposableEffect(mapView) {
        mapView.onResume()
        onDispose { mapView.onPause() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                // Mappa live
                AndroidView(
                    factory = { mapView },
                    update = { mv ->
                        location?.let { loc ->
                            val gp = OsmGeoPoint(loc.latitude, loc.longitude)
                            if (firstFix) {
                                mv.controller.setZoom(17.0)
                                firstFix = false
                            }
                            mv.controller.setCenter(gp)
                            mv.overlays.removeAll { it is Marker }
                            mv.overlays.add(Marker(mv).apply {
                                position = gp
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                            })
                            mv.invalidate()
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                )

                // Info precisione + bottoni
                Column(modifier = Modifier.padding(16.dp)) {
                    if (location != null) {
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
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("Acquisizione GPS…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onDismiss) { Text("Annulla") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                location?.let { onLocationConfirmed(it.latitude, it.longitude) }
                            },
                            enabled = location != null
                        ) { Text("Usa posizione") }
                    }
                }
            }
        }
    }
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
