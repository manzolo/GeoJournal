package it.manzolo.geojournal.ui.navigation

sealed class Routes(val route: String) {
    data object Map : Routes("map?focusLat={focusLat}&focusLon={focusLon}") {
        /** URL senza parametri — usato dalla bottom nav */
        val navRoute = "map"
        /** URL con focus su un punto specifico */
        fun focusRoute(lat: Double, lon: Double) = "map?focusLat=$lat&focusLon=$lon"
    }
    data object List : Routes("list")
    data object Calendar : Routes("calendar")
    data object Profile : Routes("profile")
    data object Login : Routes("login")

    data object AddEditPoint : Routes("add_edit_point/{pointId}") {
        fun createRoute(pointId: String? = null) = "add_edit_point/${pointId ?: "new"}"
    }

    data object PointDetail : Routes("point_detail/{pointId}") {
        fun createRoute(pointId: String) = "point_detail/$pointId"
    }
}
