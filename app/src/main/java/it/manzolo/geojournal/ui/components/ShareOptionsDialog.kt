package it.manzolo.geojournal.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import it.manzolo.geojournal.R
import it.manzolo.geojournal.data.backup.ShareOptions

@Composable
fun ShareOptionsDialog(
    onConfirm: (message: String?, options: ShareOptions) -> Unit,
    onDismiss: () -> Unit
) {
    var shareMessage by remember { mutableStateOf("") }
    var includePhotos by remember { mutableStateOf(true) }
    var includeTags by remember { mutableStateOf(false) }
    var includeKml by remember { mutableStateOf(false) }
    var includeNotes by remember { mutableStateOf(false) }
    var includeReminders by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_message_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = shareMessage,
                    onValueChange = { if (it.length <= 200) shareMessage = it },
                    placeholder = { Text(stringResource(R.string.share_message_hint)) },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.share_options_include_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ShareCheckRow(
                    label = stringResource(R.string.share_options_photos),
                    checked = includePhotos,
                    onCheckedChange = { includePhotos = it }
                )
                ShareCheckRow(
                    label = stringResource(R.string.share_options_tags),
                    checked = includeTags,
                    onCheckedChange = { includeTags = it }
                )
                ShareCheckRow(
                    label = stringResource(R.string.share_options_kml),
                    checked = includeKml,
                    onCheckedChange = { includeKml = it }
                )
                ShareCheckRow(
                    label = stringResource(R.string.share_options_notes),
                    checked = includeNotes,
                    onCheckedChange = { includeNotes = it }
                )
                ShareCheckRow(
                    label = stringResource(R.string.share_options_reminders),
                    checked = includeReminders,
                    onCheckedChange = { includeReminders = it }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(
                    shareMessage.ifBlank { null },
                    ShareOptions(
                        includePhotos = includePhotos,
                        includeTags = includeTags,
                        includeKml = includeKml,
                        includeNotes = includeNotes,
                        includeReminders = includeReminders
                    )
                )
            }) {
                Text(stringResource(R.string.point_share))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}

@Composable
private fun ShareCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
