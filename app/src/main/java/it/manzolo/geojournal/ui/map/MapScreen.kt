package it.manzolo.geojournal.ui.map

import android.Manifest
import android.content.Context
import android.os.Build
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.IconButton
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
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
import androidx.compose.runtime.MutableState
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.core.content.FileProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
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
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

private enum class MapLayer { ROAD, TOPO, SATELLITE }

/**
 * Fornisce le URL/JSON di stile per i 3 layer supportati.
 * - ROAD: OpenFreeMap Liberty (vector, gratuito, senza API key, AGPL-friendly)
 *   Fallback documentato: MapTiler (100k tiles/mese gratis, richiede key)
 * - TOPO: OpenTopoMap (raster, gratuito, attribution richiesta)
 * - SATELLITE: ESRI World Imagery (raster, {z}/{y}/{x} — URL nativa MapLibre)
 */
private object MapLibreStyleProvider {
    private const val ROAD_URL = "https://tiles.openfreemap.org/styles/liberty"

    private const val TOPO_JSON = """{
        "version": 8,
        "sources": {
            "topo-tiles": {
                "type": "raster",
                "tiles": ["https://tile.opentopomap.org/{z}/{x}/{y}.png"],
                "tileSize": 256,
                "maxzoom": 17,
                "attribution": "© OpenTopoMap (CC-BY-SA)"
            }
        },
        "layers": [{
            "id": "topo-layer",
            "type": "raster",
            "source": "topo-tiles"
        }]
    }"""

    // ESRI usa Z/Y/X anziché Z/X/Y — MapLibre supporta natively il template {z}/{y}/{x}
    private const val SATELLITE_JSON = """{
        "version": 8,
        "sources": {
            "satellite-tiles": {
                "type": "raster",
                "tiles": ["https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}"],
                "tileSize": 256,
                "maxzoom": 19,
                "attribution": "© Esri, Maxar, Earthstar Geographics"
            }
        },
        "layers": [{
            "id": "satellite-layer",
            "type": "raster",
            "source": "satellite-tiles"
        }]
    }"""

    /**
     * Costruisce uno [Style.Builder] per il layer richiesto.
     * NB: `MapLibreMap.setStyle(String)` interpreta la stringa come URI, quindi per
     * i JSON inline serve esplicitamente `Style.Builder().fromJson(...)`.
     */
    fun builderFor(layer: MapLayer): Style.Builder = when (layer) {
        MapLayer.ROAD      -> Style.Builder().fromUri(ROAD_URL)
        MapLayer.TOPO      -> Style.Builder().fromJson(TOPO_JSON)
        MapLayer.SATELLITE -> Style.Builder().fromJson(SATELLITE_JSON)
    }
}

private data class MapCluster(
    val centerLat: Double,
    val centerLon: Double,
    val points: List<GeoPoint>
)

/**
 * Payload associato a ogni [Symbol] creato sulla mappa.
 * Il click listener del [SymbolManager] usa questo tipo per distinguere
 * un marker singolo (apre bottom sheet) da un cluster (zoom-to-bbox o picker).
 */
