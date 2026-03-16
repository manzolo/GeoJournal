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
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        handleGeojIntent(intent)
        setContent {
            val isDarkTheme by mainViewModel.isDarkTheme.collectAsState()
            GeoJournalTheme(darkTheme = isDarkTheme) {
                val startDestination by mainViewModel.startDestination.collectAsState()
                val pendingGeojUri by mainViewModel.pendingGeojUri.collectAsState()
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
                    pendingGeojUri?.let { uri ->
                        AlertDialog(
                            onDismissRequest = { mainViewModel.clearPendingGeojUri() },
                            title = { Text("Importa punto") },
                            text = { Text("Vuoi importare questo punto GeoJournal nel tuo diario?") },
                            confirmButton = {
                                Button(onClick = {
                                    mainViewModel.importGeojPoint(uri)
                                    mainViewModel.clearPendingGeojUri()
                                }) { Text("Importa") }
                            },
                            dismissButton = {
                                TextButton(onClick = { mainViewModel.clearPendingGeojUri() }) {
                                    Text("Annulla")
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
        handleGeojIntent(intent)
    }

    private fun handleGeojIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        val filename = resolveFileName(uri)
        if (filename.endsWith(".geoj", ignoreCase = true)) {
            mainViewModel.setPendingGeojUri(uri)
        }
    }

    private fun resolveFileName(uri: Uri): String {
        // Prova prima tramite ContentResolver (content:// URI da WhatsApp, Drive, ecc.)
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val name = cursor.getString(0)
                    if (!name.isNullOrBlank()) return name
                }
            }
        // Fallback: ultimo segmento dell'URI (file:// o path diretto)
        return uri.lastPathSegment ?: ""
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

    NavigationBar {
        bottomNavItems.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = stringResource(item.labelRes)) },
                label = { Text(stringResource(item.labelRes)) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    }
}
