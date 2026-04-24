package it.manzolo.geojournal.ui.addedit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.os.Looper
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.automirrored.filled.Notes
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import android.provider.OpenableColumns
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import kotlinx.coroutines.delay
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.launch
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import it.manzolo.geojournal.R
import it.manzolo.geojournal.ui.navigation.Routes
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions
import it.manzolo.geojournal.domain.model.PointKml
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val EMOJI_LIST = listOf(
    // Luoghi e navigazione
    "📍", "🗺️", "🧭", "📌", "🚩", "🏁", "🔍", "🌐",
    // Natura e paesaggi
    "🏔️", "⛰️", "🗻", "🌋", "🏕️", "⛺", "🌊", "🏖️",
    "🏝️", "🌅", "🌄", "🌆", "🌇", "🌉", "🌁", "🌃",
    "🌳", "🌲", "🌴", "🌵", "🍀", "🌿", "🌾", "🍂",
    "🌸", "🌺", "🌻", "🌼", "🌹", "🌷", "💐", "🍄",
    // Edifici e strutture
    "🏠", "🏡", "🏢", "🏣", "🏤", "🏥", "🏦", "🏨",
    "🏩", "🏪", "🏫", "🏬", "🏭", "🏯", "🏰", "🗼",
    "🗽", "🗺️", "⛪", "🕌", "🛕", "🕍", "🏛️", "🏟️",
    // Trasporti
    "🚗", "🚕", "🚌", "🚎", "🏎️", "🚓", "🚑", "🚒",
    "✈️", "🚂", "🚢", "⛵", "🚁", "🛶", "🚲", "🛤️",
    // Cibo e bevande
    "🍕", "🍔", "🌮", "🌯", "🍜", "🍣", "🍦", "🎂",
    "☕", "🍷", "🍺", "🥂", "🧃", "🍹", "🥐", "🍰",
    // Attività e sport
    "🧗", "🚴", "🏊", "⛷️", "🏄", "🎿", "🤿", "🧘",
    "🎯", "⛳", "🎣", "🏕️", "🏋️", "🤸", "🚵", "🧜",
    // Arte e cultura
    "🎨", "🎭", "🎪", "🎡", "🎠", "🎢", "🎵", "🎶",
    "📸", "🎬", "🎤", "🎸", "🎹", "📚", "🖼️", "🎭",
    // Animali
    "🐘", "🦁", "🐬", "🦋", "🐦", "🦅", "🐺", "🦊",
    "🐸", "🐢", "🦎", "🐧", "🦜", "🦩", "🦚", "🐾",
    // Simboli e altro
    "⭐", "🌟", "💫", "✨", "🔥", "💎", "🏺", "🗿",
    "🌞", "🌙", "❄️", "⛅", "🌈", "💧", "🌊", "🎯"
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
    val titleFocusRequester = remember { FocusRequester() }
    var showGpsPreview by remember { mutableStateOf(false) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var permissionLaunchedOnce by remember { mutableStateOf(false) }
    var kmlToRename by remember { mutableStateOf<PointKml?>(null) }
    var kmlToDelete by remember { mutableStateOf<PointKml?>(null) }
    var photoToRemove by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pendingDiscardAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    BackHandler(enabled = uiState.isDirty) {
        showDiscardDialog = true
        pendingDiscardAction = { navController.popBackStack() }
    }

    // Feature 2: auto-focus sul campo Titolo solo per nuovi punti
    LaunchedEffect(Unit) {
        if (!viewModel.isEditMode) {
            delay(150)
            titleFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val gpsPermissionSettingsMsg = stringResource(R.string.addedit_gps_permission_settings)

    // Location permission — apre il dialog preview se concesso; se negato apre in modalità manuale
    val locationPermission = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION,
        onPermissionResult = { granted ->
            // In entrambi i casi apri il dialog: con GPS se concesso, solo mappa se negato
            showGpsPreview = true
        }
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

    // KML file picker
    val kmlLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { u ->
            val displayName = context.contentResolver.query(u, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                cursor.moveToFirst()
                if (nameIndex >= 0) cursor.getString(nameIndex) else null
            } ?: u.lastPathSegment ?: "file.kml"
            viewModel.importKml(u, displayName)
        }
    }

    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) {
            viewModel.onNavigated()
            if (uiState.isSaved && !viewModel.isEditMode) {
                // Nuovo punto: vai alla mappa centrata sul punto appena creato
                navController.navigate(Routes.Map.route) {
                    popUpTo(Routes.Map.route) { inclusive = false }
                    launchSingleTop = true
                }
            } else {
                navController.popBackStack()
            }
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
            hasLocationPermission = locationPermission.status.isGranted,
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
            title = { Text(stringResource(R.string.addedit_delete_title)) },
            text = { Text(stringResource(R.string.addedit_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = viewModel::delete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.point_delete)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::toggleDeleteConfirm) { Text(stringResource(R.string.action_cancel)) }
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
                    stringResource(R.string.addedit_add_photo),
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
                    Text(stringResource(R.string.addedit_camera), style = MaterialTheme.typography.bodyLarge)
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
                    Text(stringResource(R.string.addedit_gallery), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    shape = RoundedCornerShape(50),
                    containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
                )
            }
        },
        topBar = {
            TopAppBar(
                title = { Text(if (viewModel.isEditMode) stringResource(R.string.edit_point_title) else stringResource(R.string.add_point_title)) },
                navigationIcon = {
                    IconButton(onClick = {
                        if (uiState.isDirty) {
                            pendingDiscardAction = { navController.popBackStack() }
                            showDiscardDialog = true
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
                            Icon(Icons.Filled.Check, contentDescription = stringResource(R.string.action_save),
                                tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (viewModel.isEditMode) {
                        IconButton(onClick = viewModel::toggleDeleteConfirm) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.point_delete),
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

        val hasLocation = uiState.latitude != 0.0 || uiState.longitude != 0.0
        var showCoords by remember { mutableStateOf(false) }
        val clipboard = LocalClipboard.current
        val scope = rememberCoroutineScope()
        val mapsUrlNotFound = stringResource(R.string.addedit_maps_url_not_found)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))

            // ── Hero card: emoji + titolo ─────────────────────────────────
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = Color.Transparent,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable { viewModel.toggleEmojiPicker() }
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.linearGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(uiState.emoji, style = MaterialTheme.typography.displaySmall)
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                        value = uiState.title,
                        onValueChange = { v ->
                            viewModel.updateTitle(
                                if (v.isNotEmpty()) v[0].uppercaseChar() + v.drop(1) else v
                            )
                        },
                        label = { Text(stringResource(R.string.addedit_title_hint)) },
                        modifier = Modifier.weight(1f).focusRequester(titleFocusRequester),
                        singleLine = true,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Next,
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )
                }
            }

            // ── Descrizione ───────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = uiState.description,
                        onValueChange = { v ->
                            viewModel.updateDescription(
                                if (v.isNotEmpty()) v[0].uppercaseChar() + v.drop(1) else v
                            )
                        },
                        label = { Text(stringResource(R.string.field_description)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        maxLines = 6,
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Default,
                            capitalization = KeyboardCapitalization.Sentences
                        )
                    )
                }
            }

            // ── Posizione GPS ─────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null,
                            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.addedit_section_location),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f))
                        // ⓘ mostra/nasconde coordinate grezze
                        if (hasLocation) {
                            IconButton(onClick = { showCoords = !showCoords },
                                modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.addedit_coordinates),
                                    modifier = Modifier.size(18.dp),
                                    tint = if (showCoords) MaterialTheme.colorScheme.primary
                                           else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        // Vedi sulla mappa (solo edit mode)
                        if (viewModel.isEditMode && hasLocation) {
                            IconButton(onClick = {
                                if (uiState.isDirty) {
                                    pendingDiscardAction = { navigateToMapFocus(navController, uiState.latitude, uiState.longitude) }
                                    showDiscardDialog = true
                                } else {
                                    navigateToMapFocus(navController, uiState.latitude, uiState.longitude)
                                }
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.addedit_view_on_map),
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        // Incolla URL Google Maps
                        IconButton(
                            onClick = {
                                scope.launch {
                                    val text = clipboard.getClipEntry()
                                        ?.clipData?.getItemAt(0)?.text?.toString() ?: ""
                                    viewModel.importFromMapsUrl(text, mapsUrlNotFound)
                                }
                            },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Filled.ContentPaste,
                                contentDescription = stringResource(R.string.addedit_paste_maps_url),
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Stato posizione
                    if (hasLocation) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.addedit_location_set),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary)
                        }
                        AnimatedVisibility(visible = showCoords) {
                            Text("%.6f, %.6f".format(uiState.latitude, uiState.longitude),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 22.dp))
                        }
                    } else {
                        Text(stringResource(R.string.addedit_location_not_set),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(
                        onClick = {
                            when {
                                locationPermission.status.isGranted -> showGpsPreview = true
                                !permissionLaunchedOnce || locationPermission.status.shouldShowRationale -> {
                                    permissionLaunchedOnce = true
                                    locationPermission.launchPermissionRequest()
                                }
                                else -> {
                                    // Permesso disabilitato permanentemente: apri in modalità solo-mappa
                                    showGpsPreview = true
                                    viewModel.showError(gpsPermissionSettingsMsg)
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.addedit_detect_gps))
                    }
                }
            }

            // ── Foto ──────────────────────────────────────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null,
                            modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.addedit_section_photos),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary)
                    }
                    if (uiState.photoUris.isNotEmpty()) {
                        var selectedPhotoUri by remember { mutableStateOf<String?>(null) }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            maxItemsInEachRow = 3
                        ) {
                            uiState.photoUris.forEachIndexed { index, uri ->
                                val isSelected = uri == selectedPhotoUri
                                Box(
                                    modifier = Modifier
                                        .size(90.dp)
                                        .then(
                                            if (isSelected)
                                                Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp))
                                            else Modifier
                                        )
                                        .clickable {
                                            when {
                                                selectedPhotoUri == null -> selectedPhotoUri = uri
                                                selectedPhotoUri == uri  -> selectedPhotoUri = null
                                                else -> {
                                                    viewModel.swapPhotos(selectedPhotoUri!!, uri)
                                                    selectedPhotoUri = null
                                                }
                                            }
                                        }
                                ) {
                                    AsyncImage(
                                        model = when {
                                            uri.startsWith("/") -> File(uri)
                                            uri.startsWith("content://") -> android.net.Uri.parse(uri)
                                            else -> uri
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    // "×" rimuovi — top-right
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color.Black.copy(alpha = 0.6f))
                                            .clickable { photoToRemove = uri },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.addedit_remove_photo),
                                            tint = Color.White, modifier = Modifier.size(14.dp))
                                    }
                                    if (index == 0) {
                                        // Badge "copertina" — top-left
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(4.dp)
                                                .size(20.dp)
                                                .clip(CircleShape)
                                                .background(Color(0xFFFFB300).copy(alpha = 0.9f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.Star, contentDescription = null,
                                                tint = Color.White, modifier = Modifier.size(12.dp))
                                        }
                                    }
                                    // Overlay selezione — bottom-left (visibile quando la foto è selezionata)
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .padding(4.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.9f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Filled.OpenWith, contentDescription = null,
                                                tint = Color.White, modifier = Modifier.size(14.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                    OutlinedButton(
                        onClick = { showPhotoSourceDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(stringResource(R.string.addedit_add_photo))
                    }
                }
            }

            // ── Dettagli aggiuntivi (collassabile) ───────────────────────
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // Header cliccabile
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.toggleAdditionalDetails() },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (uiState.isAdditionalDetailsExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.addedit_section_additional_details),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        // Badge riassuntivi quando collassata
                        if (!uiState.isAdditionalDetailsExpanded) {
                            if (uiState.notes.isNotBlank()) {
                                Text(stringResource(R.string.addedit_badge_notes), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (uiState.reminders.isNotEmpty()) {
                                Text(stringResource(R.string.addedit_badge_reminders, uiState.reminders.size), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (uiState.kmls.isNotEmpty()) {
                                Text(stringResource(R.string.addedit_badge_kml, uiState.kmls.size), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                            }
                            val visibleTagCount = uiState.tags.count { !it.startsWith("_") }
                            if (visibleTagCount > 0) {
                                Text(
                                    stringResource(R.string.addedit_badge_tags, visibleTagCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                            }
                            if (uiState.rating > 0) {
                                Text(stringResource(R.string.addedit_badge_rating, uiState.rating), style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    AnimatedVisibility(visible = uiState.isAdditionalDetailsExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Spacer(Modifier.height(12.dp))

                            // ── Note personali ────────────────────────────
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Notes, contentDescription = null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.addedit_section_notes),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.notes,
                                onValueChange = { v ->
                                    viewModel.updateNotes(if (v.isNotEmpty()) v[0].uppercaseChar() + v.drop(1) else v)
                                },
                                placeholder = { Text(stringResource(R.string.addedit_notes_hint),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)) },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 2,
                                maxLines = 6,
                                shape = RoundedCornerShape(12.dp),
                                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                            )

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // ── Promemoria ────────────────────────────────
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Notifications, contentDescription = null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.addedit_section_reminders),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (uiState.reminders.isNotEmpty()) {
                                val reminderDateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.ITALIAN) }
                                val reminderTimeFormat = remember { SimpleDateFormat("HH:mm", Locale.ITALIAN) }
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    uiState.reminders.forEach { reminder ->
                                        val timeStr = reminderTimeFormat.format(Date(reminder.startDate))
                                        val dateStr = when (reminder.type) {
                                            ReminderType.DATE_RANGE -> reminder.endDate?.let {
                                                "${reminderDateFormat.format(Date(reminder.startDate))} → ${reminderDateFormat.format(Date(it))} · $timeStr"
                                            } ?: "${reminderDateFormat.format(Date(reminder.startDate))} · $timeStr"
                                            ReminderType.ANNUAL_RECURRING -> "${reminderDateFormat.format(Date(reminder.startDate))} · ${stringResource(R.string.reminder_annual_suffix)} · $timeStr"
                                            ReminderType.SINGLE -> "${reminderDateFormat.format(Date(reminder.startDate))} · $timeStr"
                                        }
                                        InputChip(
                                            selected = false,
                                            onClick = {},
                                            label = { Text(stringResource(R.string.addedit_reminder_chip, reminder.title, dateStr)) },
                                            trailingIcon = {
                                                IconButton(onClick = { viewModel.deleteReminder(reminder) },
                                                    modifier = Modifier.size(18.dp)) {
                                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove),
                                                        modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedButton(
                                onClick = viewModel::toggleReminderSheet,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Notifications, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.addedit_add_reminder))
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // ── File KML ──────────────────────────────────
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Polyline, contentDescription = null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.addedit_section_kml),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (uiState.kmls.isNotEmpty()) {
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    uiState.kmls.forEach { kml ->
                                        InputChip(
                                            selected = false,
                                            onClick = { kmlToRename = kml },
                                            label = { Text(kml.name) },
                                            trailingIcon = {
                                                IconButton(onClick = { kmlToDelete = kml },
                                                    modifier = Modifier.size(18.dp)) {
                                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove),
                                                        modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        )
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                            }
                            OutlinedButton(
                                onClick = { kmlLauncher.launch("*/*") },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Filled.Polyline, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.addedit_import_kml))
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // ── Tag ───────────────────────────────────────
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.AutoMirrored.Filled.Label, contentDescription = null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.field_tags),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = uiState.tagInput,
                                onValueChange = viewModel::updateTagInput,
                                label = { Text(stringResource(R.string.addedit_add_tag)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                shape = RoundedCornerShape(16.dp),
                                trailingIcon = {
                                    IconButton(onClick = viewModel::addTag) {
                                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
                                    }
                                },
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { viewModel.addTag() })
                            )
                            if (uiState.tags.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    uiState.tags.filter { !it.startsWith("_") }.forEach { tag ->
                                        InputChip(
                                            selected = false,
                                            onClick = {},
                                            label = { Text(tag) },
                                            trailingIcon = {
                                                IconButton(onClick = { viewModel.removeTag(tag) },
                                                    modifier = Modifier.size(18.dp)) {
                                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove),
                                                        modifier = Modifier.size(14.dp))
                                                }
                                            }
                                        )
                                    }
                                }
                            }
                            if (uiState.suggestedTags.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    uiState.suggestedTags.forEach { tag ->
                                        SuggestionChip(
                                            onClick = { viewModel.addTagFromSuggestion(tag) },
                                            label = { Text(tag) }
                                        )
                                    }
                                }
                            }

                            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                            // ── Valutazione ───────────────────────────────
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null,
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.addedit_section_rating),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                (1..5).forEach { star ->
                                    IconButton(
                                        onClick = { viewModel.updateRating(star) },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (star <= uiState.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                            contentDescription = stringResource(R.string.addedit_stars, star),
                                            tint = if (star <= uiState.rating) MaterialTheme.colorScheme.primary
                                                   else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(30.dp)
                                        )
                                    }
                                }
                                if (uiState.rating > 0) {
                                    Spacer(Modifier.width(4.dp))
                                    Text("${uiState.rating}/5", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }

                            Spacer(Modifier.height(4.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // --- Discard Changes Confirmation Dialog ---
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text(stringResource(R.string.addedit_discard_title)) },
            text = { Text(stringResource(R.string.addedit_discard_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        showDiscardDialog = false
                        pendingDiscardAction?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.addedit_discard_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // --- Remove Photo Confirmation Dialog ---
    photoToRemove?.let { uri ->
        AlertDialog(
            onDismissRequest = { photoToRemove = null },
            title = { Text(stringResource(R.string.addedit_remove_photo_confirm_title)) },
            text = { Text(stringResource(R.string.addedit_remove_photo_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removePhotoUri(uri)
                        photoToRemove = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { photoToRemove = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // --- Remove KML Confirmation Dialog ---
    kmlToDelete?.let { kml ->
        AlertDialog(
            onDismissRequest = { kmlToDelete = null },
            title = { Text(stringResource(R.string.addedit_remove_kml_confirm_title)) },
            text = { Text(stringResource(R.string.addedit_remove_kml_confirm_body)) },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteKml(kml)
                        kmlToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.action_remove)) }
            },
            dismissButton = {
                TextButton(onClick = { kmlToDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // --- KML Rename Dialog ---
    kmlToRename?.let { kml ->
        RenameKmlDialog(
            currentName = kml.name,
            onConfirm = { newName ->
                viewModel.renameKml(kml, newName)
                kmlToRename = null
            },
            onDismiss = { kmlToRename = null }
        )
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

private fun isPlayServicesAvailable(context: Context): Boolean =
    com.google.android.gms.common.GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS

// ─── GPS Live Preview Dialog ──────────────────────────────────────────────────

@SuppressLint("MissingPermission")
@Composable
private fun GpsPreviewDialog(
    hasLocationPermission: Boolean,
    onLocationConfirmed: (Double, Double) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var location by remember { mutableStateOf<android.location.Location?>(null) }
    val fusedClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    // Posizione selezionata manualmente toccando la mappa (ha priorità sul GPS)
    val manualTapState = remember { mutableStateOf<LatLng?>(null) }
    var manualTapPosition by manualTapState

    // Avvia aggiornamenti continui solo se il permesso è disponibile.
    // Usa FusedLocationProvider se Play Services è aggiornato, altrimenti LocationManager diretto.
    DisposableEffect(Unit) {
        if (!hasLocationPermission) return@DisposableEffect onDispose { }
        if (isPlayServicesAvailable(context)) {
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
        } else {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
            // SAM conversion non funziona su API <29: LocationListener ha 4 metodi astratti.
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) { location = loc }
                override fun onStatusChanged(p: String, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER
            ).forEach { provider ->
                runCatching {
                    lm.requestLocationUpdates(provider, 500L, 0f, listener, Looper.getMainLooper())
                }
            }
            onDispose { lm.removeUpdates(listener) }
        }
    }

    // True dopo che l'utente scrolla la mappa: blocca il ricentramento GPS
    // (il marker continua ad aggiornarsi, ma la camera non segue più)
    var userScrolled by remember { mutableStateOf(false) }
    var firstFix by remember { mutableStateOf(true) }

    // MapView + MapLibreMap + SymbolManager: SymbolManager è disponibile solo dopo setStyle
    val mapView = remember { MapView(context) }
    var mapLibreMap by remember { mutableStateOf<MapLibreMap?>(null) }
    var symbolManager by remember { mutableStateOf<SymbolManager?>(null) }

    // Lifecycle completo MapLibre
    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            symbolManager?.onDestroy()
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // Bootstrap mappa: stile + tap listener + move listener + SymbolManager
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapLibreMap = map
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = true
            // Tap sulla mappa → posizione manuale
            map.addOnMapClickListener { latLng ->
                manualTapState.value = latLng
                true
            }
            // Gesture utente (solo user, non camera programmatica) → blocca auto-follow GPS
            map.addOnMoveListener(object : MapLibreMap.OnMoveListener {
                override fun onMoveBegin(detector: org.maplibre.android.gestures.MoveGestureDetector) {
                    userScrolled = true
                }
                override fun onMove(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
                override fun onMoveEnd(detector: org.maplibre.android.gestures.MoveGestureDetector) {}
            })
            map.setStyle("https://tiles.openfreemap.org/styles/liberty") { style ->
                if (style.getImage(GPS_PIN_IMAGE) == null) {
                    style.addImage(GPS_PIN_IMAGE, makeGpsPinBitmap(context))
                }
                symbolManager = SymbolManager(mapView, map, style).apply {
                    iconAllowOverlap = true
                    iconIgnorePlacement = true
                }
            }
        }
    }

    // Posizione effettiva: manuale > GPS
    val effectivePos: LatLng? = manualTapPosition
        ?: location?.let { LatLng(it.latitude, it.longitude) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column {
                // Mappa con suggerimento sovrapposto
                Box {
                    AndroidView(
                        factory = { mapView },
                        update = {
                            val map = mapLibreMap ?: return@AndroidView
                            val sm = symbolManager ?: return@AndroidView
                            if (manualTapPosition != null) {
                                // Modalità manuale: marker fisso, non seguire il GPS
                                sm.deleteAll()
                                sm.create(
                                    SymbolOptions()
                                        .withLatLng(manualTapPosition!!)
                                        .withIconImage(GPS_PIN_IMAGE)
                                        .withIconAnchor("center")
                                )
                            } else {
                                // Modalità GPS: ricentra solo finché l'utente non ha scrollato
                                location?.let { loc ->
                                    val ll = LatLng(loc.latitude, loc.longitude)
                                    if (firstFix) {
                                        map.moveCamera(CameraUpdateFactory.newLatLngZoom(ll, 15.0))
                                        firstFix = false
                                    } else if (!userScrolled) {
                                        map.moveCamera(CameraUpdateFactory.newLatLng(ll))
                                    }
                                    // Il marker si aggiorna sempre (mostra precisione GPS)
                                    sm.deleteAll()
                                    sm.create(
                                        SymbolOptions()
                                            .withLatLng(ll)
                                            .withIconImage(GPS_PIN_IMAGE)
                                            .withIconAnchor("center")
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    )
                    // Suggerimento in basso sulla mappa
                    Text(
                        text = stringResource(R.string.addedit_map_tap_hint),
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
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when {
                        manualTapPosition != null -> {
                            // Posizione manuale selezionata sulla mappa
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.LocationOn,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    stringResource(R.string.addedit_location_selected),
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
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
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
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(accColor)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    stringResource(R.string.addedit_gps_accuracy, acc.toInt()),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = accColor,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        !hasLocationPermission -> {
                            // Nessun permesso GPS: solo selezione manuale
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Filled.Map,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.addedit_gps_tap_to_select),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                        else -> {
                            // In attesa del primo fix GPS
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.addedit_gps_acquiring),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    val showResumeGps = manualTapPosition != null && hasLocationPermission
                    val showLockHere = location != null && manualTapPosition == null

                    if (showResumeGps || showLockHere) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            if (showResumeGps) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        manualTapPosition = null
                                        userScrolled = false
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.GpsFixed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.addedit_use_gps))
                                }
                            } else if (showLockHere) {
                                OutlinedButton(
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        manualTapPosition = LatLng(location!!.latitude, location!!.longitude)
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.GpsFixed,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(stringResource(R.string.addedit_gps_lock))
                                }
                            }

                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = onDismiss
                            ) {
                                Text(stringResource(R.string.action_cancel))
                            }
                        }
                    } else {
                        OutlinedButton(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = onDismiss
                        ) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            effectivePos?.let { onLocationConfirmed(it.latitude, it.longitude) }
                        },
                        enabled = effectivePos != null
                    ) { Text(stringResource(R.string.addedit_use_location)) }
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
    // startDate as millis — default to today at midnight (time handled separately)
    val todayMillis = remember { Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis }
    var startDateMillis by remember { mutableLongStateOf(todayMillis) }
    var endDateMillis by remember { mutableLongStateOf(todayMillis + 86400000L * 7) }
    var selectedHour by remember { mutableIntStateOf(9) }
    var selectedMinute by remember { mutableIntStateOf(0) }
    val dateFormat = remember { SimpleDateFormat("d MMM yyyy", Locale.ITALIAN) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.addedit_add_reminder)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Title field
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.addedit_reminder_title_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                // Type selector
                Text(stringResource(R.string.addedit_reminder_type), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 3),
                        onClick = { selectedType = ReminderType.SINGLE },
                        selected = selectedType == ReminderType.SINGLE,
                        label = { Text(stringResource(R.string.addedit_reminder_once)) }
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 3),
                        onClick = { selectedType = ReminderType.ANNUAL_RECURRING },
                        selected = selectedType == ReminderType.ANNUAL_RECURRING,
                        label = { Text(stringResource(R.string.addedit_reminder_yearly)) }
                    )
                    SegmentedButton(
                        shape = SegmentedButtonDefaults.itemShape(index = 2, count = 3),
                        onClick = { selectedType = ReminderType.DATE_RANGE },
                        selected = selectedType == ReminderType.DATE_RANGE,
                        label = { Text(stringResource(R.string.addedit_reminder_period)) }
                    )
                }

                // Date picker — simplified: show current and allow increment/decrement days
                Text(stringResource(R.string.addedit_reminder_start_date), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { startDateMillis -= 86400000L }) { Text("‹") }
                    Text(dateFormat.format(Date(startDateMillis)),
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { startDateMillis += 86400000L }) { Text("›") }
                }

                if (selectedType == ReminderType.DATE_RANGE) {
                    Text(stringResource(R.string.addedit_reminder_end_date), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { if (endDateMillis > startDateMillis + 86400000L) endDateMillis -= 86400000L }) { Text("‹") }
                        Text(dateFormat.format(Date(endDateMillis)),
                            style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { endDateMillis += 86400000L }) { Text("›") }
                    }
                }

                // Time picker
                Text(stringResource(R.string.addedit_reminder_time), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { selectedHour = (selectedHour - 1 + 24) % 24 }) { Text("‹") }
                    Text(String.format("%02d", selectedHour), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { selectedHour = (selectedHour + 1) % 24 }) { Text("›") }
                    Text(":", style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { selectedMinute = (selectedMinute - 5 + 60) % 60 }) { Text("‹") }
                    Text(String.format("%02d", selectedMinute), style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { selectedMinute = (selectedMinute + 5) % 60 }) { Text("›") }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isNotBlank()) {
                        val startWithTime = Calendar.getInstance().apply {
                            timeInMillis = startDateMillis
                            set(Calendar.HOUR_OF_DAY, selectedHour)
                            set(Calendar.MINUTE, selectedMinute)
                            set(Calendar.SECOND, 0)
                            set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                        onConfirm(
                            Reminder(
                                id = UUID.randomUUID().toString(),
                                geoPointId = geoPointId,
                                title = title.trim(),
                                startDate = startWithTime,
                                endDate = if (selectedType == ReminderType.DATE_RANGE) endDateMillis else null,
                                type = selectedType
                            )
                        )
                    }
                },
                enabled = title.isNotBlank()
            ) { Text(stringResource(R.string.action_add)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ─── KML Rename Dialog ────────────────────────────────────────────────────────

@Composable
private fun RenameKmlDialog(
    currentName: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.addedit_kml_rename_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.addedit_kml_rename_hint)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            Button(onClick = { if (name.isNotBlank()) onConfirm(name.trim()) },
                enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
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
        title = { Text(stringResource(R.string.addedit_emoji_title)) },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Fixed(8),
                modifier = Modifier.height(360.dp)
            ) {
                items(EMOJI_LIST) { emoji ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(40.dp).clickable { onEmojiSelected(emoji) }
                    ) {
                        Text(text = emoji, style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}

// ─── GPS Preview map marker ──────────────────────────────────────────────────

private const val GPS_PIN_IMAGE = "gps_pin"

/** Pin circolare blu (stile "Sono qui") per il marker della GpsPreviewDialog. */
private fun makeGpsPinBitmap(context: Context): android.graphics.Bitmap {
    val dp = context.resources.displayMetrics.density
    val size = (28 * dp).toInt()
    val bmp = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bmp)
    val cx = size / 2f
    val cy = size / 2f
    val r = size / 2f - 2f * dp
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    // shadow
    paint.color = android.graphics.Color.argb(70, 0, 0, 0)
    canvas.drawCircle(cx + 1f * dp, cy + 1.5f * dp, r, paint)
    // white ring
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(cx, cy, r, paint)
    // blue dot
    paint.color = android.graphics.Color.rgb(33, 150, 243)
    canvas.drawCircle(cx, cy, r - 2.5f * dp, paint)
    return bmp
}