private sealed class SymbolTarget {
    data class Single(val point: GeoPoint) : SymbolTarget()
    data class Cluster(val points: List<GeoPoint>) : SymbolTarget()
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current
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
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.point_share)))
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

    // Snackbar per azioni archivia/elimina
    val actionSnackbarText = uiState.actionSnackbarRes?.let { stringResource(it) }
    LaunchedEffect(uiState.actionSnackbarRes) {
        actionSnackbarText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionSnackbar()
        }
    }

    // Snackbar preferiti
    val favoriteSnackbarText = uiState.favoriteSnackbarRes?.let { stringResource(it) }
    LaunchedEffect(uiState.favoriteSnackbarRes) {
        favoriteSnackbarText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearFavoriteSnackbar()
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

    // Ref aggiornabile per avere sempre i punti correnti nel listener di camera
    val pointsRef = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    // Punti del cluster troppo vicini da separare visivamente → mostra il picker
    val clusterPickerRef = remember { mutableStateOf<List<GeoPoint>?>(null) }
    // Ref MapLibreMap e Style (disponibili dopo getMapAsync + setStyle)
    val mapLibreMapRef = remember { mutableStateOf<MapLibreMap?>(null) }
    val mapStyleRef = remember { mutableStateOf<Style?>(null) }
    // SymbolManager per i marker (inizializzato dopo setStyle)
    val symbolManagerRef = remember { mutableStateOf<SymbolManager?>(null) }
    // SymbolId → target (marker singolo o cluster) per il click listener
    val symbolIdToTarget = remember { mutableStateOf<Map<Long, SymbolTarget>>(emptyMap()) }
    // KML overlay attivi nella sessione (kmlId → KmlAnnotationHandle)
    val kmlOverlaysRef = remember { mutableStateOf<Map<String, KmlAnnotationHandle>>(emptyMap()) }
    // True dopo che l'utente ha attivato il LocationComponent (tap MyLocation FAB).
    // Usato per ri-attivarlo dopo cambio layer (setStyle invalida lo stato).
    val locationPuckActive = remember { mutableStateOf(false) }
    // SymbolManager dedicato ai KML (separato da quello marker principali)
    val kmlSymbolManagerRef = remember { mutableStateOf<SymbolManager?>(null) }
    val kmlLineManagerRef = remember { mutableStateOf<org.maplibre.android.plugins.annotation.LineManager?>(null) }
    val kmlFillManagerRef = remember { mutableStateOf<org.maplibre.android.plugins.annotation.FillManager?>(null) }

    // Feature 5: calcola posizione iniziale (sincrona) prima di costruire MapView
    val initialCenter = remember {
        val hasLocationPerm = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val lastKnown = if (!uiState.hasAppliedInitialZoom && hasLocationPerm) getLastKnownLocation(context) else null
        lastKnown?.let { LatLng(it.latitude, it.longitude) }
            ?: LatLng(uiState.userLatitude, uiState.userLongitude)
    }
    val initialZoom = remember {
        val hasLocationPerm = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        val lastKnown = if (!uiState.hasAppliedInitialZoom && hasLocationPerm) getLastKnownLocation(context) else null
        if (lastKnown != null) 15.0 else uiState.zoomLevel
    }

    val mapView = remember { MapView(context) }

    var currentLayer by remember { mutableStateOf(MapLayer.ROAD) }
    val layerRoadLabel = stringResource(R.string.map_layer_road)
    val layerTopoLabel = stringResource(R.string.map_layer_topo)
    val layerSatLabel = stringResource(R.string.map_layer_satellite)
    val isFirstLayerRender = remember { mutableStateOf(true) }
    var showLayerLabel by remember { mutableStateOf(false) }
    var layerLabelText by remember { mutableStateOf("") }

    // Lifecycle completo MapLibre (onStart/onResume/onPause/onStop/onDestroy)
    DisposableEffect(Unit) {
        mapView.onStart()
        mapView.onResume()
        onDispose {
            symbolManagerRef.value?.onDestroy()
            kmlSymbolManagerRef.value?.onDestroy()
            kmlLineManagerRef.value?.onDestroy()
            kmlFillManagerRef.value?.onDestroy()
            mapView.onPause()
            mapView.onStop()
            mapView.onDestroy()
        }
    }

    // getMapAsync: bootstrap mappa, stile iniziale, camera, camera listener, SymbolManager
    LaunchedEffect(Unit) {
        mapView.getMapAsync { map ->
            mapLibreMapRef.value = map
            // Camera iniziale
            map.moveCamera(
                CameraUpdateFactory.newCameraPosition(
                    CameraPosition.Builder()
                        .target(initialCenter)
                        .zoom(initialZoom)
                        .build()
                )
            )
            // MapLibre non ha pulsanti zoom built-in (gestiamo noi con i FAB)
            map.uiSettings.isZoomGesturesEnabled = true
            map.uiSettings.isAttributionEnabled = true
            map.uiSettings.isLogoEnabled = true

            // Camera move/idle: salva posizione (throttle 100ms) + ri-clustera al cambio zoom
            var lastSaveMs = 0L
            var lastZoom = -1
            map.addOnCameraMoveListener {
                val now = System.currentTimeMillis()
                if (now - lastSaveMs > 100) {
                    val center = map.cameraPosition.target ?: return@addOnCameraMoveListener
                    viewModel.onMapMoved(center.latitude, center.longitude, map.cameraPosition.zoom)
                    lastSaveMs = now
                }
                val z = map.cameraPosition.zoom.toInt()
                if (z != lastZoom) {
                    lastZoom = z
                    mapStyleRef.value?.let { style ->
                        symbolManagerRef.value?.let { sm ->
                            updateClusteredMarkers(
                                map, style, sm, symbolIdToTarget,
                                pointsRef.value, context
                            )
                        }
                    }
                }
            }

            // Carica lo stile iniziale
            map.setStyle(MapLibreStyleProvider.builderFor(currentLayer)) { style ->
                mapStyleRef.value = style
                // SymbolManager per marker principali
                val sm = SymbolManager(mapView, map, style).apply {
                    iconAllowOverlap = true
                    iconIgnorePlacement = true
                }
                symbolManagerRef.value = sm
                installSymbolClickListener(sm, map, symbolIdToTarget, viewModel, clusterPickerRef)

                // SymbolManager KML (separato — i click non interferiscono coi marker principali)
                kmlSymbolManagerRef.value = SymbolManager(mapView, map, style).apply {
                    iconAllowOverlap = true
                    iconIgnorePlacement = true
                }
                kmlLineManagerRef.value = org.maplibre.android.plugins.annotation.LineManager(mapView, map, style)
                kmlFillManagerRef.value = org.maplibre.android.plugins.annotation.FillManager(mapView, map, style)
            }
        }
    }

    // Switch layer: carica nuovo stile e ricrea SymbolManager + marker
    LaunchedEffect(currentLayer) {
        if (isFirstLayerRender.value) {
            isFirstLayerRender.value = false
            return@LaunchedEffect
        }
        val map = mapLibreMapRef.value ?: return@LaunchedEffect
        // Distruggi annotation manager prima di cambiare stile
        symbolManagerRef.value?.onDestroy()
        kmlSymbolManagerRef.value?.onDestroy()
        kmlLineManagerRef.value?.onDestroy()
        kmlFillManagerRef.value?.onDestroy()
        symbolManagerRef.value = null
        mapStyleRef.value = null

        map.setStyle(MapLibreStyleProvider.builderFor(currentLayer)) { style ->
            mapStyleRef.value = style
            val sm = SymbolManager(mapView, map, style).apply {
                iconAllowOverlap = true
                iconIgnorePlacement = true
            }
            symbolManagerRef.value = sm
            installSymbolClickListener(sm, map, symbolIdToTarget, viewModel, clusterPickerRef)

            val kmlSm = SymbolManager(mapView, map, style).apply {
                iconAllowOverlap = true
                iconIgnorePlacement = true
            }
            kmlSymbolManagerRef.value = kmlSm
            val kmlLm = org.maplibre.android.plugins.annotation.LineManager(mapView, map, style)
            kmlLineManagerRef.value = kmlLm
            val kmlFm = org.maplibre.android.plugins.annotation.FillManager(mapView, map, style)
            kmlFillManagerRef.value = kmlFm

            // Ridisegna marker col nuovo stile
            updateClusteredMarkers(
                map, style, sm, symbolIdToTarget,
                pointsRef.value, context
            )

            // Ripristina KML overlay attivi dopo cambio layer (parseKml è suspend → IO)
            coroutineScope.launch {
                val rebuiltKml = mutableMapOf<String, KmlAnnotationHandle>()
                uiState.kmlItems.filter { it.isActive }.forEach { item ->
                    val geometries = viewModel.parseKml(item.kml.id)
                    if (geometries.isNotEmpty()) {
                        rebuiltKml[item.kml.id] =
                            KmlOverlayManager.buildOverlays(context, style, kmlSm, kmlLm, kmlFm, geometries)
                    }
                }
                kmlOverlaysRef.value = rebuiltKml
            }

            // Ripristina LocationComponent (puck + bearing) se era attivo prima del cambio layer
            if (locationPuckActive.value && locationPermission.status.isGranted) {
                activateLocationPuck(context, map, style)
            }
        }
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

    // Aggiorna marker quando cambiano i punti o la query di ricerca
    LaunchedEffect(uiState.points, uiState.searchQuery) {
        val displayPoints = if (uiState.searchQuery.isBlank()) uiState.points else uiState.searchResults
        pointsRef.value = displayPoints
        val map = mapLibreMapRef.value ?: return@LaunchedEffect
        val style = mapStyleRef.value ?: return@LaunchedEffect
        val sm = symbolManagerRef.value ?: return@LaunchedEffect
        updateClusteredMarkers(
            map, style, sm, symbolIdToTarget,
            displayPoints, context
        )
        if (!uiState.hasAppliedInitialZoom) {
            viewModel.markInitialFitDone()
        }
    }

    // KML overlays: aggiunge/rimuove in base allo stato di sessione
    LaunchedEffect(uiState.kmlItems) {
        val kmlSm = kmlSymbolManagerRef.value ?: return@LaunchedEffect
        val kmlLm = kmlLineManagerRef.value ?: return@LaunchedEffect
        val kmlFm = kmlFillManagerRef.value ?: return@LaunchedEffect
        val style = mapStyleRef.value ?: return@LaunchedEffect
        val current = kmlOverlaysRef.value.toMutableMap()
        // Rimuovi overlay per KML disattivati o rimossi dalla lista
        val activeIds = uiState.kmlItems.filter { it.isActive }.map { it.kml.id }.toSet()
        val toRemove = current.keys.filter { it !in activeIds }
        toRemove.forEach { id ->
            current[id]?.let { KmlOverlayManager.removeFromMap(kmlSm, kmlLm, kmlFm, it) }
            current.remove(id)
        }
        // Aggiungi overlay per KML attivati di nuovo
        val existingIds = current.keys
        uiState.kmlItems.filter { it.isActive && it.kml.id !in existingIds }.forEach { item ->
            val geometries = viewModel.parseKml(item.kml.id)
            if (geometries.isNotEmpty()) {
                val handle = KmlOverlayManager.buildOverlays(context, style, kmlSm, kmlLm, kmlFm, geometries)
                current[item.kml.id] = handle
            }
        }
        kmlOverlaysRef.value = current
    }

    // Focus su punto specifico (es. da Lista): centra + auto-seleziona il punto
    LaunchedEffect(uiState.focusTarget) {
        uiState.focusTarget?.let { target ->
            val map = mapLibreMapRef.value ?: return@LaunchedEffect
            map.animateCamera(
                CameraUpdateFactory.newLatLngZoom(LatLng(target.lat, target.lon), target.zoom)
            )
            target.pointId?.let { id ->
                uiState.points.find { it.id == id }?.let { viewModel.onPointSelected(it) }
            }
            viewModel.clearFocusTarget()
        }
    }

    // Prima concessione del permesso GPS a runtime: centra sulla posizione utente
    LaunchedEffect(locationPermission.status.isGranted) {
        if (locationPermission.status.isGranted
            && viewModel.uiState.value.focusTarget == null
            && !viewModel.uiState.value.hasAppliedInitialZoom) {
            getLastKnownLocation(context)?.let { loc ->
                val map = mapLibreMapRef.value
                if (map != null) {
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(LatLng(loc.latitude, loc.longitude), 15.0)
                    )
                }
            }
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
                keyboardController?.hide()
                viewModel.closeSearch()
                // Se il punto non è preferito e il filtro è attivo, mostra tutti i punti
                if (uiState.showFavoritesOnly && !point.isFavorite) {
                    viewModel.toggleFavoritesFilter()
                }
                mapLibreMapRef.value?.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(point.latitude, point.longitude), 17.0
                    ), 800
                )
                coroutineScope.launch {
                    delay(200)
                    viewModel.onPointSelected(point)
                }
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                .fillMaxWidth()
        )

        // Empty state quando filtro preferiti attivo ma lista vuota
        if (uiState.showFavoritesOnly && uiState.points.isEmpty() && !uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.map_no_favorites_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp)
                )
            }
        }

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
                IconButton(onClick = { mapLibreMapRef.value?.animateCamera(CameraUpdateFactory.zoomIn()) }) {
                    Icon(Icons.Filled.ZoomIn, contentDescription = stringResource(R.string.map_zoom_in), tint = MaterialTheme.colorScheme.onSurface)
                }
                HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                IconButton(onClick = { mapLibreMapRef.value?.animateCamera(CameraUpdateFactory.zoomOut()) }) {
                    Icon(Icons.Filled.ZoomOut, contentDescription = stringResource(R.string.map_zoom_out), tint = MaterialTheme.colorScheme.onSurface)
                }
                if (uiState.points.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.width(32.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    IconButton(onClick = { mapLibreMapRef.value?.let { fitAllPoints(it, uiState.points) } }) {
                        Icon(Icons.Filled.FitScreen, contentDescription = stringResource(R.string.map_fit_all_points), tint = MaterialTheme.colorScheme.onSurface)
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

                    // Pulsante Preferiti
                    IconButton(onClick = viewModel::toggleFavoritesFilter) {
                        Icon(
                            imageVector = if (uiState.showFavoritesOnly) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = stringResource(R.string.map_filter_favorites),
                            tint = if (uiState.showFavoritesOnly) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurface
                        )
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

                    // Tasto posizione utente: attiva il puck LocationComponent (accuracy circle + bearing)
                    // e centra la camera sulla posizione corrente.
                    IconButton(
                        onClick = {
                            if (locationPermission.status.isGranted) {
                                coroutineScope.launch {
                                    isCenteringActive = true
                                    val map = mapLibreMapRef.value
                                    val style = mapStyleRef.value
                                    if (map != null && style != null) {
                                        activateLocationPuck(context, map, style)
                                        locationPuckActive.value = true
                                    }
                                    val result = getFreshLocation(context)
                                    if (result != null && map != null) {
                                        val (lat, lon) = result
                                        // Mostra subito il puck con questa fix, senza aspettare il LocationEngine
                                        pushLocationToPuck(map, lat, lon)
                                        map.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), map.cameraPosition.zoom)
                                        )
                                    } else if (result == null) {
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


        // Pulsante Aggiungi Punto (uniformato agli altri controlli)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
            shadowElevation = 2.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            IconButton(
                onClick = { navController.navigate(Routes.AddEditPoint.createRoute()) }
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.map_add_point),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
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
                },
                onArchiveClick = { point ->
                    viewModel.archivePoint(point)
                },
                onDeleteClick = { point ->
                    viewModel.deletePoint(point)
                },
                onFavoriteClick = { point ->
                    viewModel.toggleFavorite(point)
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
            text = stringResource(R.string.map_cluster_points_in_area, points.size),
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
                                    stringResource(
                                        R.string.map_kml_distance_suffix,
                                        formatDistance(
                                            distanceBetween(
                                                userLat,
                                                userLon,
                                                items.first().pointLat,
                                                items.first().pointLon
                                            )
                                        )
                                    )
                                } else ""
                                Text(
                                    stringResource(R.string.map_kml_group_summary, items.size) +
                                        (if (activeCount > 0) {
                                            stringResource(R.string.map_kml_active_suffix, activeCount)
                                        } else "") +
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
                    .padding(horizontal = if (isOpen) 4.dp else 16.dp, vertical = if (isOpen) 4.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isOpen) {
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                }
                
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
                exit = ExitTransition.None
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

private fun fitAllPoints(map: MapLibreMap, points: List<GeoPoint>) {
    if (points.isEmpty()) return
    val lats = points.map { it.latitude }
    val lons = points.map { it.longitude }
    val latPad = maxOf(0.005, (lats.max() - lats.min()) * 0.25)
    val lonPad = maxOf(0.005, (lons.max() - lons.min()) * 0.25)
    val bounds = LatLngBounds.Builder()
        .include(LatLng(lats.max() + latPad, lons.max() + lonPad))
        .include(LatLng(lats.min() - latPad, lons.min() - lonPad))
        .build()
    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 120))
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
    map: MapLibreMap,
    style: Style,
    symbolManager: SymbolManager,
    symbolIdToTarget: MutableState<Map<Long, SymbolTarget>>,
    points: List<GeoPoint>,
    context: Context
) {
    val zoom = map.cameraPosition.zoom
    val clusters = clusterPoints(points, zoom)
    symbolManager.deleteAll()
    val newMap = mutableMapOf<Long, SymbolTarget>()

    clusters.forEach { cluster ->
        if (cluster.points.size == 1) {
            val point = cluster.points[0]
            val key = "bubble_${point.id}_${if (point.isFavorite) 1 else 0}"
            if (style.getImage(key) == null) {
                style.addImage(key, createCloudBubbleBitmap(context, point.emoji, point.title, point.isFavorite))
            }
            val symbol = symbolManager.create(
                SymbolOptions()
                    .withLatLng(LatLng(cluster.centerLat, cluster.centerLon))
                    .withIconImage(key)
                    .withIconAnchor("bottom")
                    .withIconOffset(arrayOf(0f, 0f))
            )
            newMap[symbol.id] = SymbolTarget.Single(point)
        } else {
            val key = "cluster_${cluster.points.size}"
            if (style.getImage(key) == null) {
                style.addImage(key, createClusterBitmap(context, cluster.points.size))
            }
            val symbol = symbolManager.create(
                SymbolOptions()
                    .withLatLng(LatLng(cluster.centerLat, cluster.centerLon))
                    .withIconImage(key)
                    .withIconAnchor("center")
            )
            newMap[symbol.id] = SymbolTarget.Cluster(cluster.points)
        }
    }
    symbolIdToTarget.value = newMap
}

/**
 * Installa il click listener sul [SymbolManager] principale. Dispatch:
 *  - [SymbolTarget.Single] → animate camera + apre bottom sheet
 *  - [SymbolTarget.Cluster] → zoom a bbox del cluster, oppure picker se i punti
 *    sono troppo vicini da separare visivamente (< [MIN_SEPARABLE_DEG]).
 */
private fun installSymbolClickListener(
    symbolManager: SymbolManager,
    map: MapLibreMap,
    symbolIdToTarget: MutableState<Map<Long, SymbolTarget>>,
    viewModel: MapViewModel,
    clusterPickerRef: MutableState<List<GeoPoint>?>
) {
    symbolManager.addClickListener { symbol ->
        when (val target = symbolIdToTarget.value[symbol.id]) {
            is SymbolTarget.Single -> {
                val point = target.point
                val targetZoom = if (map.cameraPosition.zoom < 17.0) 17.0 else map.cameraPosition.zoom
                map.animateCamera(
                    CameraUpdateFactory.newLatLngZoom(
                        LatLng(point.latitude, point.longitude), targetZoom
                    ), 800
                )
                viewModel.onPointSelected(point)
            }
            is SymbolTarget.Cluster -> {
                val pts = target.points
                val lats = pts.map { it.latitude }
                val lons = pts.map { it.longitude }
                val latRange = lats.max() - lats.min()
                val lonRange = lons.max() - lons.min()
                if (latRange < MIN_SEPARABLE_DEG && lonRange < MIN_SEPARABLE_DEG) {
                    clusterPickerRef.value = pts
                } else {
                    val latPad = maxOf(0.002, latRange * 0.3)
                    val lonPad = maxOf(0.002, lonRange * 0.3)
                    val bounds = LatLngBounds.Builder()
                        .include(LatLng(lats.max() + latPad, lons.max() + lonPad))
                        .include(LatLng(lats.min() - latPad, lons.min() - lonPad))
                        .build()
                    map.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 80))
                }
            }
            null -> { /* simbolo sconosciuto, ignora */ }
        }
        true
    }
}

/**
 * Converte createCloudBubbleDrawable in Bitmap (riutilizza la logica Canvas identica).
 */
private fun createCloudBubbleBitmap(
    context: Context,
    emoji: String,
    title: String,
    isFavorite: Boolean = false
): android.graphics.Bitmap = createCloudBubbleDrawable(context, emoji, title, isFavorite).bitmap

/**
 * Converte createClusterDrawable in Bitmap.
 */
private fun createClusterBitmap(context: Context, count: Int): android.graphics.Bitmap =
    createClusterDrawable(context, count).bitmap

/**
 * Pin Material per marker singolo: badge arrotondato (emoji + titolo) con codina triangolare.
 * Bordo dorato + stella per preferiti, bordo verde per normali.
 */
private fun createCloudBubbleDrawable(
    context: Context,
    emoji: String,
    title: String,
    isFavorite: Boolean = false
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

    val badgeR = if (isFavorite) 7f * d else 0f
    val bodyW = paddingH + emojiSize + gap + titleW + paddingH
    val bodyH = paddingV + emojiSize + paddingV
    val totalW = (bodyW + shadowDx + 2f * d + badgeR).toInt()
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

    // Bordo: dorato per preferiti, verde per normali
    val borderColor = if (isFavorite) AndroidColor.argb(255, 210, 160, 0) else AndroidColor.argb(255, 56, 102, 65)
    canvas.drawPath(pinPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = borderColor
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    })

    // Badge circolare dorato in alto a destra (sovrapposto all'angolo del fumetto)
    if (isFavorite) {
        val bx = bodyW - badgeR * 0.45f
        val by = badgeR + 2f * d
        canvas.drawCircle(bx, by, badgeR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.argb(255, 220, 165, 0)
            style = Paint.Style.FILL
        })
        canvas.drawCircle(bx, by, badgeR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 1.2f * d
        })
        canvas.drawText("★", bx, by + badgeR * 0.38f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = AndroidColor.WHITE
            textSize = badgeR * 1.15f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        })
    }

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
 * Attiva il [LocationComponent] built-in di MapLibre con render mode bussola
 * (puck + bearing arrow). Idempotente: safe da chiamare più volte.
 * Va ri-invocato dopo ogni [MapLibreMap.setStyle] perché il componente è legato allo Style.
 *
 * Richiede permesso `ACCESS_FINE_LOCATION` (controllato dal chiamante).
 */
