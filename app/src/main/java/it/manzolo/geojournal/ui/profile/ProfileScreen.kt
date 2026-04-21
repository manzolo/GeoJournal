package it.manzolo.geojournal.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PhotoSizeSelectLarge
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.app.Activity
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.result.IntentSenderRequest
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import it.manzolo.geojournal.BuildConfig
import it.manzolo.geojournal.R
import it.manzolo.geojournal.ui.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProfileScreen(
    navController: NavController,
    viewModel: ProfileViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val backupState by backupViewModel.state.collectAsState()
    val autoBackupEnabled by backupViewModel.autoBackupEnabled.collectAsState()
    val driveBackupUri by backupViewModel.driveBackupUri.collectAsState()
    val driveAccountEmail by backupViewModel.driveAccountEmail.collectAsState()
    val lastLocalBackupTimestamp by backupViewModel.lastLocalBackupTimestamp.collectAsState()
    val lastDriveBackupTimestamp by backupViewModel.lastDriveBackupTimestamp.collectAsState()
    val lastDriveBackupSuccess by backupViewModel.lastDriveBackupSuccess.collectAsState()
    val context = LocalContext.current

    var showDeleteAccountConfirm by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }

    val canScheduleExact = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()
        else true
    }
    val dateTag = remember { SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.getDefault()).format(Date()) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { backupViewModel.export(it) } }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { backupViewModel.import(it) } }

    val importGeojLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { backupViewModel.importGeojPoint(it) } }

    val driveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri -> uri?.let { backupViewModel.setDriveBackupUri(it, context) } }

    val driveAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            backupViewModel.onDriveAuthResultOk()
        }
    }

    LaunchedEffect(Unit) {
        backupViewModel.driveAuthEvent.collect { pendingIntent ->
            driveAuthLauncher.launch(
                IntentSenderRequest.Builder(pendingIntent.intentSender).build()
            )
        }
    }

    LaunchedEffect(uiState.navigateToLogin) {
        if (uiState.navigateToLogin) {
            viewModel.onNavigated()
            navController.navigate(Routes.Login.route) { popUpTo(0) { inclusive = true } }
        }
    }
    LaunchedEffect(uiState.deleteAccountError) {
        uiState.deleteAccountError?.let { viewModel.clearDeleteAccountError() }
    }
    LaunchedEffect(backupState) {
        if (backupState is BackupViewModel.State.ExportOk ||
            backupState is BackupViewModel.State.ImportOk ||
            backupState is BackupViewModel.State.ImportPointOk ||
            backupState is BackupViewModel.State.CompressOk ||
            backupState is BackupViewModel.State.DriveUploadOk ||
            backupState is BackupViewModel.State.Error
        ) {
            kotlinx.coroutines.delay(5_000)
            backupViewModel.resetState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(Modifier.height(8.dp))

        if (!canScheduleExact) AlarmPermissionBanner(context)

        AccountCard(
            uiState = uiState,
            onSignOut = viewModel::signOut,
            onDeleteAccountRequest = { showDeleteAccountConfirm = true },
            onSignIn = { navController.navigate(Routes.Login.route) { popUpTo(0) { inclusive = true } } }
        )

        BuyMeCoffeeCard(context)

        PreferencesCard(
            isDarkTheme = uiState.isDarkTheme,
            onDarkThemeChange = viewModel::setDarkTheme
        )

        BackupCard(
            autoBackupEnabled = autoBackupEnabled,
            driveAccountEmail = driveAccountEmail,
            driveBackupUri = driveBackupUri,
            lastLocalBackupTimestamp = lastLocalBackupTimestamp,
            lastDriveBackupTimestamp = lastDriveBackupTimestamp,
            lastDriveBackupSuccess = lastDriveBackupSuccess,
            backupState = backupState,
            dateTag = dateTag,
            onAutoBackupChange = backupViewModel::setAutoBackup,
            onDriveApiConnect = { backupViewModel.connectDriveApi(context) },
            onDriveApiDisconnect = backupViewModel::disconnectDriveApi,
            onDriveApiBackupNow = backupViewModel::backupNowViaDriveApi,
            onDriveConfigure = { driveLauncher.launch("geojournal_backup_cloud.zip") },
            onDriveRemove = backupViewModel::clearDriveBackupUri,
            onExport = { exportLauncher.launch("GeoJournal_backup_$dateTag.zip") },
            onImportRequest = { showImportConfirm = true },
            onImportGeoj = {
                importGeojLauncher.launch(
                    arrayOf("application/x-geojournal-point", "application/zip", "application/octet-stream", "*/*")
                )
            },
            onCompressPhotos = backupViewModel::compressExistingPhotos
        )

        if (uiState.isLoggedIn) {
            SyncPrivacyCard(uiState = uiState, viewModel = viewModel)
        }

        InfoCard(navController = navController, context = context)

        Spacer(Modifier.height(8.dp))
    }

    if (showDeleteAccountConfirm) {
        DeleteAccountDialog(
            isDeletingAccount = uiState.isDeletingAccount,
            deleteAccountError = uiState.deleteAccountError,
            onConfirm = { showDeleteAccountConfirm = false; viewModel.deleteAccount() },
            onDismiss = { showDeleteAccountConfirm = false }
        )
    }
    if (showImportConfirm) {
        ImportConfirmDialog(
            onConfirm = {
                showImportConfirm = false
                importLauncher.launch(arrayOf("application/zip", "*/*"))
            },
            onDismiss = { showImportConfirm = false }
        )
    }
}

