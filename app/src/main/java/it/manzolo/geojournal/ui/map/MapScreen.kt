package it.manzolo.geojournal.ui.map

import android.Manifest
import android.content.Context
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import it.manzolo.geojournal.R
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.ui.components.PointBottomSheet
import it.manzolo.geojournal.ui.navigation.Routes
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import org.osmdroid.config.Configuration
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

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
    val locationPermission = rememberPermissionState(Manifest.permission.ACCESS_FINE_LOCATION)

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

    // Ref aggiornabile per avere sempre i punti correnti nel listener di zoom
    val pointsRef = remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    // Punti del cluster troppo vicini da separare visivamente → mostra il picker
    val clusterPickerRef = remember { mutableStateOf<List<GeoPoint>?>(null) }

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            zoomController.setVisibility(
                org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER
            )
            controller.setZoom(13.0)
            controller.setCenter(OsmGeoPoint(45.4654219, 9.1859243))
        }
    }

    // Listener di zoom: ri-clustera quando cambia il livello intero di zoom
    LaunchedEffect(Unit) {
        var lastZoom = -1
        mapView.addMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent) = false
            override fun onZoom(event: ZoomEvent): Boolean {
                val z = mapView.zoomLevelDouble.toInt()
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

    LaunchedEffect(uiState.points) {
        pointsRef.value = uiState.points
        updateClusteredMarkers(
            mapView, uiState.points, context,
            onMarkerClick = { viewModel.onPointSelected(it) },
            onClusterTooClose = { clusterPickerRef.value = it }
        )
    }

    // Focus su punto specifico (es. da Lista): centra + auto-seleziona il punto
    LaunchedEffect(uiState.focusTarget) {
        uiState.focusTarget?.let { target ->
            mapView.controller.animateTo(OsmGeoPoint(target.lat, target.lon))
            mapView.controller.setZoom(17.0)
            target.pointId?.let { id ->
                uiState.points.find { it.id == id }?.let { viewModel.onPointSelected(it) }
            }
            viewModel.clearFocusTarget()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // Controlli zoom + inquadra tutto (lato sinistro)
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { mapView.controller.zoomIn() },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Filled.ZoomIn, contentDescription = "Zoom avanti")
            }
            SmallFloatingActionButton(
                onClick = { mapView.controller.zoomOut() },
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(Icons.Filled.ZoomOut, contentDescription = "Zoom indietro")
            }
            if (uiState.points.isNotEmpty()) {
                SmallFloatingActionButton(
                    onClick = { fitAllPoints(mapView, uiState.points) },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Filled.FitScreen, contentDescription = "Inquadra tutti i punti")
                }
            }
        }

        // FAB posizione utente (lato destro)
        SmallFloatingActionButton(
            onClick = {
                if (locationPermission.status.isGranted) {
                    centerMapOnUserLocation(context, mapView)
                } else {
                    locationPermission.launchPermissionRequest()
                }
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = 88.dp),
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Icon(
                imageVector = Icons.Filled.MyLocation,
                contentDescription = stringResource(R.string.map_my_location)
            )
        }

        LaunchedEffect(locationPermission.status.isGranted) {
            if (locationPermission.status.isGranted && uiState.focusTarget == null) {
                centerMapOnUserLocation(context, mapView)
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
                    viewModel.prepareShare(point)
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

private fun centerMapOnUserLocation(context: Context, mapView: MapView) {
    try {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        location?.let {
            mapView.controller.animateTo(OsmGeoPoint(it.latitude, it.longitude))
        }
    } catch (_: SecurityException) { /* permesso revocato */ }
}

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
        val seed = remaining.removeFirst()
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
    mapView.overlays.removeAll { it is Marker }

    clusters.forEach { cluster ->
        val marker = Marker(mapView).apply {
            position = OsmGeoPoint(cluster.centerLat, cluster.centerLon)
            if (cluster.points.size == 1) {
                val point = cluster.points[0]
                icon = createCloudBubbleDrawable(context, point.emoji, point.title)
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                setOnMarkerClickListener { _, _ ->
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
 * Nuvoletta per marker singolo: corpo + 4 bumps fusi in un'unica sagoma via Path.Op.UNION
 * così non appaiono cerchi separati con bordo individuale.
 */
private fun createCloudBubbleDrawable(
    context: Context,
    emoji: String,
    title: String
): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val bodyW = (140 * d).toInt()
    val bodyH = (48 * d).toInt()
    val bumpR = 10f * d
    val numBumps = 4
    val tailH = (13 * d).toInt()
    val bumpOverhang = (bumpR * 0.5f).toInt()
    val totalH = bumpOverhang + bodyH + tailH
    val bodyTop = bumpOverhang.toFloat()

    val bitmap = Bitmap.createBitmap(bodyW, totalH, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Corpo arrotondato
    val bodyRect = RectF(0f, bodyTop, bodyW.toFloat(), bodyTop + bodyH)
    val cloudPath = Path().apply {
        addRoundRect(bodyRect, 14f * d, 14f * d, Path.Direction.CW)
    }

    // Bumps in cima — uniti al corpo con UNION (nessun bordo interno visibile)
    val bumpSpacing = bodyW.toFloat() / (numBumps + 1)
    val bumpCy = bodyTop + bumpR * 0.5f
    for (i in 1..numBumps) {
        cloudPath.op(
            Path().apply { addCircle(bumpSpacing * i, bumpCy, bumpR, Path.Direction.CW) },
            Path.Op.UNION
        )
    }

    // Codina triangolare — unita al corpo
    val cx = bodyW / 2f
    cloudPath.op(
        Path().apply {
            moveTo(cx - tailH * 0.75f, bodyTop + bodyH - 2f * d)
            lineTo(cx, (bodyTop + bodyH + tailH))
            lineTo(cx + tailH * 0.75f, bodyTop + bodyH - 2f * d)
            close()
        },
        Path.Op.UNION
    )

    // Ombra
    canvas.save()
    canvas.translate(2f * d, 2f * d)
    canvas.drawPath(cloudPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(35, 0, 0, 0)
        style = Paint.Style.FILL
    })
    canvas.restore()

    // Riempimento bianco
    canvas.drawPath(cloudPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    })

    // Bordo verde unico sull'intera sagoma
    canvas.drawPath(cloudPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(255, 46, 125, 50)
        style = Paint.Style.STROKE
        strokeWidth = 2f * d
    })

    // Emoji + titolo
    val textY = bodyTop + bodyH / 2f + 8f * d
    canvas.drawText(emoji, 8f * d, textY, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * d
        textAlign = Paint.Align.LEFT
    })

    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(255, 27, 36, 20)
        textSize = 9.5f * d
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    val maxW = bodyW - 16f * d - 28f * d
    var displayTitle = title
    while (titlePaint.measureText(displayTitle) > maxW && displayTitle.length > 3) {
        displayTitle = displayTitle.dropLast(1)
    }
    if (displayTitle.length < title.length) displayTitle = displayTitle.dropLast(2) + "…"
    canvas.drawText(displayTitle, 34f * d, textY, titlePaint)

    return BitmapDrawable(context.resources, bitmap)
}

/**
 * Icona cluster: cerchio centrale + 8 bumps fusi con Path.Op.UNION → nuvoletta verde
 * senza cerchi separati visibili. Il conteggio è scritto in bianco al centro.
 */
private fun createClusterDrawable(context: Context, count: Int): BitmapDrawable {
    val d = context.resources.displayMetrics.density
    val mainR = 26f * d
    val bumpR = 9f * d
    val pad = bumpR + 4f * d
    val size = ((mainR + pad) * 2).toInt()
    val cx = size / 2f
    val cy = size / 2f

    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Cerchio centrale
    val cloudPath = Path().apply { addCircle(cx, cy, mainR, Path.Direction.CW) }

    // 8 bumps a 45° — uniti con UNION
    val bumpDist = mainR * 0.82f
    for (i in 0 until 8) {
        val rad = i * 45.0 * PI / 180.0
        val bx = cx + bumpDist * cos(rad).toFloat()
        val by = cy + bumpDist * sin(rad).toFloat()
        cloudPath.op(
            Path().apply { addCircle(bx, by, bumpR, Path.Direction.CW) },
            Path.Op.UNION
        )
    }

    // Ombra
    canvas.save()
    canvas.translate(2f * d, 2f * d)
    canvas.drawPath(cloudPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(40, 0, 0, 0)
        style = Paint.Style.FILL
    })
    canvas.restore()

    // Riempimento verde
    canvas.drawPath(cloudPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(235, 46, 125, 50)
        style = Paint.Style.FILL
    })

    // Bordo verde scuro
    canvas.drawPath(cloudPath, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(255, 27, 94, 32)
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * d
    })

    // Conteggio
    val label = if (count > 99) "99+" else count.toString()
    canvas.drawText(label, cx, cy + 5f * d, Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        textSize = 14f * d
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    })

    return BitmapDrawable(context.resources, bitmap)
}
