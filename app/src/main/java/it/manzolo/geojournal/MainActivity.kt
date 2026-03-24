package it.manzolo.geojournal

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dagger.hilt.android.AndroidEntryPoint
import it.manzolo.geojournal.data.notification.ReminderBroadcastReceiver
import it.manzolo.geojournal.ui.MainViewModel
import it.manzolo.geojournal.ui.navigation.AppNavGraph
import it.manzolo.geojournal.ui.navigation.Routes
import it.manzolo.geojournal.ui.theme.GeoJournalTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleExternalIntent(intent)
        setContent {
            val isDarkTheme by mainViewModel.isDarkTheme.collectAsState()
            GeoJournalTheme(darkTheme = isDarkTheme) {
                val startDestination by mainViewModel.startDestination.collectAsState()
                val pendingGeojImport by mainViewModel.pendingGeojImport.collectAsState()
                val context = LocalContext.current

                // Toast dal risultato import .geoj
                LaunchedEffect(Unit) {
                    mainViewModel.geojImportMessage.collect { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                    }
                }

                startDestination?.let { dest ->
                    val navController = rememberNavController()
                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route
                    val showBottomNav = currentRoute != Routes.Login.route

                    LaunchedEffect(Unit) {
                        mainViewModel.navigateToMap.collect {
                            navController.navigate(Routes.Map.route) {
                                popUpTo(Routes.Map.route) { inclusive = false }
                                launchSingleTop = true
                            }
                        }
                    }

                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            if (showBottomNav) {
                                GeoJournalBottomNav(navController = navController)
                            }
                        }
                    ) { innerPadding ->
                        AppNavGraph(
                            navController = navController,
                            startDestination = dest,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    // Dialog conferma import .geoj
                    pendingGeojImport?.let { importResult ->
                        AlertDialog(
                            onDismissRequest = { mainViewModel.clearPendingGeojImport() },
                            title = { Text(stringResource(R.string.maps_import_geoj_title)) },
                            text = {
                                Column {
                                    if (!importResult.senderMessage.isNullOrBlank()) {
                                        ElevatedCard(
                                            colors = CardDefaults.elevatedCardColors(
                                                containerColor = MaterialTheme.colorScheme.secondaryContainer
                                            ),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(12.dp)) {
                                                Text(
                                                    stringResource(R.string.import_sender_message_label),
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(
                                                    importResult.senderMessage,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    fontStyle = FontStyle.Italic,
                                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                                )
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(12.dp))
                                    }
                                    Text(stringResource(R.string.maps_import_geoj_text))
                                }
                            },
                            confirmButton = {
                                Button(onClick = { mainViewModel.confirmGeojImport() }) {
                                    Text(stringResource(R.string.maps_import_confirm))
                                }
                            },
                            dismissButton = {
                                TextButton(onClick = { mainViewModel.clearPendingGeojImport() }) {
                                    Text(stringResource(R.string.action_cancel))
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleExternalIntent(intent)
    }

    private fun handleExternalIntent(intent: Intent?) {
        if (intent?.action == ReminderBroadcastReceiver.ACTION_OPEN_POINT) {
            val geoPointId = intent.getStringExtra(ReminderBroadcastReceiver.EXTRA_GEO_POINT_ID)
            if (geoPointId != null) {
                mainViewModel.onNotificationOpenPoint(geoPointId)
            }
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        handleGeojUri(uri, intent)
    }

    private fun handleGeojUri(uri: Uri, intent: Intent) {
        val resolvedMime = try { contentResolver.getType(uri) } catch (_: Exception) { null }
        if (intent.type == "application/x-geojournal-point" ||
            resolvedMime == "application/x-geojournal-point"
        ) { mainViewModel.setPendingGeojUri(uri); return }
        if (uri.scheme == "file") {
            if (uri.path?.endsWith(".geoj", ignoreCase = true) == true)
                mainViewModel.setPendingGeojUri(uri)
            return
        }
        if (isGeojUri(uri)) mainViewModel.setPendingGeojUri(uri)
    }

    private fun isGeojUri(uri: Uri): Boolean {
        try {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = cursor.getString(0)
                        if (!name.isNullOrBlank()) return name.endsWith(".geoj", ignoreCase = true)
                    }
                }
        } catch (_: Exception) { }
        return uri.toString().contains(".geoj", ignoreCase = true)
    }
}

private data class BottomNavItem(
    val route: String,
    val labelRes: Int,
    val icon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(Routes.Map.route, R.string.nav_map, Icons.Filled.Map),
    BottomNavItem(Routes.List.route, R.string.nav_list, Icons.AutoMirrored.Filled.List),
    BottomNavItem(Routes.Calendar.route, R.string.nav_calendar, Icons.Filled.CalendarMonth),
    BottomNavItem(Routes.Profile.route, R.string.nav_profile, Icons.Filled.Person),
)

@Composable
private fun GeoJournalBottomNav(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        bottomNavItems.forEach { item ->
            val selected = currentRoute == item.route
            NavigationBarItem(
                icon = {
                    Icon(
                        item.icon,
                        contentDescription = stringResource(item.labelRes),
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                label = {
                    Text(
                        stringResource(item.labelRes),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                selected = selected,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(Routes.Map.route) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            )
        }
    }
}
