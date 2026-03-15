package it.manzolo.geojournal.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import it.manzolo.geojournal.R
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.ui.components.PointBottomSheet
import it.manzolo.geojournal.ui.navigation.Routes
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@Composable
fun MapScreen(
    navController: NavController,
    viewModel: MapViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val mapView = remember {
        Configuration.getInstance().userAgentValue = context.packageName
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(13.0)
            controller.setCenter(OsmGeoPoint(45.4654219, 9.1859243))
        }
    }

    DisposableEffect(Unit) {
        onDispose { mapView.onDetach() }
    }

    LaunchedEffect(uiState.points) {
        updateMapMarkers(mapView, uiState.points, context) { point ->
            viewModel.onPointSelected(point)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        // FAB posizione utente
        SmallFloatingActionButton(
            onClick = { /* Fase 3: implementare richiesta posizione GPS */ },
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

        // FAB aggiungi punto
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

        // Bottom sheet con dettagli del punto selezionato
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
                }
            )
        }
    }
}

private fun updateMapMarkers(
    mapView: MapView,
    points: List<GeoPoint>,
    context: Context,
    onMarkerClick: (GeoPoint) -> Unit
) {
    mapView.overlays.removeAll { it is Marker }

    points.forEach { point ->
        val marker = Marker(mapView).apply {
            position = OsmGeoPoint(point.latitude, point.longitude)
            title = point.title
            snippet = point.description
            icon = createSpeechBubbleDrawable(context, point.emoji, point.title)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            setOnMarkerClickListener { _, _ ->
                onMarkerClick(point)
                true
            }
        }
        mapView.overlays.add(marker)
    }
    mapView.invalidate()
}

private fun createSpeechBubbleDrawable(
    context: Context,
    emoji: String,
    title: String
): BitmapDrawable {
    val density = context.resources.displayMetrics.density
    val bubbleWidth = (140 * density).toInt()
    val bubbleHeight = (52 * density).toInt()
    val tailH = (14 * density).toInt()
    val radius = 14f * density
    val pad = 8f * density

    val totalHeight = bubbleHeight + tailH
    val bitmap = Bitmap.createBitmap(bubbleWidth, totalHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // Ombra morbida
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(50, 0, 0, 0)
        style = Paint.Style.FILL
    }
    canvas.drawRoundRect(
        RectF(3f * density, 3f * density, bubbleWidth - 1f * density, bubbleHeight - 1f * density),
        radius, radius, shadowPaint
    )

    // Sfondo bubble bianco
    val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    val bubbleRect = RectF(0f, 0f, bubbleWidth.toFloat(), bubbleHeight.toFloat())
    canvas.drawRoundRect(bubbleRect, radius, radius, bgPaint)

    // Coda triangolare in basso al centro
    val tailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.WHITE
        style = Paint.Style.FILL
    }
    val tailPath = Path().apply {
        val cx = bubbleWidth / 2f
        moveTo(cx - tailH * 0.8f, bubbleHeight.toFloat() - 2f * density)
        lineTo(cx, totalHeight.toFloat())
        lineTo(cx + tailH * 0.8f, bubbleHeight.toFloat() - 2f * density)
        close()
    }
    canvas.drawPath(tailPath, tailPaint)

    // Bordo verde
    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(255, 46, 125, 50)
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
    }
    canvas.drawRoundRect(bubbleRect, radius, radius, borderPaint)

    // Emoji
    val emojiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * density
        textAlign = Paint.Align.LEFT
    }
    canvas.drawText(emoji, pad, bubbleHeight / 2f + 9f * density, emojiPaint)

    // Titolo (troncato se troppo lungo)
    val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = AndroidColor.argb(255, 27, 36, 20)
        textSize = 9.5f * density
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.LEFT
    }
    val maxW = bubbleWidth - (pad * 2) - (28f * density)
    var displayTitle = title
    while (titlePaint.measureText(displayTitle) > maxW && displayTitle.length > 3) {
        displayTitle = displayTitle.dropLast(1)
    }
    if (displayTitle.length < title.length) displayTitle = displayTitle.dropLast(2) + "…"
    canvas.drawText(displayTitle, pad + 26f * density, bubbleHeight / 2f + 9f * density, titlePaint)

    return BitmapDrawable(context.resources, bitmap)
}
