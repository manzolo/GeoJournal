package it.manzolo.geojournal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import it.manzolo.geojournal.ui.calendar.CalendarScreen
import it.manzolo.geojournal.ui.list.ListScreen
import it.manzolo.geojournal.ui.map.MapScreen
import it.manzolo.geojournal.ui.profile.ProfileScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.Map.route,
        modifier = modifier
    ) {
        composable(Routes.Map.route) {
            MapScreen(navController = navController)
        }
        composable(Routes.List.route) {
            ListScreen(navController = navController)
        }
        composable(Routes.Calendar.route) {
            CalendarScreen(navController = navController)
        }
        composable(Routes.Profile.route) {
            ProfileScreen(navController = navController)
        }
    }
}
