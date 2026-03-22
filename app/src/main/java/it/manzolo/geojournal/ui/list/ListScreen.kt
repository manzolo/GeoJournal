package it.manzolo.geojournal.ui.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.content.FileProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Surface
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import it.manzolo.geojournal.R
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.draw.clip
import it.manzolo.geojournal.ui.theme.Terracotta
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.ui.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ListScreen(navController: NavController) {
    val viewModel: ListViewModel = hiltViewModel()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareChooserLabel = stringResource(R.string.point_share)
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var deleteConfirmPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var archiveConfirmPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var deleteTagConfirm by remember { mutableStateOf<String?>(null) }
    var tagsExpanded by remember { mutableStateOf(false) }

    // Emette il file .geoj da condividere via share sheet
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
            context.startActivity(Intent.createChooser(intent, shareChooserLabel))
        }
    }

    // Menu contestuale (tap lungo)
    contextMenuPoint?.let { point ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { contextMenuPoint = null },
            title = { Text("${point.emoji} ${point.title}") },
            text = {
                Column {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.list_view_on_map)) },
                        leadingIcon = { Icon(Icons.Filled.Map, null) },
                        onClick = {
                            contextMenuPoint = null
                            navigateToMapFocus(navController, point.latitude, point.longitude, point.id)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.point_open_google_maps)) },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                        onClick = {
                            contextMenuPoint = null
                            val uri = Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}(${Uri.encode(point.title)})")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.point_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            contextMenuPoint = null
                            navController.navigate(Routes.AddEditPoint.createRoute(point.id))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.point_share)) },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = {
                            contextMenuPoint = null
                            viewModel.prepareShare(point)
                        }
                    )
                    if (state.showArchived) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_unarchive_point)) },
                            leadingIcon = { Icon(Icons.Filled.Unarchive, null) },
                            onClick = {
                                contextMenuPoint = null
                                viewModel.unarchivePoint(point)
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.list_archive_point)) },
                            leadingIcon = { Icon(Icons.Filled.Archive, null) },
                            onClick = {
                                contextMenuPoint = null
                                archiveConfirmPoint = point
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.point_delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            contextMenuPoint = null
                            deleteConfirmPoint = point
                        }
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { contextMenuPoint = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Dialog conferma archivio
    archiveConfirmPoint?.let { point ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { archiveConfirmPoint = null },
            title = { Text(stringResource(R.string.list_archive_confirm_title)) },
            text = { Text(stringResource(R.string.list_archive_confirm_message, point.title)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.archivePoint(point)
                        archiveConfirmPoint = null
                    }
                ) {
                    Text(stringResource(R.string.list_archive_point))
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { archiveConfirmPoint = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Dialog conferma eliminazione
    deleteConfirmPoint?.let { point ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirmPoint = null },
            title = { Text(stringResource(R.string.list_delete_point_title)) },
            text = { Text(stringResource(R.string.list_delete_point_message, point.title)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deletePoint(point)
                        deleteConfirmPoint = null
                    }
                ) {
                    Text(stringResource(R.string.point_delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteConfirmPoint = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Dialog conferma eliminazione tag
    deleteTagConfirm?.let { tag ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTagConfirm = null },
            title = { Text(stringResource(R.string.list_delete_tag_title)) },
            text = { Text(stringResource(R.string.list_delete_tag_message, tag)) },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deleteTag(tag)
                        deleteTagConfirm = null
                    }
                ) {
                    Text(stringResource(R.string.list_delete_tag_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteTagConfirm = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.list_title)) },
                actions = {
                    IconButton(onClick = viewModel::toggleArchiveView) {
                        Icon(
                            imageVector = if (state.showArchived) Icons.Filled.Unarchive else Icons.Filled.Archive,
                            contentDescription = stringResource(R.string.list_show_archived),
                            tint = if (state.showArchived) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = stringResource(R.string.list_sort))
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(stringResource(order.labelRes)) },
                                onClick = {
                                    viewModel.setSortOrder(order)
                                    showSortMenu = false
                                },
                                leadingIcon = if (state.sortOrder == order) {
                                    { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                } else null
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { navController.navigate(Routes.AddEditPoint.createRoute()) }
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_point_title))
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                placeholder = { Text(stringResource(R.string.list_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.list_search_clear))
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.Transparent,
                )
            )

            if (state.allTags.isNotEmpty()) {
                val visibleTags = if (tagsExpanded) state.allTags else state.allTags.take(5)
                val hiddenCount = state.allTags.size - 5
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    items(visibleTags) { tag ->
                        val selected = tag in state.selectedTags
                        Surface(
                            modifier = Modifier.combinedClickable(
                                onClick = { viewModel.toggleTag(tag) },
                                onLongClick = { deleteTagConfirm = tag }
                            ),
                            shape = RoundedCornerShape(16.dp),
                            color = if (selected) MaterialTheme.colorScheme.secondaryContainer
                                    else MaterialTheme.colorScheme.surface,
                            border = if (selected) null
                                     else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer
                                        else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                    if (!tagsExpanded && hiddenCount > 0) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { tagsExpanded = true },
                                label = { Text("+$hiddenCount") },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                    if (tagsExpanded && state.allTags.size > 5) {
                        item {
                            FilterChip(
                                selected = false,
                                onClick = { tagsExpanded = false },
                                label = { Text(stringResource(R.string.list_tags_collapse)) },
                                shape = RoundedCornerShape(16.dp)
                            )
                        }
                    }
                }
            }

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                state.points.isEmpty() -> {
                    EmptyState(
                        hasFilters = state.query.isNotEmpty() || state.selectedTags.isNotEmpty(),
                        isArchiveView = state.showArchived
                    )
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        item {
                            BuyMeCoffeeBanner(context = context)
                        }
                        items(state.points, key = { it.id }) { point ->
                            GeoPointCard(
                                point = point,
                                onClick = { navController.navigate(Routes.PointDetail.createRoute(point.id)) },
                                onLongClick = { contextMenuPoint = point }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GeoPointCard(point: GeoPoint, onClick: () -> Unit, onLongClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val photoUrl = point.photoUrls.firstOrNull()
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (photoUrl != null) {
                    AsyncImage(
                        model = if (photoUrl.startsWith("/")) File(photoUrl) else photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(
                        text = point.emoji,
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (point.description.isNotBlank()) {
                    Text(
                        text = point.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = point.createdAt.toDisplayDate(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    if (point.rating > 0) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Terracotta
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${point.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Terracotta
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasFilters: Boolean, isArchiveView: Boolean = false) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = when {
                    hasFilters -> "🔍"
                    isArchiveView -> "📦"
                    else -> "📋"
                },
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = when {
                    hasFilters -> stringResource(R.string.list_empty_filtered)
                    isArchiveView -> stringResource(R.string.list_archived_empty)
                    else -> stringResource(R.string.empty_list_title)
                },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = when {
                    hasFilters -> stringResource(R.string.list_empty_filtered_hint)
                    isArchiveView -> stringResource(R.string.list_archived_empty_hint)
                    else -> stringResource(R.string.empty_list_subtitle)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun Date.toDisplayDate(): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(this)

@Composable
private fun BuyMeCoffeeBanner(context: Context) {
    val url = androidx.compose.ui.res.stringResource(it.manzolo.geojournal.R.string.buy_me_coffee_url)
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth(),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("☕", style = MaterialTheme.typography.titleLarge)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = androidx.compose.ui.res.stringResource(it.manzolo.geojournal.R.string.support_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = androidx.compose.ui.res.stringResource(it.manzolo.geojournal.R.string.support_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            androidx.compose.material3.TextButton(
                onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            ) {
                Text(
                    text = androidx.compose.ui.res.stringResource(it.manzolo.geojournal.R.string.support_button),
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

private fun navigateToMapFocus(navController: NavController, lat: Double, lon: Double, pointId: String) {
    it.manzolo.geojournal.ui.map.MapViewModel.FocusRequest.send(lat, lon, pointId)
    navController.navigate(Routes.Map.route) {
        popUpTo(Routes.Map.route) { inclusive = false }
        launchSingleTop = true
    }
}
