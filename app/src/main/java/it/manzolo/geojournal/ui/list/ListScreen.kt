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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var showSortMenu by remember { mutableStateOf(false) }
    var contextMenuPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var deleteConfirmPoint by remember { mutableStateOf<GeoPoint?>(null) }

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
            context.startActivity(Intent.createChooser(intent, "Condividi punto"))
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
                        text = { Text("Vedi sulla mappa") },
                        leadingIcon = { Icon(Icons.Filled.Map, null) },
                        onClick = {
                            contextMenuPoint = null
                            navigateToMapFocus(navController, point.latitude, point.longitude, point.id)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Apri in Google Maps") },
                        leadingIcon = { Icon(Icons.Filled.LocationOn, null) },
                        onClick = {
                            contextMenuPoint = null
                            val uri = Uri.parse("geo:${point.latitude},${point.longitude}?q=${point.latitude},${point.longitude}(${Uri.encode(point.title)})")
                            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Modifica") },
                        leadingIcon = { Icon(Icons.Filled.Edit, null) },
                        onClick = {
                            contextMenuPoint = null
                            navController.navigate(Routes.AddEditPoint.createRoute(point.id))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Condividi") },
                        leadingIcon = { Icon(Icons.Filled.Share, null) },
                        onClick = {
                            contextMenuPoint = null
                            viewModel.prepareShare(point)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Elimina", color = MaterialTheme.colorScheme.error) },
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
                    Text("Annulla")
                }
            }
        )
    }

    // Dialog conferma eliminazione
    deleteConfirmPoint?.let { point ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteConfirmPoint = null },
            title = { Text("Elimina punto") },
            text = { Text("Vuoi eliminare \"${point.title}\"? L'operazione non è reversibile.") },
            confirmButton = {
                androidx.compose.material3.TextButton(
                    onClick = {
                        viewModel.deletePoint(point)
                        deleteConfirmPoint = null
                    }
                ) {
                    Text("Elimina", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { deleteConfirmPoint = null }) {
                    Text("Annulla")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Il tuo diario") },
                actions = {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Ordina")
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                text = { Text(order.label) },
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
                Icon(Icons.Default.Add, contentDescription = "Nuovo punto")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Cerca luoghi...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.updateQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancella")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            if (state.allTags.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    items(state.allTags) { tag ->
                        FilterChip(
                            selected = tag in state.selectedTags,
                            onClick = { viewModel.toggleTag(tag) },
                            label = { Text(tag) }
                        )
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
                    EmptyState(hasFilters = state.query.isNotEmpty() || state.selectedTags.isNotEmpty())
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
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp)) {
            Text(
                text = point.emoji,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(end = 12.dp, top = 2.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = point.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (point.description.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = point.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (point.tags.isNotEmpty()) {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        items(point.tags) { tag ->
                            SuggestionChip(
                                onClick = {},
                                label = {
                                    Text(tag, style = MaterialTheme.typography.labelSmall)
                                }
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = point.createdAt.toDisplayDate(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(10.dp))
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        text = "%.4f, %.4f".format(point.latitude, point.longitude),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (point.rating > 0) {
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.width(2.dp))
                        Text(
                            text = "${point.rating}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyState(hasFilters: Boolean) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasFilters) "🔍" else "📋",
                style = MaterialTheme.typography.displayMedium
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = if (hasFilters) "Nessun risultato" else "Il tuo diario è vuoto",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (hasFilters)
                    "Prova a modificare la ricerca o i filtri"
                else
                    "Inizia ad esplorare e a salvare\ni posti che ami 🗺️",
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