// ─── Banners ─────────────────────────────────────────────────────────────────

@Composable
private fun AlarmPermissionBanner(context: Context) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_alarm_warning_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.profile_alarm_warning_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.startActivity(
                            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text(stringResource(R.string.profile_alarm_enable))
            }
        }
    }
}

// ─── Account ─────────────────────────────────────────────────────────────────

@Composable
private fun AccountCard(
    uiState: ProfileUiState,
    onSignOut: () -> Unit,
    onDeleteAccountRequest: () -> Unit,
    onSignIn: () -> Unit
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_section_account),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.Transparent,
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.primaryContainer,
                                        MaterialTheme.colorScheme.tertiaryContainer
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (uiState.isLoggedIn) "👤" else "🕵️",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
                Column {
                    if (uiState.isLoggedIn) {
                        Text(
                            text = uiState.displayName.ifBlank { stringResource(R.string.profile_user_fallback) },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = uiState.email,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            stringResource(R.string.profile_local_mode),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.profile_no_account),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Warning inline quando l'utente è guest
            if (uiState.isGuest) {
                Spacer(Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.profile_guest_warning_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.profile_guest_warning_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))

            if (uiState.isLoggedIn) {
                OutlinedButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isDeletingAccount
                ) {
                    Text(stringResource(R.string.profile_sign_out))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onDeleteAccountRequest,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isDeletingAccount,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    if (uiState.isDeletingAccount) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(Modifier.size(6.dp))
                    }
                    Text(stringResource(R.string.profile_delete_account))
                }
                if (uiState.deleteAccountError != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.profile_error_message, uiState.deleteAccountError),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.profile_sign_in))
                }
            }
        }
    }
}

// ─── Preferenze ──────────────────────────────────────────────────────────────

@Composable
private fun PreferencesCard(isDarkTheme: Boolean, onDarkThemeChange: (Boolean) -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_section_prefs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.profile_dark_theme),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(if (isDarkTheme) R.string.profile_dark_theme_on else R.string.profile_dark_theme_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = isDarkTheme, onCheckedChange = onDarkThemeChange)
            }
        }
    }
}

// ─── Backup ──────────────────────────────────────────────────────────────────

