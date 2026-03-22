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
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.res.stringResource
import it.manzolo.geojournal.R
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import android.net.Uri
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.domain.model.Reminder
import it.manzolo.geojournal.domain.model.ReminderType
import it.manzolo.geojournal.domain.model.VisitLogEntry
import it.manzolo.geojournal.ui.map.MapViewModel
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
    val fallbackTitle = stringResource(R.string.detail_title_fallback)

    // Feature 1: naviga indietro automaticamente se il punto è eliminato/archiviato
    LaunchedEffect(uiState.isDeleted) {
        if (uiState.isDeleted) navController.popBackStack()
    }

    // Feature 2: dialog conferma eliminazione
    if (uiState.showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::toggleDeleteConfirm,
            title = { Text(stringResource(R.string.point_delete_confirm_title)) },
            text = { Text(stringResource(R.string.addedit_delete_message)) },
            confirmButton = {
                TextButton(onClick = viewModel::deletePoint) {
                    Text(stringResource(R.string.point_delete_confirm_button), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::toggleDeleteConfirm) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Feature 4: dialog conferma archivio / ripristino
    if (uiState.showArchiveConfirm) {
        val isArchived = uiState.point?.isArchived == true
        AlertDialog(
            onDismissRequest = viewModel::toggleArchiveConfirm,
            title = { Text(stringResource(if (isArchived) R.string.point_unarchive else R.string.point_archive_confirm_title)) },
            text = { if (!isArchived) Text(stringResource(R.string.point_archive_confirm_message)) },
            confirmButton = {
                TextButton(onClick = if (isArchived) viewModel::unarchivePoint else viewModel::archivePoint) {
                    Text(stringResource(if (isArchived) R.string.point_unarchive else R.string.point_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::toggleArchiveConfirm) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.point?.title ?: fallbackTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    uiState.point?.let { point ->
                        IconButton(onClick = {
                            navController.navigate(Routes.AddEditPoint.createRoute(point.id))
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.point_edit))
                        }
                        IconButton(onClick = viewModel::toggleDeleteConfirm) {
                            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.point_delete), tint = MaterialTheme.colorScheme.error)
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
                onArchiveToggle = viewModel::toggleArchiveConfirm,
                navController = navController,
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
    onArchiveToggle: () -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedPhoto by remember { mutableStateOf<String?>(null) }

    selectedPhoto?.let { url ->
        PhotoViewerDialog(url = url, onDismiss = { selectedPhoto = null })
    }

    var showDates by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        // ── Hero card: emoji + titolo + descrizione ───────────────────────────
        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(
                            androidx.compose.ui.graphics.Brush.verticalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.secondaryContainer
                                )
                            )
                        ),
                    contentAlignment = Alignment.BottomStart
                ) {
                    val photoUrl = point.photoUrls.firstOrNull()
                    if (photoUrl != null) {
                        AsyncImage(
                            model = if (photoUrl.startsWith("/")) File(photoUrl) else photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        // Gradient overlay per leggibilità header
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    androidx.compose.ui.graphics.Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f)),
                                        startY = 100f
                                    )
                                )
                        )
                    }
                    
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.25f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(point.emoji, style = MaterialTheme.typography.displaySmall)
                        }
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = point.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (photoUrl != null) Color.White else MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                
                if (point.description.isNotBlank()) {
                    Text(
                        text = point.description,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }

        // ── Posizione: coordinate e date tutte dietro (i) ────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(icon = Icons.Filled.LocationOn,
                        title = stringResource(R.string.detail_section_info),
                        modifier = Modifier.weight(1f))
                    IconButton(onClick = { showDates = !showDates }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Filled.Info, contentDescription = stringResource(R.string.detail_created),
                            modifier = Modifier.size(18.dp),
                            tint = if (showDates) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                AnimatedVisibility(visible = showDates) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        InfoRow(Icons.Filled.LocationOn, stringResource(R.string.detail_latitude),
                            "%.6f".format(point.latitude))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InfoRow(Icons.Filled.LocationOn, stringResource(R.string.detail_longitude),
                            "%.6f".format(point.longitude))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InfoRow(Icons.Filled.CalendarToday, stringResource(R.string.detail_created),
                            dateFormat.format(point.createdAt))
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        InfoRow(Icons.Filled.Edit, stringResource(R.string.detail_updated),
                            dateFormat.format(point.updatedAt))
                    }
                }
            }
        }

        // ── Foto ──────────────────────────────────────────────────────────────
        if (point.photoUrls.isNotEmpty()) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(icon = Icons.Filled.PhotoLibrary,
                        title = stringResource(R.string.detail_section_photos, point.photoUrls.size))
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { selectedPhoto = url },
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            }
        }

        // ── Promemoria ────────────────────────────────────────────────────────
        if (reminders.isNotEmpty()) {
            val reminderDateFormat = remember { SimpleDateFormat("d MMM", Locale.ITALIAN) }
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(icon = Icons.Filled.Notifications,
                        title = stringResource(R.string.detail_section_reminders, reminders.size))
                    reminders.forEach { reminder ->
                        val dateStr = when (reminder.type) {
                            ReminderType.DATE_RANGE -> reminder.endDate?.let {
                                "${reminderDateFormat.format(Date(reminder.startDate))} → ${reminderDateFormat.format(Date(it))}"
                            } ?: reminderDateFormat.format(Date(reminder.startDate))
                            ReminderType.ANNUAL_RECURRING -> "${reminderDateFormat.format(Date(reminder.startDate))} · ${context.getString(R.string.reminder_annual_suffix)}"
                            ReminderType.SINGLE -> reminderDateFormat.format(Date(reminder.startDate))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Notifications, contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(reminder.title, style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold)
                                Text(dateStr, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onDeleteReminder(reminder) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove),
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (reminder != reminders.last()) HorizontalDivider()
                    }
                }
            }
        }

        // ── Registro visite ───────────────────────────────────────────────────
        val visitDateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ITALIAN) }
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionHeader(icon = Icons.Filled.CheckCircle,
                        title = stringResource(R.string.detail_section_visits, visitLogs.size),
                        modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = onLogVisitToday) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.detail_visited_today),
                            style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (visitLogs.isNotEmpty()) {
                    visitLogs.forEachIndexed { i, visit ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(visitDateFormat.format(Date(visit.visitedAt)),
                                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                if (visit.note.isNotBlank()) {
                                    Text(visit.note, style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            IconButton(onClick = { onDeleteVisitLog(visit) }, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_remove),
                                    modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (i < visitLogs.lastIndex) HorizontalDivider()
                    }
                }
            }
        }

        // ── Tag ───────────────────────────────────────────────────────────────
        if (point.tags.isNotEmpty()) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(icon = Icons.AutoMirrored.Filled.Label, title = stringResource(R.string.detail_section_tags))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        point.tags.forEach { tag ->
                            AssistChip(onClick = {}, label = { Text(tag) })
                        }
                    }
                }
            }
        }

        // ── Valutazione ───────────────────────────────────────────────────────
        if (point.rating > 0) {
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    SectionHeader(icon = Icons.Filled.Star, title = stringResource(R.string.detail_section_rating))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        (1..5).forEach { star ->
                            Icon(
                                imageVector = if (star <= point.rating) Icons.Filled.Star else Icons.Filled.StarBorder,
                                contentDescription = null,
                                tint = if (star <= point.rating) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("${point.rating}/5",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        // ── Azioni ────────────────────────────────────────────────────────────
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            MapViewModel.FocusRequest.send(point.latitude, point.longitude, point.id)
                            navController.popBackStack(Routes.Map.route, inclusive = false)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.MyLocation, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.point_navigate_on_map), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            val uri = Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}(${Uri.encode(point.title)})")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.point_open_google_maps), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.material3.FilledTonalButton(
                        onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "https://maps.google.com/?q=${point.latitude},${point.longitude}")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, null))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.point_share_location), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                    androidx.compose.material3.FilledTonalButton(
                        onClick = onArchiveToggle,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = if (point.isArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(if (point.isArchived) R.string.point_unarchive else R.string.point_archive),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(top = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun InfoRow(icon: ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp)
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
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
                        Icon(Icons.Filled.Share, contentDescription = stringResource(R.string.point_share), tint = Color.White)
                    }
                    IconButton(onClick = { scope.launch { saveToGallery(context, url) } }) {
                        Icon(Icons.Filled.FileDownload, contentDescription = stringResource(R.string.detail_save_to_gallery), tint = Color.White)
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.action_close), tint = Color.White)
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
            Toast.makeText(context, context.getString(R.string.detail_photo_error), Toast.LENGTH_SHORT).show()
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
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.detail_share_photo)))
    }
}

private suspend fun saveToGallery(context: android.content.Context, url: String) {
    val bitmap = loadBitmap(context, url) ?: run {
        withContext(Dispatchers.Main) {
            Toast.makeText(context, context.getString(R.string.detail_photo_error), Toast.LENGTH_SHORT).show()
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
        Toast.makeText(context, context.getString(R.string.detail_photo_saved), Toast.LENGTH_SHORT).show()
    }
}
