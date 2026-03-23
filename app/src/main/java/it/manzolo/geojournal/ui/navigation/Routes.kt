package it.manzolo.geojournal.ui.navigation

sealed class Routes(val route: String) {
    data object Map : Routes("map")
    data object List : Routes("list")
    data object Calendar : Routes("calendar")
    data object Profile : Routes("profile")
    data object Login : Routes("login")
    data object Onboarding : Routes("onboarding?fromProfile={fromProfile}") {
        fun createRoute(fromProfile: Boolean = false) = "onboarding?fromProfile=$fromProfile"
    }

    data object AddEditPoint : Routes("add_edit_point/{pointId}?title={title}&lat={lat}&lon={lon}") {
        fun createRoute(pointId: String? = null) = "add_edit_point/${pointId ?: "new"}"
    }

    data object PointDetail : Routes("point_detail/{pointId}") {
        fun createRoute(pointId: String) = "point_detail/$pointId"
    }
}
