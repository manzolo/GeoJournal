package it.manzolo.geojournal.ui.map

import it.manzolo.geojournal.data.kml.KmlGeometry
import it.manzolo.geojournal.data.kml.KmlGeometryType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

object KmlOverlayManager {

    fun buildOverlays(mapView: MapView, geometries: List<KmlGeometry>): List<Overlay> =
        geometries.mapNotNull { geom ->
            when (geom.type) {
                KmlGeometryType.POINT -> {
                    val (lon, lat) = geom.coordinates.first()
                    Marker(mapView).apply {
                        position = GeoPoint(lat, lon)
                        title = geom.name.ifBlank { null }
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    }
                }
                KmlGeometryType.LINE_STRING -> {
                    if (geom.coordinates.size < 2) return@mapNotNull null
                    Polyline(mapView).apply {
                        setPoints(geom.coordinates.map { (lon, lat) -> GeoPoint(lat, lon) })
                        title = geom.name.ifBlank { null }
                    }
                }
                KmlGeometryType.POLYGON -> {
                    if (geom.coordinates.size < 3) return@mapNotNull null
                    Polygon(mapView).apply {
                        points = geom.coordinates.map { (lon, lat) -> GeoPoint(lat, lon) }
                        title = geom.name.ifBlank { null }
                    }
                }
            }
        }

    fun addToMap(mapView: MapView, overlays: List<Overlay>) {
        mapView.overlays.addAll(overlays)
        mapView.invalidate()
    }

    fun removeFromMap(mapView: MapView, overlays: List<Overlay>) {
        mapView.overlays.removeAll(overlays.toSet())
        mapView.invalidate()
    }
}
