package it.manzolo.geojournal.ui.map

import android.Manifest
import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import android.location.LocationManager
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Polyline
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import it.manzolo.geojournal.R
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.ui.components.PointBottomSheet
import it.manzolo.geojournal.ui.components.ShareOptionsDialog
import it.manzolo.geojournal.ui.navigation.Routes
import kotlin.math.abs
import kotlin.math.pow
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.tileprovider.tilesource.XYTileSource
import org.osmdroid.util.MapTileIndex
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

// ESRI usa Z/Y/X anziché Z/X/Y — necessita tile source custom
private object EsriSatelliteTileSource : OnlineTileSourceBase(
    "ESRI_Satellite", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        val z = MapTileIndex.getZoom(pMapTileIndex)
        val x = MapTileIndex.getX(pMapTileIndex)
        val y = MapTileIndex.getY(pMapTileIndex)
        return "${baseUrl}$z/$y/$x"
    }
}

private enum class MapLayer { ROAD, TOPO, SATELLITE }

private val mapLayerSources = mapOf(
    MapLayer.ROAD to { TileSourceFactory.MAPNIK },
    MapLayer.TOPO to {
        XYTileSource(
            "OpenTopoMap", 0, 17, 256, ".png",
            arrayOf("https://tile.opentopomap.org/")
        )
    },
    MapLayer.SATELLITE to { EsriSatelliteTileSource }
)

