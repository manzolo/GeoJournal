package it.manzolo.geojournal.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Typeface
import it.manzolo.geojournal.data.kml.KmlGeometry
import it.manzolo.geojournal.data.kml.KmlGeometryType
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Fill
import org.maplibre.android.plugins.annotation.FillManager
import org.maplibre.android.plugins.annotation.FillOptions
import org.maplibre.android.plugins.annotation.Line
import org.maplibre.android.plugins.annotation.LineManager
import org.maplibre.android.plugins.annotation.LineOptions
import org.maplibre.android.plugins.annotation.Symbol
import org.maplibre.android.plugins.annotation.SymbolManager
import org.maplibre.android.plugins.annotation.SymbolOptions

/**
 * Riferimenti agli annotation creati per un singolo file KML.
 * Conservare gli oggetti (non gli id) semplifica [KmlOverlayManager.removeFromMap].
 */
data class KmlAnnotationHandle(
    val symbols: List<Symbol> = emptyList(),
    val lines: List<Line> = emptyList(),
    val fills: List<Fill> = emptyList()
)

/**
 * Rendering di geometrie KML (Point / LineString / Polygon) su MapLibre tramite
 * i plugin annotation manager.
 *
 * I bitmap dei marker KML ("Partenza" ▶ verde, "Arrivo" ■ rosso, altri blu) vengono
 * registrati una sola volta nello [Style] con chiavi stabili; registrazioni successive
 * sono no-op (il check `style.getImage` evita duplicazioni).
 */
object KmlOverlayManager {

    private const val COLOR_START       = 0xFF22C55E.toInt() // green-500
    private const val COLOR_END         = 0xFFEF4444.toInt() // red-500
    private const val COLOR_POINT       = 0xFF5078FF.toInt() // accent blue
    private const val COLOR_LINE_HEX    = "#FF4466EE"        // accent blue line
    private const val COLOR_POLY_STROKE = "#FF4466EE"
    private const val COLOR_POLY_FILL   = "#5078FF"          // 24% opacity applicato via withFillOpacity

    private const val IMG_KML_START = "kml_marker_start"
    private const val IMG_KML_END   = "kml_marker_end"
    private const val IMG_KML_POINT = "kml_marker_point"

    fun buildOverlays(
        context: Context,
        style: Style,
        symbolManager: SymbolManager,
        lineManager: LineManager,
        fillManager: FillManager,
        geometries: List<KmlGeometry>
    ): KmlAnnotationHandle {
        ensureMarkerImages(context, style)

        val symbols = mutableListOf<Symbol>()
        val lines = mutableListOf<Line>()
        val fills = mutableListOf<Fill>()

        for (geom in geometries) {
            when (geom.type) {
                KmlGeometryType.POINT -> {
                    val (lon, lat) = geom.coordinates.firstOrNull() ?: continue
                    val imageKey = imageKeyForName(geom.name)
                    val sym = symbolManager.create(
                        SymbolOptions()
                            .withLatLng(LatLng(lat, lon))
                            .withIconImage(imageKey)
                            .withIconAnchor("center")
                            .withIconSize(1f)
                    )
                    symbols += sym
                }

                KmlGeometryType.LINE_STRING -> {
                    if (geom.coordinates.size < 2) continue
                    val latLngs = geom.coordinates.map { (lon, lat) -> LatLng(lat, lon) }
                    val line = lineManager.create(
                        LineOptions()
                            .withLatLngs(latLngs)
                            .withLineColor(COLOR_LINE_HEX)
                            .withLineWidth(4f)
                            .withLineOpacity(0.95f)
                    )
                    lines += line
                }

                KmlGeometryType.POLYGON -> {
                    if (geom.coordinates.size < 3) continue
                    val ring = geom.coordinates.map { (lon, lat) -> LatLng(lat, lon) }
                    val fill = fillManager.create(
                        FillOptions()
                            .withLatLngs(listOf(ring))
                            .withFillColor(COLOR_POLY_FILL)
                            .withFillOpacity(0.24f)
                    )
                    fills += fill
                    // Bordo chiuso
                    val closed = ring + ring.first()
                    val border = lineManager.create(
                        LineOptions()
                            .withLatLngs(closed)
                            .withLineColor(COLOR_POLY_STROKE)
                            .withLineWidth(2.5f)
                            .withLineOpacity(1f)
                    )
                    lines += border
                }
            }
        }

        return KmlAnnotationHandle(symbols, lines, fills)
    }

    fun removeFromMap(
        symbolManager: SymbolManager,
        lineManager: LineManager,
        fillManager: FillManager,
        handle: KmlAnnotationHandle
    ) {
        handle.symbols.forEach { runCatching { symbolManager.delete(it) } }
        handle.lines.forEach   { runCatching { lineManager.delete(it) } }
        handle.fills.forEach   { runCatching { fillManager.delete(it) } }
    }

    private fun imageKeyForName(name: String): String = when (name.trim().lowercase()) {
        "partenza" -> IMG_KML_START
        "arrivo"   -> IMG_KML_END
        else       -> IMG_KML_POINT
    }

    private fun ensureMarkerImages(context: Context, style: Style) {
        if (style.getImage(IMG_KML_START) == null) {
            style.addImage(IMG_KML_START, makeCircleMarker(context, COLOR_START, "▶"))
        }
        if (style.getImage(IMG_KML_END) == null) {
            style.addImage(IMG_KML_END, makeCircleMarker(context, COLOR_END, "■"))
        }
        if (style.getImage(IMG_KML_POINT) == null) {
            style.addImage(IMG_KML_POINT, makeCircleMarker(context, COLOR_POINT, null))
        }
    }

    private fun makeCircleMarker(
        context: Context,
        fillColor: Int,
        symbol: String?
    ): Bitmap {
        val dp = context.resources.displayMetrics.density
        val size = (36 * dp).toInt()
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        val cx = size / 2f
        val cy = size / 2f
        val r  = size / 2f - 2.5f * dp
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Drop shadow
        paint.color = AndroidColor.argb(60, 0, 0, 0)
        cv.drawCircle(cx + dp * 0.8f, cy + dp * 1f, r, paint)

        // White ring
        paint.color = AndroidColor.WHITE
        cv.drawCircle(cx, cy, r, paint)

        // Coloured fill
        paint.color = fillColor
        cv.drawCircle(cx, cy, r - 2.5f * dp, paint)

        if (symbol != null) {
            paint.color = AndroidColor.WHITE
            paint.textAlign = Paint.Align.CENTER
            paint.textSize = 13f * dp
            paint.typeface = Typeface.DEFAULT_BOLD
            val fm = paint.fontMetrics
            val textY = cy - (fm.ascent + fm.descent) / 2f
            cv.drawText(symbol, cx, textY, paint)
        } else {
            paint.color = AndroidColor.WHITE
            cv.drawCircle(cx, cy, 4f * dp, paint)
        }

        return bmp
    }
}
