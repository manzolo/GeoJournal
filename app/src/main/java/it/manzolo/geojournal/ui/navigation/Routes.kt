package it.manzolo.geojournal.ui.navigation

sealed class Routes(val route: String) {
    data object Map : Routes("map")
    data object List : Routes("list")
    data object Album : Routes("album")
    data object Calendar : Routes("calendar")
    data object Profile : Routes("profile")
    data object Login : Routes("login")
    data object Onboarding : Routes("onboarding?fromProfile={fromProfile}") {
        fun createRoute(fromProfile: Boolean = false) = "onboarding?fromProfile=$fromProfile"
    }

    data object AddEditPoint : Routes("add_edit_point/{pointId}?title={title}&lat={lat}&lon={lon}&cloneFromId={cloneFromId}") {
        fun createRoute(
            pointId: String? = null,
            title: String? = null,
            lat: String? = null,
            lon: String? = null,
            cloneFromId: String? = null
        ): String {
            val id = pointId ?: "new"
            val queryParams = mutableListOf<String>()
            if (title != null) queryParams.add("title=$title")
            if (lat != null) queryParams.add("lat=$lat")
            if (lon != null) queryParams.add("lon=$lon")
            if (cloneFromId != null) queryParams.add("cloneFromId=$cloneFromId")
            val queryStr = if (queryParams.isNotEmpty()) "?" + queryParams.joinToString("&") else ""
            return "add_edit_point/$id$queryStr"
        }
    }

    data object PointDetail : Routes("point_detail/{pointId}") {
        fun createRoute(pointId: String) = "point_detail/$pointId"
    }

    data object Help : Routes("help")
}
