package it.manzolo.geojournal.ui.detail

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.ui.navigation.Routes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ITALIAN)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PointDetailScreen(
    navController: NavController,
    viewModel: PointDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.point?.title ?: "Dettaglio") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    uiState.point?.let { point ->
                        IconButton(onClick = {
                            navController.navigate(Routes.AddEditPoint.createRoute(point.id))
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Modifica")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            uiState.error != null -> Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                Text(uiState.error!!, color = MaterialTheme.colorScheme.error)
            }

            uiState.point != null -> PointDetailContent(
                point = uiState.point!!,
                visitLogs = uiState.visitLogs,
                reminders = uiState.reminders,
                onLogVisitToday = viewModel::logVisitToday,
                onDeleteVisitLog = viewModel::deleteVisitLog,
                onDeleteReminder = viewModel::deleteReminder,
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PointDetailContent(
    point: GeoPoint,
    visitLogs: List<VisitLogEntry>,
    reminders: List<Reminder>,
    onLogVisitToday: () -> Unit,
    onDeleteVisitLog: (VisitLogEntry) -> Unit,
    onDeleteReminder: (Reminder) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPhoto by remember { mutableStateOf<String?>(null) }

    selectedPhoto?.let { url ->
        PhotoViewerDialog(
            url = url,
            onDismiss = { selectedPhoto = null }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Emoji + titolo
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(point.emoji, style = MaterialTheme.typography.displaySmall)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = point.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }

        // Rating
        if (point.rating > 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                (1..5).forEach { star ->
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = if (star <= point.rating) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${point.rating}/5",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Tag
        if (point.tags.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                point.tags.forEach { tag ->
                    AssistChip(onClick = {}, label = { Text(tag) })
                }
            }
        }

        HorizontalDivider()

        // Descrizione
        if (point.description.isNotBlank()) {
            Text(
                text = point.description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            HorizontalDivider()
        }

        // Coordinate
        DetailRow(label = "Latitudine", value = "%.6f".format(point.latitude))
        DetailRow(label = "Longitudine", value = "%.6f".format(point.longitude))

        HorizontalDivider()

        // Date
        DetailRow(label = "Creato", value = dateFormat.format(point.createdAt))
        DetailRow(label = "Modificato", value = dateFormat.format(point.updatedAt))

        // Foto
        if (point.photoUrls.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Foto (${point.photoUrls.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3
            ) {
                point.photoUrls.forEach { url ->
                    AsyncImage(
                        model = if (url.startsWith("/")) File(url) else url,
                        contentDescription = null,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedPhoto = url },
                        contentScale = ContentScale.Crop
                    )
                }
            }
        }

        // Promemoria
        if (reminders.isNotEmpty()) {
            HorizontalDivider()
            val reminderDateFormat = remember { SimpleDateFormat("d MMM", Locale.ITALIAN) }
            Text(
                text = "Promemoria (${reminders.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(4.dp))
            reminders.forEach { reminder ->
                val dateStr = when (reminder.type) {
                    ReminderType.DATE_RANGE -> reminder.endDate?.let {
                        "${reminderDateFormat.format(Date(reminder.startDate))} → ${reminderDateFormat.format(Date(it))}"
                    } ?: reminderDateFormat.format(Date(reminder.startDate))
                    ReminderType.ANNUAL_RECURRING -> "${reminderDateFormat.format(Date(reminder.startDate))} · ogni anno"
                    ReminderType.SINGLE -> reminderDateFormat.format(Date(reminder.startDate))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("🔔", modifier = Modifier.padding(end = 8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(reminder.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Text(dateStr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { onDeleteReminder(reminder) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Rimuovi", modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // Registro visite
        HorizontalDivider()
        val visitDateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ITALIAN) }
        Text(
            text = "Registro visite (${visitLogs.size})",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        if (visitLogs.isNotEmpty()) {
            visitLogs.forEach { visit ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f))
                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            visitDateFormat.format(Date(visit.visitedAt)),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (visit.note.isNotBlank()) {
                            Text(visit.note, style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { onDeleteVisitLog(visit) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Rimuovi", modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
        OutlinedButton(onClick = onLogVisitToday, modifier = Modifier.fillMaxWidth()) {
            Text("📍  Sono stato qui oggi")
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun PhotoViewerDialog(url: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset = if (scale > 1f) offset + panChange else Offset.Zero
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // 1. Immagine (Layer Inferiore)
            AsyncImage(
                model = if (url.startsWith("/")) File(url) else url,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .transformable(transformState)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
                contentScale = ContentScale.Fit
            )

            // Barra superiore: share + salva a sinistra, chiudi a destra
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row {
                    IconButton(onClick = { scope.launch { sharePhoto(context, url) } }) {
                        Icon(Icons.Filled.Share, contentDescription = "Condividi", tint = Color.White)
                    }
                    IconButton(onClick = { scope.launch { saveToGallery(context, url) } }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = "Salva in galleria", tint = Color.White)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Chiudi", tint = Color.White)
                }
            }
        }
    }
}


private suspend fun loadBitmap(context: android.content.Context, url: String): android.graphics.Bitmap? {
    val request = ImageRequest.Builder(context)
        .data(if (url.startsWith("/")) File(url) else url)
        .allowHardware(false)
        .build()
    val result = context.imageLoader.execute(request)
    return (result as? SuccessResult)?.drawable?.toBitmap()
}

private suspend fun sharePhoto(context: android.content.Context, url: String) {
    val bitmap = loadBitmap(context, url) ?: run {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Impossibile caricare la foto", Toast.LENGTH_SHORT).show()
        }
        return
    }
    val cacheFile = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.jpg")
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
        file
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    withContext(Dispatchers.Main) {
        context.startActivity(Intent.createChooser(intent, "Condividi foto"))
    }
}

private suspend fun saveToGallery(context: android.content.Context, url: String) {
    val bitmap = loadBitmap(context, url) ?: run {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, "Impossibile caricare la foto", Toast.LENGTH_SHORT).show()
        }
        return
    }
    withContext(Dispatchers.IO) {
        val filename = "GeoJournal_${System.currentTimeMillis()}.jpg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/GeoJournal")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)
                    ?.use { out -> bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, out) }
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = android.os.Environment
                .getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val geoJournalDir = File(dir, "GeoJournal").also { it.mkdirs() }
            val file = File(geoJournalDir, filename)
            file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 95, it) }
            android.media.MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
        }
    }
    withContext(Dispatchers.Main) {
        Toast.makeText(context, "Foto salvata in galleria", Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}
