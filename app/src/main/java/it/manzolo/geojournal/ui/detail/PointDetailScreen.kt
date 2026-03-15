package it.manzolo.geojournal.ui.detail

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import it.manzolo.geojournal.domain.model.GeoPoint
import it.manzolo.geojournal.ui.navigation.Routes
import java.text.SimpleDateFormat
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
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PointDetailContent(point: GeoPoint, modifier: Modifier = Modifier) {
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

        // Foto (placeholder)
        if (point.photoUrls.isNotEmpty()) {
            HorizontalDivider()
            Text(
                text = "Foto (${point.photoUrls.size})",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
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
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