@android.annotation.SuppressLint("MissingPermission")
private fun activateLocationPuck(context: Context, map: MapLibreMap, style: Style) {
    runCatching {
        val lc = map.locationComponent
        lc.activateLocationComponent(
            LocationComponentActivationOptions.builder(context, style)
                .useDefaultLocationEngine(true)
                .build()
        )
        lc.isLocationComponentEnabled = true
        lc.renderMode = RenderMode.COMPASS
        lc.cameraMode = CameraMode.NONE
    }.onFailure {
        android.util.Log.w("MapScreen", "activateLocationPuck failed: ${it.message}", it)
    }
}

/**
 * Spinge una posizione fresca al [LocationComponent] senza aspettare il prossimo
 * fix del LocationEngine — il puck compare subito con la posizione passata invece
 * di restare invisibile finché il provider non produce un fix.
 */
@android.annotation.SuppressLint("MissingPermission")
private fun pushLocationToPuck(map: MapLibreMap, lat: Double, lon: Double) {
    runCatching {
        val lc = map.locationComponent
        if (lc.isLocationComponentActivated && lc.isLocationComponentEnabled) {
            val loc = android.location.Location("manual").apply {
                latitude = lat
                longitude = lon
                time = System.currentTimeMillis()
                accuracy = 10f
            }
            lc.forceLocationUpdate(loc)
        }
    }
}
