package it.manzolo.geojournal.ui.map

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.BitmapDrawable
import it.manzolo.geojournal.data.kml.KmlGeometry
import it.manzolo.geojournal.data.kml.KmlGeometryType
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline

/** Marker subclass used for KML overlays — excluded from the cluster-rebuild removeAll. */
class KmlMarker(mapView: MapView) : Marker(mapView)

object KmlOverlayManager {

    private const val COLOR_START   = 0xFF22C55E.toInt()  // green-500
    private const val COLOR_END     = 0xFFEF4444.toInt()  // red-500
    private const val COLOR_POINT   = 0xFF5078FF.toInt()  // accent blue
    private const val COLOR_LINE    = 0xFF4466EE.toInt()  // accent blue (line)
    private const val COLOR_POLY_FILL   = 0x3C5078FF      // accent blue 24% alpha
    private const val COLOR_POLY_STROKE = 0xFF4466EE.toInt()

    /** Draw a circular marker bitmap. [symbol] is a single UTF-8 char drawn centered. */
    private fun makeCircleMarker(
        mapView: MapView,
        fillColor: Int,
        symbol: String? = null
    ): BitmapDrawable {
        val dp  = mapView.context.resources.displayMetrics.density
        val size = (36 * dp).toInt()
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv   = Canvas(bmp)
        val cx   = size / 2f
        val cy   = size / 2f
        val r    = size / 2f - 2.5f * dp

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Drop shadow
        paint.color = Color.argb(60, 0, 0, 0)
        cv.drawCircle(cx + dp * 0.8f, cy + dp * 1f, r, paint)

        // White ring
        paint.color = Color.WHITE
        cv.drawCircle(cx, cy, r, paint)

        // Coloured fill
        paint.color = fillColor
        cv.drawCircle(cx, cy, r - 2.5f * dp, paint)

        if (symbol != null) {
            // Centred text symbol
            paint.color = Color.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 13f * dp
            paint.typeface = Typeface.DEFAULT_BOLD
            // Vertical centre adjustment: (descent - ascent) / 2 - descent
            val fm = paint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2f
            cv.drawText(symbol, cx, textY, paint)
        } else {
            // Small white dot
            paint.color = Color.WHITE
            cv.drawCircle(cx, cy, 4f * dp, paint)
        }

        return BitmapDrawable(mapView.resources, bmp)
    }

    private fun markerForName(mapView: MapView, name: String): BitmapDrawable {
        return when (name.trim().lowercase()) {
            "partenza" -> makeCircleMarker(mapView, COLOR_START, "▶")
            "arrivo"   -> makeCircleMarker(mapView, COLOR_END,   "■")
            else       -> makeCircleMarker(mapView, COLOR_POINT)
        }
    }

    fun buildOverlays(mapView: MapView, geometries: List<KmlGeometry>): List<Overlay> {
        val dp = mapView.context.resources.displayMetrics.density
        return geometries.mapNotNull { geom ->
            when (geom.type) {
                KmlGeometryType.POINT -> {
                    val (lon, lat) = geom.coordinates.first()
                    KmlMarker(mapView).apply {
                        position  = GeoPoint(lat, lon)
                        title     = geom.name.ifBlank { null }
                        icon      = markerForName(mapView, geom.name)
                        // Circle is symmetric — anchor at centre
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                    }
                }

                KmlGeometryType.LINE_STRING -> {
                    if (geom.coordinates.size < 2) return@mapNotNull null
                    Polyline(mapView).apply {
                        setPoints(geom.coordinates.map { (lon, lat) -> GeoPoint(lat, lon) })
                        title = geom.name.ifBlank { null }
                        outlinePaint.apply {
                            color       = COLOR_LINE
                            strokeWidth = 4f * dp
                            strokeCap   = android.graphics.Paint.Cap.ROUND
                            strokeJoin  = android.graphics.Paint.Join.ROUND
                        }
                    }
                }

                KmlGeometryType.POLYGON -> {
                    if (geom.coordinates.size < 3) return@mapNotNull null
                    Polygon(mapView).apply {
                        points = geom.coordinates.map { (lon, lat) -> GeoPoint(lat, lon) }
                        title  = geom.name.ifBlank { null }
                        fillPaint.color    = COLOR_POLY_FILL
                        outlinePaint.apply {
                            color       = COLOR_POLY_STROKE
                            strokeWidth = 2.5f * dp
                        }
                    }
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
