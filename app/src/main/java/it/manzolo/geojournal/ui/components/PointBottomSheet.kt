package it.manzolo.geojournal.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.manzolo.geojournal.R
import it.manzolo.geojournal.domain.model.GeoPoint
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PointBottomSheet(
    point: GeoPoint,
    onDismiss: () -> Unit,
    onEditClick: (GeoPoint) -> Unit,
    onDetailClick: (GeoPoint) -> Unit,
    onShareClick: ((GeoPoint) -> Unit)? = null,
    onOpenGoogleMaps: ((GeoPoint) -> Unit)? = null,
    onShareGoogleMaps: ((GeoPoint) -> Unit)? = null,
    onArchiveClick: ((GeoPoint) -> Unit)? = null,
    onDeleteClick: ((GeoPoint) -> Unit)? = null,
    onFavoriteClick: ((GeoPoint) -> Unit)? = null
) {
    // skipPartiallyExpanded = false → il bottom sheet inizia in peek (parziale)
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }
    var showDatesDialog by remember { mutableStateOf(false) }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Dialog info date creazione/modifica
    if (showDatesDialog) {
        AlertDialog(
            onDismissRequest = { showDatesDialog = false },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.point_info_created, dateFormat.format(point.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.point_info_updated, dateFormat.format(point.updatedAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showDatesDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    // Dialog conferma archiviazione
    if (showArchiveDialog) {
        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            icon = { Icon(Icons.Filled.Archive, contentDescription = null) },
            title = { Text(stringResource(R.string.point_archive_confirm_title)) },
            text = { Text(stringResource(R.string.point_archive_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    onArchiveClick?.invoke(point)
                }) {
                    Text(stringResource(R.string.point_archive))
                }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    // Dialog conferma eliminazione
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            icon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.point_delete_confirm_title)) },
            text = { Text(stringResource(R.string.point_delete_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteClick?.invoke(point)
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.point_delete_confirm_button))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            // ═══════════════════════════════════════════════════════════
            // SEZIONE PEEK — visibile immediatamente
            // ═══════════════════════════════════════════════════════════

            // Header: emoji + titolo + stella preferito
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = point.emoji,
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = point.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (onFavoriteClick != null) {
                    IconButton(onClick = { onFavoriteClick(point) }) {
                        Icon(
                            imageVector = if (point.isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                            contentDescription = null,
                            tint = if (point.isFavorite) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Coordinate + (i) per creato/modificato
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "📍 %.5f, %.5f".format(point.latitude, point.longitude),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = { showDatesDialog = true },
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = stringResource(R.string.point_info_created, ""),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Pulsanti principali: Modifica + Dettaglio
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { onEditClick(point) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.point_edit))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onDetailClick(point) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.OpenInFull, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.point_detail))
                }
            }

            // ═══════════════════════════════════════════════════════════
            // SEZIONE ESPANSA — visibile dopo swipe up
            // ═══════════════════════════════════════════════════════════

            // Descrizione — visibile solo quando il sheet è espanso
            AnimatedVisibility(
                visible = sheetState.currentValue == SheetValue.Expanded && point.description.isNotBlank(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = point.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4
                    )
                }
            }

            // Tag — visibili solo quando il sheet è completamente espanso
            AnimatedVisibility(
                visible = sheetState.currentValue == SheetValue.Expanded && point.tags.isNotEmpty(),
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column {
                    Spacer(modifier = Modifier.height(16.dp))
                    LazyRow(modifier = Modifier.fillMaxWidth()) {
                        items(point.tags) { tag ->
                            AssistChip(
                                onClick = {},
                                label = { Text(tag, style = MaterialTheme.typography.labelSmall) },
                                modifier = Modifier.padding(end = 6.dp)
                            )
                        }
                    }
                }
            }

            // Divider tra azioni primarie e secondarie
            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(modifier = Modifier.height(12.dp))

            // Azioni secondarie: Condividi, Google Maps, Condividi Maps
            if (onShareClick != null) {
                OutlinedButton(
                    onClick = { onShareClick(point) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.point_share))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (onOpenGoogleMaps != null) {
                OutlinedButton(
                    onClick = { onOpenGoogleMaps(point) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Map, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.point_open_google_maps))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (onShareGoogleMaps != null) {
                OutlinedButton(
                    onClick = { onShareGoogleMaps(point) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.point_share_google_maps))
                }
            }

            // Divider prima delle azioni distruttive
            if (onArchiveClick != null || onDeleteClick != null) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Archivia
            if (onArchiveClick != null) {
                OutlinedButton(
                    onClick = { showArchiveDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                ) {
                    Icon(Icons.Filled.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.point_archive))
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Elimina
            if (onDeleteClick != null) {
                OutlinedButton(
                    onClick = { showDeleteDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.point_delete))
                }
            }
        }
    }
}
