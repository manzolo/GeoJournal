package it.manzolo.geojournal.ui.navigation

sealed class Routes(val route: String) {
    object Map : Routes("map")
    object List : Routes("list")
    object Calendar : Routes("calendar")
    object Profile : Routes("profile")
    object Login : Routes("login")

    object AddEditPoint : Routes("add_edit_point/{pointId}") {
        fun createRoute(pointId: String? = null) = "add_edit_point/${pointId ?: "new"}"
    }

    object PointDetail : Routes("point_detail/{pointId}") {
        fun createRoute(pointId: String) = "point_detail/$pointId"
    }
}