@Composable
private fun BackupCard(
    autoBackupEnabled: Boolean,
    driveAccountEmail: String,
    driveBackupUri: String,
    lastLocalBackupTimestamp: Long,
    lastDriveBackupTimestamp: Long,
    lastDriveBackupSuccess: Boolean,
    backupState: BackupViewModel.State,
    dateTag: String,
    onAutoBackupChange: (Boolean) -> Unit,
    onDriveApiConnect: () -> Unit,
    onDriveApiDisconnect: () -> Unit,
    onDriveApiBackupNow: () -> Unit,
    onDriveConfigure: () -> Unit,
    onDriveRemove: () -> Unit,
    onExport: () -> Unit,
    onImportRequest: () -> Unit,
    onImportGeoj: () -> Unit,
    onCompressPhotos: () -> Unit
) {
    var showBackupInfoDialog by remember { mutableStateOf(false) }
    if (showBackupInfoDialog) {
        val infoBody = if (driveAccountEmail.isNotEmpty())
            stringResource(R.string.profile_backup_info_body_api)
        else
            stringResource(R.string.profile_backup_info_body_saf)
        AlertDialog(
            onDismissRequest = { showBackupInfoDialog = false },
            title = { Text(stringResource(R.string.profile_backup_info_title)) },
            text = { Text(infoBody) },
            confirmButton = {
                TextButton(onClick = { showBackupInfoDialog = false }) {
                    Text(stringResource(R.string.action_close))
                }
            }
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_section_backup),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(12.dp))

            // ── Auto backup + Drive config ────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        stringResource(R.string.profile_auto_backup),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        stringResource(R.string.profile_auto_backup_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(checked = autoBackupEnabled, onCheckedChange = onAutoBackupChange)
            }

            Spacer(Modifier.height(8.dp))

            // ── Drive REST API (primario) ─────────────────────────────────
            val workingOp0 = (backupState as? BackupViewModel.State.Working)?.op
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Cloud,
                        contentDescription = null,
                        modifier = Modifier.size(15.dp),
                        tint = if (driveAccountEmail.isNotEmpty()) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        stringResource(R.string.profile_drive_api_section),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(2.dp))
                if (driveAccountEmail.isNotEmpty()) {
                    Text(
                        stringResource(R.string.profile_drive_api_connected_as, driveAccountEmail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onDriveApiBackupNow,
                            enabled = workingOp0 == null,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (workingOp0 == BackupViewModel.Op.DRIVE_UPLOAD) {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(14.dp))
                            }
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.profile_drive_api_backup_now))
                        }
                        TextButton(onClick = onDriveApiDisconnect) {
                            Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.profile_drive_api_disconnect))
                        }
                    }
                } else {
                    Text(
                        stringResource(R.string.profile_drive_api_connect_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onDriveApiConnect,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(4.dp))
                        Text(stringResource(R.string.profile_drive_api_connect))
                    }
                }
            }

            // ── File locale alternativo (SAF) — collassabile ──────────────
            Spacer(Modifier.height(4.dp))
            var showSafSection by remember(driveBackupUri) { mutableStateOf(driveBackupUri.isNotEmpty()) }
            TextButton(
                onClick = { showSafSection = !showSafSection },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.profile_drive_saf_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showSafSection) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = showSafSection) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.profile_drive_saf_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (driveBackupUri.isNotEmpty()) {
                            Text(
                                stringResource(R.string.profile_drive_configured),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    if (driveBackupUri.isNotEmpty()) {
                        TextButton(onClick = onDriveRemove) {
                            Icon(Icons.Filled.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.profile_drive_remove))
                        }
                    } else {
                        OutlinedButton(onClick = onDriveConfigure) {
                            Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.profile_drive_configure))
                        }
                    }
                }
            }

            // Banner: auto backup attivo ma nessun cloud configurato
            if (autoBackupEnabled && driveAccountEmail.isEmpty() && driveBackupUri.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer
                    )
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            stringResource(R.string.profile_backup_local_only_title),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.profile_backup_local_only_body),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = onDriveApiConnect,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Filled.Cloud, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.size(4.dp))
                            Text(stringResource(R.string.profile_backup_configure_drive))
                        }
                    }
                }
            }

            // ── Stato backup ──────────────────────────────────────────────
            val hasCloudTarget = driveAccountEmail.isNotEmpty() || driveBackupUri.isNotEmpty()
            if (lastLocalBackupTimestamp > 0L || hasCloudTarget) {
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                BackupStatusSection(
                    lastLocalBackupTimestamp = lastLocalBackupTimestamp,
                    lastDriveBackupTimestamp = lastDriveBackupTimestamp,
                    lastDriveBackupSuccess = lastDriveBackupSuccess,
                    hasCloudTarget = hasCloudTarget,
                    onInfoClick = { showBackupInfoDialog = true }
                )
            }

            // ── Azioni manuali ────────────────────────────────────────────
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            val workingOp = (backupState as? BackupViewModel.State.Working)?.op
            val isWorking = workingOp != null
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onExport,
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    if (workingOp == BackupViewModel.Op.EXPORT) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.profile_export))
                }
                Button(
                    onClick = onImportRequest,
                    enabled = !isWorking,
                    modifier = Modifier.weight(1f)
                ) {
                    if (workingOp == BackupViewModel.Op.IMPORT) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.profile_import))
                }
            }
            // ── Strumenti ────────────────────────────────────────────────
            Spacer(Modifier.height(4.dp))
            var showAdvanced by remember { mutableStateOf(false) }
            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.profile_advanced_section),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = if (showAdvanced) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onImportGeoj,
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (workingOp == BackupViewModel.Op.IMPORT_POINT) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.profile_import_geoj))
                    }
                    OutlinedButton(
                        onClick = onCompressPhotos,
                        enabled = !isWorking,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (workingOp == BackupViewModel.Op.COMPRESS) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.PhotoSizeSelectLarge, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.size(6.dp))
                        Text(stringResource(R.string.tools_compress_photos))
                    }
                }
            }

            // Feedback operazione
            when (val s = backupState) {
                is BackupViewModel.State.ExportOk -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_export_ok, s.pointCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is BackupViewModel.State.ImportOk -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_import_ok, s.pointCount, s.reminderCount, s.visitCount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is BackupViewModel.State.ImportPointOk -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_import_point_ok, s.title),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is BackupViewModel.State.CompressOk -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.tools_compress_ok, s.savedKb),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is BackupViewModel.State.DriveUploadOk -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_backup_drive_api_ok),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                is BackupViewModel.State.Error -> {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_operation_error, s.message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun BackupStatusSection(
    lastLocalBackupTimestamp: Long,
    lastDriveBackupTimestamp: Long,
    lastDriveBackupSuccess: Boolean,
    hasCloudTarget: Boolean,
    onInfoClick: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault()) }

    if (lastLocalBackupTimestamp > 0L) {
        val localDateStr = remember(lastLocalBackupTimestamp) { fmt.format(Date(lastLocalBackupTimestamp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.profile_last_backup, localDateStr),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onInfoClick, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = stringResource(R.string.profile_backup_info_title),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    if (hasCloudTarget) {
        Spacer(Modifier.height(2.dp))
        if (lastDriveBackupTimestamp > 0L) {
            val driveDateStr = remember(lastDriveBackupTimestamp) { fmt.format(Date(lastDriveBackupTimestamp)) }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (lastDriveBackupSuccess) Icons.Filled.CheckCircle else Icons.Filled.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = if (lastDriveBackupSuccess) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
                Text(
                    stringResource(
                        if (lastDriveBackupSuccess) R.string.profile_last_drive_backup_ok
                        else R.string.profile_last_drive_backup_error,
                        driveDateStr
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (lastDriveBackupSuccess) MaterialTheme.colorScheme.onSurfaceVariant
                            else MaterialTheme.colorScheme.error
                )
            }
        } else {
            Text(
                stringResource(R.string.profile_drive_never_synced),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ─── Sincronizzazione & Privacy ───────────────────────────────────────────────

@Composable
private fun SyncPrivacyCard(uiState: ProfileUiState, viewModel: ProfileViewModel) {
    val anyActive = uiState.syncGeoPointsEnabled || uiState.syncPhotosEnabled ||
                    uiState.syncRemindersEnabled || uiState.syncVisitLogsEnabled
    // Espansa se almeno un toggle è attivo, collassata di default altrimenti
    var expanded by remember(anyActive) { mutableStateOf(anyActive) }

    // Testo riassuntivo dei toggle attivi per il badge collassato
    val activeLabels = buildList {
        if (uiState.syncGeoPointsEnabled) add(stringResource(R.string.profile_sync_badge_geo))
        if (uiState.syncPhotosEnabled)    add(stringResource(R.string.profile_sync_badge_photos))
        if (uiState.syncRemindersEnabled) add(stringResource(R.string.profile_sync_badge_reminders))
        if (uiState.syncVisitLogsEnabled) add(stringResource(R.string.profile_sync_badge_visits))
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header sempre visibile — click espande/collassa
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.profile_section_sync_privacy),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (!expanded) {
                        Text(
                            if (anyActive) activeLabels.joinToString(" · ")
                            else stringResource(R.string.profile_sync_all_local),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (anyActive) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowDown
                                  else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Contenuto collassabile
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.profile_sync_privacy_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Text(
                            stringResource(R.string.profile_sync_local_default_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(8.dp))

                    SyncToggleRow(
                        title = stringResource(R.string.profile_sync_geo_points_title),
                        desc = stringResource(R.string.profile_sync_geo_points_desc),
                        checked = uiState.syncGeoPointsEnabled,
                        onCheckedChange = viewModel::setSyncGeoPointsEnabled
                    )
                    Spacer(Modifier.height(4.dp))
                    SyncToggleRow(
                        title = stringResource(R.string.profile_sync_photos_title),
                        desc = stringResource(R.string.profile_sync_photos_desc),
                        checked = uiState.syncPhotosEnabled,
                        onCheckedChange = viewModel::setSyncPhotosEnabled
                    )
                    Spacer(Modifier.height(4.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(4.dp))
                    SyncToggleRow(
                        title = stringResource(R.string.profile_sync_reminders_title),
                        desc = stringResource(R.string.profile_sync_reminders_desc),
                        checked = uiState.syncRemindersEnabled,
                        onCheckedChange = viewModel::setSyncRemindersEnabled
                    )
                    Spacer(Modifier.height(4.dp))
                    SyncToggleRow(
                        title = stringResource(R.string.profile_sync_visit_logs_title),
                        desc = stringResource(R.string.profile_sync_visit_logs_desc),
                        checked = uiState.syncVisitLogsEnabled,
                        onCheckedChange = viewModel::setSyncVisitLogsEnabled
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.profile_sync_existing_data_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

// ─── Info app ─────────────────────────────────────────────────────────────────

@Composable
private fun InfoCard(navController: NavController, context: Context) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.profile_section_info),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    stringResource(R.string.profile_version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(
                onClick = { navController.navigate(Routes.Onboarding.createRoute(fromProfile = true)) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.profile_data_info_button),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(
                onClick = { navController.navigate(Routes.Help.route) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.profile_help_button),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            val privacyUrl = stringResource(R.string.privacy_policy_url)
            TextButton(
                onClick = {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(privacyUrl)))
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(R.string.privacy_policy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

// ─── Dialogs ──────────────────────────────────────────────────────────────────

@Composable
private fun DeleteAccountDialog(
    isDeletingAccount: Boolean,
    deleteAccountError: String?,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_delete_account_title)) },
        text = { Text(stringResource(R.string.profile_delete_account_confirm)) },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = !isDeletingAccount,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                if (isDeletingAccount) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(6.dp))
                }
                Text(stringResource(R.string.profile_delete_account_action))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel_label)) }
        }
    )
}

@Composable
private fun ImportConfirmDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_import_backup_title)) },
        text = { Text(stringResource(R.string.profile_import_backup_confirm)) },
        confirmButton = {
            Button(onClick = onConfirm) { Text(stringResource(R.string.profile_import)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel_label)) }
        }
    )
}

// ─── Helper composables ───────────────────────────────────────────────────────

@Composable
private fun SyncToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                desc,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun BuyMeCoffeeCard(context: Context, modifier: Modifier = Modifier) {
    val url = stringResource(R.string.buy_me_coffee_url)
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("☕", style = MaterialTheme.typography.headlineMedium)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.support_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.support_subtitle),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                ) {
                    Text(stringResource(R.string.support_button))
                }
            }
        }
    }
}