private data class MapCluster(
    val centerLat: Double,
    val centerLon: Double,
    val points: List<GeoPoint>
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // COARSE + FINE insieme: su Android 12+ il sistema mostra un unico dialog
    // con la scelta "Precisa / Approssimativa". Su Android <12 equivale a chiedere solo FINE.
    val locationPermissions = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    )
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)
    val snackbarHostState = remember { SnackbarHostState() }
    val locationUnavailableText = stringResource(R.string.map_location_unavailable)

    // Richiedi permesso posizione proattivamente all'avvio (prima apertura o dopo revoca)
    LaunchedEffect(Unit) {
        if (!locationPermission.status.isGranted) {
            locationPermissions.launchMultiplePermissionRequest()
        }
    }

    // Richiedi permesso POST_NOTIFICATIONS su Android 13+ (necessario per i reminder)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val notificationPermission = rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
        LaunchedEffect(Unit) {
            if (!notificationPermission.status.isGranted) {
                notificationPermission.launchPermissionRequest()
            }
        }
    }
    val parkingPointTitle = stringResource(R.string.map_parking_point_title)

    // Feature 6: snackbar conferma parcheggio
    val parkingSnackbarText = uiState.parkingSnackbarRes?.let { stringResource(it) }
    LaunchedEffect(uiState.parkingSnackbarRes) {
        parkingSnackbarText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearParkingSnackbar()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.shareFileEvent.collect { file ->
            val uri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/x-geojournal-point"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Condividi punto"))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToPointEvent.collect { pointId ->
            navController.navigate(Routes.PointDetail.createRoute(pointId))
        }
    }

    val trackingSavedTitle = uiState.pendingTrackSavedToTitle
    val trackingSavedText = trackingSavedTitle?.let { stringResource(R.string.tracking_saved_to, it) }
    LaunchedEffect(trackingSavedTitle) {
        trackingSavedText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearTrackingSavedSnackbar()
        }
    }

    // Dialog opzioni di condivisione
    if (uiState.pendingSharePoint != null) {
        ShareOptionsDialog(
            availability = uiState.pendingShareAvailability,
            onConfirm = { message, options -> viewModel.onShareConfirmed(message, options) },
            onDismiss = viewModel::onShareDismissed
        )
    }

    // Ref aggiornabile per avere sempre i punti correnti nel listener di zoom
    val pointsRef = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    // Punti del cluster troppo vicini da separare visivamente → mostra il picker
    val clusterPickerRef = remember { mutableStateOf<List<GeoPoint>?>(null) }
    // Overlay posizione + direzione utente (piazzato dal FAB MyLocation)
    val myLocationOverlayRef = remember { mutableStateOf<MyLocationOverlay?>(null) }
    // KML overlay attivi nella sessione (kmlId → lista overlay OSMDroid)
    val kmlOverlaysRef = remember { mutableStateOf<Map<String, List<org.osmdroid.views.overlay.Overlay>>>(emptyMap()) }

    // Feature 5: ripristina la camera salvata nel ViewModel (persiste tra navigazioni).
    // Al primo avvio usa lastKnownLocation (sincrona, nessun salto visivo).
    // Nelle aperture successive usa la posizione salvata da onMapMoved.
    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        val savedPosition = OsmGeoPoint(uiState.userLatitude, uiState.userLongitude)
        val isFirstOpen = !uiState.hasAppliedInitialZoom
        val hasLocationPerm = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val lastKnown = if (isFirstOpen && hasLocationPerm) getLastKnownLocation(context) else null
        val initialCenter = lastKnown?.let { OsmGeoPoint(it.latitude, it.longitude) } ?: savedPosition
        val initialZoom = if (lastKnown != null) 15.0 else uiState.zoomLevel
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
            controller.setZoom(initialZoom)
            controller.setCenter(initialCenter)
        }
    }

    var currentLayer by remember { mutableStateOf(MapLayer.ROAD) }
    val layerRoadLabel = stringResource(R.string.map_layer_road)
    val layerTopoLabel = stringResource(R.string.map_layer_topo)
    val layerSatLabel = stringResource(R.string.map_layer_satellite)
    val isFirstLayerRender = remember { mutableStateOf(true) }
    var showLayerLabel by remember { mutableStateOf(false) }
    var layerLabelText by remember { mutableStateOf("") }
    LaunchedEffect(currentLayer) {
        if (isFirstLayerRender.value) {
            isFirstLayerRender.value = false
            return@LaunchedEffect
        }
        mapView.setTileSource(mapLayerSources[currentLayer]!!.invoke())
        mapView.invalidate()
        val label = when (currentLayer) {
            MapLayer.ROAD -> layerRoadLabel
            MapLayer.TOPO -> layerTopoLabel
            MapLayer.SATELLITE -> layerSatLabel
        }
        layerLabelText = label
        showLayerLabel = true
        delay(1500L)
        showLayerLabel = false
    }

    // Listener di zoom e scroll: ri-clustera al cambio zoom, salva camera (throttled 100ms)
    LaunchedEffect(Unit) {
        var lastZoom = -1
        var lastSaveMs = 0L
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent): Boolean {
                val now = System.currentTimeMillis()
                if (now - lastSaveMs > 100) {
                    viewModel.onMapMoved(
                        mapView.mapCenter.latitude,
                        mapView.mapCenter.longitude,
                        mapView.zoomLevelDouble
                    )
                    lastSaveMs = now
                }
                return false
            }
            override fun onZoom(event: ZoomEvent): Boolean {
                val z = mapView.zoomLevelDouble.toInt()
                val now = System.currentTimeMillis()
                if (now - lastSaveMs > 100) {
                    viewModel.onMapMoved(
                        mapView.mapCenter.latitude,
                        mapView.mapCenter.longitude,
                        mapView.zoomLevelDouble
                    )
                    lastSaveMs = now
                }
                if (z != lastZoom) {
                    lastZoom = z
                    updateClusteredMarkers(
                        mapView, pointsRef.value, context,
                        onMarkerClick = { viewModel.onPointSelected(it) },
                        onClusterTooClose = { clusterPickerRef.value = it }
                    )
                }
                return false
            }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            mapView.overlays.clear()
            mapView.onDetach()
        }
    }

    // Bussola: registra il sensore solo mentre l'overlay "Sono qui" è visibile.
    // TYPE_ROTATION_VECTOR → azimuth accurato anche con il telefono inclinato.
    DisposableEffect(myLocationOverlayRef.value) {
        val overlay = myLocationOverlayRef.value
            ?: return@DisposableEffect onDispose { }
        val sm = context.getSystemService(Context.SENSOR_SERVICE)
            as android.hardware.SensorManager
        val sensor = sm.getDefaultSensor(android.hardware.Sensor.TYPE_ROTATION_VECTOR)
            ?: return@DisposableEffect onDispose { }
        var smoothAzimuth = 0f
        val listener = object : android.hardware.SensorEventListener {
            override fun onSensorChanged(event: android.hardware.SensorEvent) {
                val rm  = FloatArray(9)
                val rm2 = FloatArray(9)
                android.hardware.SensorManager.getRotationMatrixFromVector(rm, event.values)
                // Remap per telefono in portrait (asse Z → alto schermo)
                android.hardware.SensorManager.remapCoordinateSystem(
                    rm, android.hardware.SensorManager.AXIS_X,
                    android.hardware.SensorManager.AXIS_Z, rm2
                )
                val or = FloatArray(3)
                android.hardware.SensorManager.getOrientation(rm2, or)
                val raw = ((Math.toDegrees(or[0].toDouble()).toFloat() + 360f) % 360f)
                // Filtro passa-basso con gestione wraparound 359°→1°
                val diff = ((raw - smoothAzimuth + 540f) % 360f) - 180f
                smoothAzimuth = (smoothAzimuth + 0.12f * diff + 360f) % 360f
                overlay.azimuth = smoothAzimuth
                mapView.postInvalidate()
            }
            override fun onAccuracyChanged(s: android.hardware.Sensor, a: Int) {}
        }
        sm.registerListener(
            listener, sensor,
            android.hardware.SensorManager.SENSOR_DELAY_UI
        )
        onDispose { sm.unregisterListener(listener) }
    }

    LaunchedEffect(uiState.points, uiState.searchQuery) {
        val displayPoints = if (uiState.searchQuery.isBlank()) uiState.points else uiState.searchResults
        pointsRef.value = displayPoints
        updateClusteredMarkers(
            mapView, displayPoints, context,
            onMarkerClick = { viewModel.onPointSelected(it) },
            onClusterTooClose = { clusterPickerRef.value = it }
        )
        if (!uiState.hasAppliedInitialZoom) {
            viewModel.markInitialFitDone()
        }
    }

    // KML overlays: aggiunge/rimuove in base allo stato di sessione
    LaunchedEffect(uiState.kmlItems) {
        val current = kmlOverlaysRef.value.toMutableMap()
        // Rimuovi overlay per KML disattivati o rimossi dalla lista
        val activeIds = uiState.kmlItems.filter { it.isActive }.map { it.kml.id }.toSet()
        val toRemove = current.keys.filter { it !in activeIds }
        toRemove.forEach { id ->
            current[id]?.let { KmlOverlayManager.removeFromMap(mapView, it) }
            current.remove(id)
        }
        // Aggiungi overlay per KML attivati di nuovo
        val existingIds = current.keys
        uiState.kmlItems.filter { it.isActive && it.kml.id !in existingIds }.forEach { item ->
            val geometries = viewModel.parseKml(item.kml.id)
            if (geometries.isNotEmpty()) {
                val overlays = KmlOverlayManager.buildOverlays(mapView, geometries)
                KmlOverlayManager.addToMap(mapView, overlays)
                current[item.kml.id] = overlays
            }
        }
        kmlOverlaysRef.value = current
    }

    // Focus su punto specifico (es. da Lista): centra + auto-seleziona il punto
    LaunchedEffect(uiState.focusTarget) {
        uiState.focusTarget?.let { target ->
            mapView.controller.animateTo(OsmGeoPoint(target.lat, target.lon))
            mapView.controller.setZoom(target.zoom)
            target.pointId?.let { id ->
                uiState.points.find { it.id == id }?.let { viewModel.onPointSelected(it) }
            }
            viewModel.clearFocusTarget()
        }
    }

    BackHandler(enabled = uiState.isSearchOpen) { viewModel.closeSearch() }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Search Bar stile Google Maps — pill collassabile in cima
        MapSearchBar(
            isOpen = uiState.isSearchOpen,
            query = uiState.searchQuery,
            results = uiState.searchResults,
            userLat = uiState.userLatitude,
            userLon = uiState.userLongitude,
            hasUserLocation = uiState.hasUserLocation,
            onOpen = viewModel::openSearch,
            onClose = viewModel::closeSearch,
            onQueryChange = viewModel::updateSearchQuery,
            onResultClick = { point ->
                viewModel.closeSearch()
                viewModel.onPointSelected(point)
                mapView.controller.animateTo(OsmGeoPoint(point.latitude, point.longitude), 17.0, 800L)
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        )

        // Controlli zoom + inquadra tutto (lato sinistro)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
            shadowElevation = 2.dp
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(onClick = { mapView.controller.zoomIn() }) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom avanti", tint = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                IconButton(onClick = { mapView.controller.zoomOut() }) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom indietro", tint = MaterialTheme.colorScheme.onSurface)
                }
                if (uiState.points.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IconButton(onClick = { fitAllPoints(mapView, uiState.points) }) {
                        Icon(Icons.Filled.FitScreen, contentDescription = "Inquadra tutti i punti", tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }

        // FAB destra — raggruppati in due "pillole" (Modalità Mappa e Azioni)
        var isParkingActive by remember { mutableStateOf(false) }
        var isCenteringActive by remember { mutableStateOf(false) }
        val trackingStartedText = stringResource(R.string.tracking_started_toast)
        val trackingStoppedText = stringResource(R.string.tracking_stopped_toast)

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            // Gruppo Modalità Mappa (Layer + KML)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                shadowElevation = 2.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Pulsante Layer
                    IconButton(
                        onClick = {
                            currentLayer = when (currentLayer) {
                                MapLayer.ROAD -> MapLayer.TOPO
                                MapLayer.TOPO -> MapLayer.SATELLITE
                                MapLayer.SATELLITE -> MapLayer.ROAD
                            }
                        }
                    ) {
                        val tint = when (currentLayer) {
                            MapLayer.ROAD -> MaterialTheme.colorScheme.onSurface
                            MapLayer.TOPO -> MaterialTheme.colorScheme.tertiary
                            MapLayer.SATELLITE -> MaterialTheme.colorScheme.primary
                        }
                        Icon(Icons.Filled.Layers, contentDescription = stringResource(R.string.map_layer_button), tint = tint)
                    }

                    HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Pulsante KML
                    IconButton(onClick = { viewModel.toggleKmlPanel() }) {
                        val tint = if (uiState.kmlItems.any { it.isActive }) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        Icon(Icons.Filled.Polyline, contentDescription = stringResource(R.string.map_kml_toggle_button), tint = tint)
                    }
                }
            }

            // Gruppo Azioni (Traccia + Parcheggio + MyLocation)
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.9f),
                shadowElevation = 2.dp
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Tasto Registra percorso (visibile solo se non c'è un tracking di punto in corso)
                    AnimatedVisibility(
                        visible = !uiState.isTracking || uiState.isFreeTracking,
                        enter = scaleIn() + fadeIn(),
                        exit = scaleOut() + fadeOut()
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            IconButton(
                                onClick = {
                                    if (locationPermission.status.isGranted) {
                                        if (uiState.isFreeTracking) {
                                            viewModel.stopFreeTracking()
                                            coroutineScope.launch { snackbarHostState.showSnackbar(trackingStoppedText) }
                                        } else {
                                            viewModel.startFreeTracking()
                                            coroutineScope.launch { snackbarHostState.showSnackbar(trackingStartedText) }
                                        }
                                    } else {
                                        locationPermission.launchPermissionRequest()
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = if (uiState.isFreeTracking) Icons.Filled.Stop else Icons.Filled.FiberManualRecord,
                                    contentDescription = stringResource(
                                        if (uiState.isFreeTracking) R.string.tracking_free_stop else R.string.tracking_free_start
                                    ),
                                    tint = if (uiState.isFreeTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                            HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }

                    // Tasto Parcheggio
                    IconButton(
                        onClick = {
                            if (locationPermission.status.isGranted) {
                                coroutineScope.launch {
                                    isParkingActive = true
                                    getFreshLocation(context)?.let { (lat, lon) ->
                                        viewModel.saveParkingPoint(lat, lon, parkingPointTitle)
                                    }
                                    isParkingActive = false
                                }
                            } else {
                                locationPermission.launchPermissionRequest()
                            }
                        }
                    ) {
                        val tint = if (isParkingActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        Icon(Icons.Filled.DirectionsCar, contentDescription = stringResource(R.string.map_parking_fab), tint = tint)
                    }

                    HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                    // Tasto posizione utente / centra
                    IconButton(
                        onClick = {
                            if (locationPermission.status.isGranted) {
                                coroutineScope.launch {
                                    isCenteringActive = true
                                    val result = getFreshLocation(context)
                                    if (result != null) {
                                        val (lat, lon) = result
                                        myLocationOverlayRef.value?.let { mapView.overlays.remove(it) }
                                        val overlay = MyLocationOverlay(lat, lon)
                                        mapView.overlays.add(overlay)
                                        myLocationOverlayRef.value = overlay
                                        mapView.invalidate()
                                        mapView.controller.animateTo(OsmGeoPoint(lat, lon))
                                    } else {
                                        snackbarHostState.showSnackbar(locationUnavailableText)
                                    }
                                    isCenteringActive = false
                                }
                            } else {
                                locationPermission.launchPermissionRequest()
                            }
                        }
                    ) {
                        val tint = if (isCenteringActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_my_location), tint = tint)
                    }
                }
            }
        }

        // Chip nome layer — compare a sinistra del FAB Layer, scompare dopo 1.5s
        AnimatedVisibility(
            visible = showLayerLabel,
            enter = fadeIn() + scaleIn(initialScale = 0.85f),
            exit = fadeOut() + scaleOut(targetScale = 0.85f),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 64.dp, bottom = 290.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.inverseSurface,
                shadowElevation = 4.dp
            ) {
                Text(
                    text = layerLabelText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.inverseOnSurface,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Prima concessione del permesso GPS a runtime: centra sulla posizione utente
        // solo se non c'è già un focusTarget attivo e la mappa è ancora ai default.
        LaunchedEffect(locationPermission.status.isGranted) {
            if (locationPermission.status.isGranted
                && viewModel.uiState.value.focusTarget == null
                && !viewModel.uiState.value.hasAppliedInitialZoom) {
                getLastKnownLocation(context)?.let { loc ->
                    mapView.controller.setCenter(OsmGeoPoint(loc.latitude, loc.longitude))
                    mapView.controller.setZoom(15.0)
                }
            }
        }

        FloatingActionButton(
            onClick = { navController.navigate(Routes.AddEditPoint.createRoute()) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.map_add_point),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        if (uiState.isBottomSheetVisible && uiState.selectedPoint != null) {
            PointBottomSheet(
                point = uiState.selectedPoint!!,
                onDismiss = viewModel::onBottomSheetDismiss,
                onEditClick = { point ->
                    viewModel.onBottomSheetDismiss()
                    navController.navigate(Routes.AddEditPoint.createRoute(point.id))
                },
                onDetailClick = { point ->
                    viewModel.onBottomSheetDismiss()
                    navController.navigate(Routes.PointDetail.createRoute(point.id))
                },
                onShareClick = { point ->
                    viewModel.onBottomSheetDismiss()
                    viewModel.onShareRequested(point)
                },
                onOpenGoogleMaps = { point ->
                    viewModel.onBottomSheetDismiss()
                    val uri = android.net.Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}(${android.net.Uri.encode(point.title)})")
                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                },
                onShareGoogleMaps = { point ->
                    viewModel.onBottomSheetDismiss()
                    val url = "https://maps.google.com/?q=${point.latitude},${point.longitude}"
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, url)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, null))
                }
            )
        }

        // Picker per cluster con punti troppo vicini da separare visivamente
        clusterPickerRef.value?.let { points ->
            ClusterPickerSheet(
                points = points,
                onDismiss = { clusterPickerRef.value = null },
                onPointClick = { point ->
                    clusterPickerRef.value = null
                    viewModel.onPointSelected(point)
                }
            )
        }

        // Dialog parcheggio già esistente
        if (uiState.showParkingOptions) {
            AlertDialog(
                onDismissRequest = viewModel::dismissParkingOptions,
                icon = { Icon(Icons.Filled.DirectionsCar, contentDescription = null) },
                title = { Text(stringResource(R.string.map_parking_dialog_title)) },
                text = { Text(stringResource(R.string.map_parking_dialog_text)) },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmUpdateParking) {
                        Text(stringResource(R.string.map_parking_update_action))
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = viewModel::navigateToParking) {
                            Text(stringResource(R.string.map_parking_navigate))
                        }
                        TextButton(onClick = viewModel::dismissParkingOptions) {
                            Text(stringResource(R.string.action_cancel))
                        }
                    }
                }
            )
        }

        // Pannello KML
        if (uiState.showKmlPanel) {
            KmlLayerSheet(
                kmlItems = uiState.kmlItems,
                expandedPointIds = uiState.expandedKmlPointIds,
                userLat = uiState.userLatitude,
                userLon = uiState.userLongitude,
                hasUserLocation = uiState.hasUserLocation,
                onDismiss = viewModel::dismissKmlPanel,
                onToggle = viewModel::toggleKml,
                onToggleGroup = viewModel::toggleKmlPointGroup
            )
        }

        // Dialog traccia completata (tracking libero terminato)
        val pendingTrackResult = uiState.pendingTrackResult
        if (pendingTrackResult != null) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(stringResource(R.string.tracking_save_title)) },
                text = {
                    Text(stringResource(R.string.tracking_save_point_count, pendingTrackResult.pointCount))
                },
                confirmButton = {
                    TextButton(onClick = viewModel::saveTrackToNewPoint) {
                        Text(stringResource(R.string.tracking_save_create_point))
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = viewModel::showPointPickerSheet) {
                            Text(stringResource(R.string.tracking_save_attach_point))
                        }
                        TextButton(onClick = viewModel::discardPendingTrack) {
                            Text(stringResource(R.string.tracking_save_discard))
                        }
                    }
                }
            )
        }

        // BottomSheet selezione punto esistente
        if (uiState.showPointPickerSheet) {
            PointPickerSheet(
                points = uiState.points,
                onDismiss = viewModel::hidePointPickerSheet,
                onPointClick = { point ->
                    viewModel.saveTrackToExistingPoint(point.id)
                }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        ) { data ->
            Snackbar(
                snackbarData = data,
                shape = RoundedCornerShape(50),
                containerColor = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.92f),
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.padding(horizontal = 32.dp, vertical = 8.dp)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ClusterPickerSheet(
    points: List<GeoPoint>,
    onDismiss: () -> Unit,
    onPointClick: (GeoPoint) -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = "${points.size} punti in questa zona",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(modifier = Modifier.fillMaxWidth()) {
            items(points) { point ->
                ListItem(
                    headlineContent = { Text(point.title) },
                    leadingContent = { Text(point.emoji, style = MaterialTheme.typography.titleLarge) },
                    supportingContent = point.description.takeIf { it.isNotBlank() }?.let {
                        { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    },
                    modifier = Modifier.clickable { onPointClick(point) }
                )
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PointPickerSheet(
    points: List<GeoPoint>,
    onDismiss: () -> Unit,
    onPointClick: (GeoPoint) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, points) {
        if (query.isBlank()) points
        else points.filter { it.title.contains(query, ignoreCase = true) }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.tracking_save_pick_point),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text(stringResource(R.string.tracking_save_search_hint)) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.5f)
        ) {
            items(filtered) { point ->
                ListItem(
                    headlineContent = { Text(point.title) },
                    leadingContent = { Text(point.emoji, style = MaterialTheme.typography.titleLarge) },
                    modifier = Modifier.clickable { onPointClick(point) }
                )
                HorizontalDivider()
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KmlLayerSheet(
    kmlItems: List<KmlSessionItem>,
    expandedPointIds: Set<String>,
    userLat: Double,
    userLon: Double,
    hasUserLocation: Boolean,
    onDismiss: () -> Unit,
    onToggle: (String) -> Unit,
    onToggleGroup: (String) -> Unit
) {
    // Raggruppa per punto: geoPointId → (pointTitle, items)
    val groups = kmlItems
        .groupBy { it.kml.geoPointId }
        .entries
        .sortedBy { (_, items) -> items.first().pointTitle }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.map_kml_panel_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (groups.isEmpty()) {
            Text(
                text = stringResource(R.string.map_kml_no_layers),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.5f)
            ) {
                groups.forEach { (geoPointId, items) ->
                    val pointTitle = items.first().pointTitle
                    val isExpanded = geoPointId in expandedPointIds
                    val activeCount = items.count { it.isActive }

                    item(key = "header_$geoPointId") {
                        ListItem(
                            headlineContent = {
                                Text(
                                    pointTitle,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                val distStr = if (hasUserLocation) {
                                    " · ${formatDistance(distanceBetween(userLat, userLon, items.first().pointLat, items.first().pointLon))}"
                                } else ""
                                Text(
                                    "${items.size} KML" +
                                        (if (activeCount > 0) " · $activeCount attivi" else "") +
                                        distStr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null
                                )
                            },
                            modifier = Modifier
                                .clickable { onToggleGroup(geoPointId) }
                        )
                        HorizontalDivider()
                    }

                    if (isExpanded) {
                        items(items, key = { it.kml.id }) { item ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        item.kml.name,
                                        modifier = Modifier.padding(start = 16.dp),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = item.trackLengthMeters?.let { len ->
                                    { Text(formatDistance(len), modifier = Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                },
                                trailingContent = {
                                    Switch(
                                        checked = item.isActive,
                                        onCheckedChange = { onToggle(item.kml.id) }
                                    )
                                },
                                modifier = Modifier.clickable { onToggle(item.kml.id) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MapSearchBar(
    isOpen: Boolean,
    query: String,
    results: List<GeoPoint>,
    userLat: Double,
    userLon: Double,
    hasUserLocation: Boolean,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onQueryChange: (String) -> Unit,
    onResultClick: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(50.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (!isOpen) Modifier.clickable { onOpen() } else Modifier)
                    .padding(horizontal = 16.dp, vertical = if (isOpen) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(8.dp))
                if (isOpen) {
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyLarge.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        decorationBox = { innerTextField ->
                            if (query.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.map_search_placeholder),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            innerTextField()
                        }
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Filled.Clear, contentDescription = stringResource(R.string.map_search_clear))
                        }
                    }
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = null)
                    }
                } else {
                    Text(
                        text = stringResource(R.string.map_search_placeholder),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            AnimatedVisibility(
                visible = isOpen && results.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    HorizontalDivider()
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp)) {
                        items(results, key = { it.id }) { point ->
                            val distText = if (hasUserLocation) {
                                formatDistance(distanceBetween(userLat, userLon, point.latitude, point.longitude))
                            } else "—"
                            ListItem(
                                headlineContent = {
                                    Text(point.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = {
                                    Text(point.emoji, style = MaterialTheme.typography.titleLarge)
                                },
                                trailingContent = {
                                    Text(
                                        distText,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                modifier = Modifier.clickable { onResultClick(point) }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(isOpen) {
        if (isOpen) focusRequester.requestFocus()
    }
}

private fun distanceBetween(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
    val result = FloatArray(1)
    android.location.Location.distanceBetween(lat1, lon1, lat2, lon2, result)
    return result[0]
}

private fun formatDistance(meters: Float): String = when {
    meters < 1000f -> "${meters.toInt()}m"
    else -> "${"%.1f".format(meters / 1000f)}km"
}

private fun getLastKnownLocation(context: Context): android.location.Location? {
    return try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
    } catch (_: SecurityException) { null }
}

/**
 * Richiede una nuova fix GPS (timeout 5s) con tre livelli di fallback:
 *  1. FusedLocationProvider (se Play Services aggiornato)
 *  2. LocationManager.requestLocationUpdates — attiva il GPS e aspetta il primo fix
 *  3. getLastKnownLocation — legge la cache (ultima risorsa)
 */
private suspend fun getFreshLocation(context: Context): Pair<Double, Double>? {
    if (isPlayServicesAvailable(context)) {
        val cts = CancellationTokenSource()
        try {
            val location = withTimeoutOrNull(5_000L) {
                LocationServices.getFusedLocationProviderClient(context)
                    .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                    .await()
            }
            if (location != null) return location.latitude to location.longitude
        } catch (_: Exception) {
        } finally {
            cts.cancel()
        }
    }
    // Fallback: attiva GPS direttamente e aspetta il primo fix (funziona senza Play Services)
    return getLocationFromManager(context)
        ?: getLastKnownLocation(context)?.let { it.latitude to it.longitude }
}

/**
 * Attiva GPS (o rete) tramite LocationManager e aspetta il primo fix (max 5s).
 * Necessario quando Play Services non è disponibile o non ha posizione in cache.
 * Funziona anche con il mock GPS dell'emulatore (adb emu geo fix).
 */
@android.annotation.SuppressLint("MissingPermission")
private suspend fun getLocationFromManager(context: Context): Pair<Double, Double>? =
    withTimeoutOrNull(5_000L) {
        suspendCancellableCoroutine { cont ->
            val lm = context.getSystemService(Context.LOCATION_SERVICE)
                as android.location.LocationManager
            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(loc: android.location.Location) {
                    lm.removeUpdates(this)
                    if (cont.isActive) cont.resumeWith(Result.success(loc.latitude to loc.longitude))
                }
                override fun onStatusChanged(p: String, s: Int, e: android.os.Bundle?) {}
                override fun onProviderEnabled(p: String) {}
                override fun onProviderDisabled(p: String) {}
            }
            val provider = listOf(
                android.location.LocationManager.GPS_PROVIDER,
                android.location.LocationManager.NETWORK_PROVIDER
            ).firstOrNull { lm.isProviderEnabled(it) }

            if (provider != null) {
                try {
                    lm.requestLocationUpdates(
                        provider, 0L, 0f, listener,
                        android.os.Looper.getMainLooper()
                    )
                    cont.invokeOnCancellation { lm.removeUpdates(listener) }
                } catch (_: SecurityException) {
                    if (cont.isActive) cont.resumeWith(Result.success(null))
                }
            } else {
                if (cont.isActive) cont.resumeWith(Result.success(null))
            }
        }
    }

private fun isPlayServicesAvailable(context: Context): Boolean =
    com.google.android.gms.common.GoogleApiAvailability.getInstance()
        .isGooglePlayServicesAvailable(context) == com.google.android.gms.common.ConnectionResult.SUCCESS

private fun fitAllPoints(mapView: MapView, points: List<GeoPoint>) {
    if (points.isEmpty()) return
    val lats = points.map { it.latitude }
    val lons = points.map { it.longitude }
    val latPad = maxOf(0.005, (lats.max() - lats.min()) * 0.25)
    val lonPad = maxOf(0.005, (lons.max() - lons.min()) * 0.25)
    mapView.zoomToBoundingBox(
        BoundingBox(
            lats.max() + latPad, lons.max() + lonPad,
            lats.min() - latPad, lons.min() - lonPad
        ),
        true, 120
    )
}

/**
 * Raggruppa i punti in cluster greedy basati sulla distanza in gradi.
 * La soglia è calibrata su ~80px di schermo: threshold = 112.5 / 2^zoom
 * (zoom 13 ≈ 1.4°/tile → 80px ≈ 0.014°; zoom 17 → 0.00086°).
 * Sopra zoom 19 non si raggruppa più nulla.
 */
private fun clusterPoints(points: List<GeoPoint>, zoom: Double): List<MapCluster> {
    if (points.isEmpty()) return emptyList()
    val threshold = if (zoom >= 19.0) 0.0 else 112.5 / 2.0.pow(zoom)
    val remaining = points.toMutableList()
    val clusters = mutableListOf<MapCluster>()
    while (remaining.isNotEmpty()) {
        val seed = remaining.removeAt(0)
        if (threshold == 0.0) {
            clusters.add(MapCluster(seed.latitude, seed.longitude, listOf(seed)))
            continue
        }
        val grouped = remaining.filter { p ->
            abs(p.latitude - seed.latitude) < threshold &&
                abs(p.longitude - seed.longitude) < threshold
        }
        remaining.removeAll(grouped.toSet())
        val all = listOf(seed) + grouped
        clusters.add(
            MapCluster(
                centerLat = all.map { it.latitude }.average(),
                centerLon = all.map { it.longitude }.average(),
                points = all
            )
        )
    }
    return clusters
}

// Soglia in gradi sotto la quale due punti non sono separabili visivamente nemmeno al max zoom.
// ~0.001° ≈ 111m lat / 78m lon @ lat 45° → corrisponde alla larghezza del marker bubble a zoom 19.
private const val MIN_SEPARABLE_DEG = 0.001

private fun updateClusteredMarkers(
    mapView: MapView,
    points: List<GeoPoint>,
    context: Context,
    onMarkerClick: (GeoPoint) -> Unit,
    onClusterTooClose: (List<GeoPoint>) -> Unit = {}
) {
    val zoom = mapView.zoomLevelDouble
    val clusters = clusterPoints(points, zoom)
    mapView.overlays.removeAll { it is Marker && it !is KmlMarker }

    clusters.forEach { cluster ->
        val marker = Marker(mapView).apply {
            position = OsmGeoPoint(cluster.centerLat, cluster.centerLon)
            if (cluster.points.size == 1) {
                val point = cluster.points[0]
                icon = createCloudBubbleDrawable(context, point.emoji, point.title)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, mv ->
                    val targetZoom = if (mv.zoomLevelDouble < 17.0) 17.0 else mv.zoomLevelDouble
                    mv.controller.animateTo(OsmGeoPoint(point.latitude, point.longitude), targetZoom, 800L)
                    onMarkerClick(point)
                    true
                }
            } else {
                icon = createClusterDrawable(context, cluster.points.size)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                setOnMarkerClickListener { _, _ ->
                    val lats = cluster.points.map { it.latitude }
                    val lons = cluster.points.map { it.longitude }
                    val latRange = lats.max() - lats.min()
                    val lonRange = lons.max() - lons.min()
                    if (latRange < MIN_SEPARABLE_DEG && lonRange < MIN_SEPARABLE_DEG) {
                        // Punti troppo vicini: mostra lista invece di zoomare
                        onClusterTooClose(cluster.points)
                    } else {
                        val latPad = maxOf(0.002, latRange * 0.3)
                        val lonPad = maxOf(0.002, lonRange * 0.3)
                        mapView.zoomToBoundingBox(
                            BoundingBox(
                                lats.max() + latPad, lons.max() + lonPad,
                                lats.min() - latPad, lons.min() - lonPad
                            ),
                            true, 80
                        )
                    }
                    true
                }
            }
        }
        mapView.overlays.add(marker)
    }
    mapView.invalidate()
}

/**
 * Pin Material per marker singolo: badge arrotondato (emoji + titolo) con codina triangolare.
 * Design pulito senza bumps — bordo verde, ombra morbida.
 */
private fun createCloudBubbleDrawable(
    context: Context,
    emoji: String,
    title: String
): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val paddingH = 10f * d
    val paddingV = 8f * d
    val emojiSize = 20f * d
    val titleSize = 10f * d
    val gap = 6f * d
    val cornerR = 12f * d
    val tailH = 10f * d
    val shadowDx = 1.5f * d
    val shadowDy = 2f * d

    // Misura il titolo per calcolare la larghezza del badge
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = titleSize
        typeface = Typeface.DEFAULT_BOLD
    }
    val maxTitleW = 90f * d
    var displayTitle = title
    while (titlePaint.measureText(displayTitle) > maxTitleW && displayTitle.length > 3) {
        displayTitle = displayTitle.dropLast(1)
    }
    if (displayTitle.length < title.length) displayTitle = displayTitle.dropLast(2) + "…"
    val titleW = titlePaint.measureText(displayTitle)

    val bodyW = paddingH + emojiSize + gap + titleW + paddingH
    val bodyH = paddingV + emojiSize + paddingV
    val totalW = (bodyW + shadowDx + 2f * d).toInt()
    val totalH = (bodyH + tailH + shadowDy + 2f * d).toInt()

    val bitmap = Bitmap.createBitmap(totalW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val bodyRect = RectF(0f, 0f, bodyW, bodyH)
    val cx = bodyW / 2f

    val pinPath = Path().apply {
        addRoundRect(bodyRect, cornerR, cornerR, Path.Direction.CW)
    }
    // Codina triangolare centrata in basso
    pinPath.op(
        Path().apply {
            moveTo(cx - tailH * 0.6f, bodyH - 2f * d)
            lineTo(cx, bodyH + tailH)
            lineTo(cx + tailH * 0.6f, bodyH - 2f * d)
            close()
        },
        Path.Op.UNION
    )

    // Ombra
    canvas.save()
    canvas.translate(shadowDx, shadowDy)
    canvas.drawPath(pinPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(50, 0, 0, 0)
        style = Paint.Style.FILL
    })
    canvas.restore()

    // Riempimento bianco
    canvas.drawPath(pinPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    })

    // Bordo verde
    canvas.drawPath(pinPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(255, 56, 102, 65)
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    })

    // Emoji
    val textY = bodyH / 2f + emojiSize * 0.35f
    canvas.drawText(emoji, paddingH, textY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = emojiSize
        textAlign = Paint.Align.LEFT
    })

    // Titolo
    titlePaint.apply {
        color = AndroidColor.argb(255, 27, 36, 20)
        textAlign = Paint.Align.LEFT
    }
    canvas.drawText(displayTitle, paddingH + emojiSize + gap, textY - 1f * d, titlePaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Icona cluster: 3 cerchi concentrici sfumati (stile Google/Apple Maps).
 * Anello esterno 25% opaco, anello medio 55% opaco, cerchio centrale pieno.
 * Numero in bianco bold al centro.
 */
private fun createClusterDrawable(context: Context, count: Int): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val innerR = 18f * d
    val midR = 26f * d
    val outerR = 34f * d
    val size = (outerR * 2 + 4f * d).toInt()
    val cx = size / 2f
    val cy = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Anello esterno — trasparente 25%
    canvas.drawCircle(cx, cy, outerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(64, 56, 102, 65)
        style = Paint.Style.FILL
    })

    // Anello medio — semi-trasparente 55%
    canvas.drawCircle(cx, cy, midR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(140, 56, 102, 65)
        style = Paint.Style.FILL
    })

    // Cerchio centrale pieno
    canvas.drawCircle(cx, cy, innerR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(255, 56, 102, 65)
        style = Paint.Style.FILL
    })

    // Numero in bianco bold
    val label = if (count > 99) "99+" else count.toString()
    canvas.drawText(label, cx, cy + 5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 13f * d
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    })

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Overlay "Sono qui" con indicatore direzionale.
 * Disegna su canvas: settore circolare con RadialGradient (opaco vicino al punto,
 * trasparente in lontananza) + glow + anello bianco + disco blu centrale.
 * [azimuth] viene aggiornato in tempo reale dal sensore TYPE_ROTATION_VECTOR.
 */
private class MyLocationOverlay(var lat: Double, var lon: Double) :
    org.osmdroid.views.overlay.Overlay() {

    var azimuth: Float = 0f

    override fun draw(canvas: Canvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val pt = android.graphics.Point()
        mapView.projection.toPixels(OsmGeoPoint(lat, lon), pt)
        val x = pt.x.toFloat()
        val y = pt.y.toFloat()
        val d = mapView.context.resources.displayMetrics.density
        val dotR  =  9f * d
        val ringR = 12f * d
        val glowR = 17f * d

        // Settore direzionale con sfumatura radiale — ruotato sull'azimuth corrente
        canvas.save()
        canvas.rotate(azimuth, x, y)
        val beamLen   = 55f * d   // lunghezza del raggio
        val halfAngle = 22f       // semi-ampiezza del settore in gradi
        // Settore: dal centro, arco nella direzione "su" (−90° ± halfAngle)
        val sectorPath = Path().apply {
            moveTo(x, y)
            arcTo(
                RectF(x - beamLen, y - beamLen, x + beamLen, y + beamLen),
                -90f - halfAngle,
                halfAngle * 2f,
                false
            )
            close()
        }
        canvas.drawPath(sectorPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                x, y, beamLen,
                intArrayOf(
                    AndroidColor.argb(200, 33, 150, 243),  // opaco vicino al punto
                    AndroidColor.argb(60,  33, 150, 243),  // semi-trasparente a metà
                    AndroidColor.argb(0,   33, 150, 243)   // invisibile al bordo
                ),
                floatArrayOf(0.05f, 0.55f, 1.0f),
                Shader.TileMode.CLAMP
            )
            style = Paint.Style.FILL
        })
        canvas.restore()

        // Glow esterno
        canvas.drawCircle(x, y, glowR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(55, 33, 150, 243)
            style = Paint.Style.FILL
        })
        // Anello bianco
        canvas.drawCircle(x, y, ringR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.FILL
        })
        // Disco blu
        canvas.drawCircle(x, y, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.rgb(33, 150, 243)
            style = Paint.Style.FILL
        })
    }
}
