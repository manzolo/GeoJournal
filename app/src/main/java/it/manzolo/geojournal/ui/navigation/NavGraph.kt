package it.manzolo.geojournal.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import androidx.navigation.navOptions
import it.manzolo.geojournal.ui.addedit.AddEditScreen
import it.manzolo.geojournal.ui.auth.AuthScreen
import it.manzolo.geojournal.ui.detail.PointDetailScreen
import it.manzolo.geojournal.ui.calendar.CalendarScreen
import it.manzolo.geojournal.ui.list.ListScreen
import it.manzolo.geojournal.ui.map.MapScreen
import it.manzolo.geojournal.ui.profile.ProfileScreen

@Composable
fun AppNavGraph(
    navController: NavHostController,
    startDestination: String,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier
    ) {
        composable(Routes.Login.route) {
            AuthScreen(
                onNavigateToMain = {
                    navController.navigate(Routes.Map.route) {
                        popUpTo(Routes.Login.route) { inclusive = true }
                    }
                }
            )
        }
        composable(
            route = Routes.Map.route,
            arguments = listOf(
                navArgument("focusLat") { type = NavType.FloatType; defaultValue = 0f },
                navArgument("focusLon") { type = NavType.FloatType; defaultValue = 0f }
            )
        ) {
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
        composable(
            route = Routes.AddEditPoint.route,
            arguments = listOf(navArgument("pointId") { type = NavType.StringType })
        ) {
            AddEditScreen(navController = navController)
        }
        composable(
            route = Routes.PointDetail.route,
            arguments = listOf(navArgument("pointId") { type = NavType.StringType })
        ) {
            PointDetailScreen(navController = navController)
        }
    }
}
