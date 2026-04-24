package it.manzolo.geojournal.ui.map

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
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

data class KmlAnnotationHandle(
    val symbols: List<Symbol> = emptyList(),
    val lines: List<Line> = emptyList(),
    val fills: List<Fill> = emptyList()
)

object KmlOverlayManager {

    // Track line: outer halo (white) + inner vivid orange — stile GPS moderno
    private const val COLOR_TRACK_HALO  = "#FFFFFF"
    private const val COLOR_TRACK_MAIN  = "#FF6633"   // orange GPS track (Strava-like)

    // Colori fill per marker start / end / waypoint (ARGB int)
    private const val COLOR_START  = 0xFF22C55E.toInt()  // green-500
    private const val COLOR_END    = 0xFFEF4444.toInt()  // red-500
    private const val COLOR_POINT  = 0xFF5078FF.toInt()  // accent blue

    // Polygon overlay
    private const val COLOR_POLY_STROKE = "#4466EE"
    private const val COLOR_POLY_FILL   = "#4466EE"

    private const val IMG_KML_START = "kml_marker_start_v2"
    private const val IMG_KML_END   = "kml_marker_end_v2"
    private const val IMG_KML_POINT = "kml_marker_point_v2"

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
        val lines   = mutableListOf<Line>()
        val fills   = mutableListOf<Fill>()

        for (geom in geometries) {
            when (geom.type) {
                KmlGeometryType.POINT -> {
                    val (lon, lat) = geom.coordinates.firstOrNull() ?: continue
                    val sym = symbolManager.create(
                        SymbolOptions()
                            .withLatLng(LatLng(lat, lon))
                            .withIconImage(imageKeyForName(geom.name))
                            .withIconAnchor("bottom")
                            .withIconSize(1f)
                    )
                    symbols += sym
                }

                KmlGeometryType.LINE_STRING -> {
                    if (geom.coordinates.size < 2) continue
                    val latLngs = geom.coordinates.map { (lon, lat) -> LatLng(lat, lon) }

                    // Halo bianco sotto (effetto bordo/ombra)
                    val halo = lineManager.create(
                        LineOptions()
                            .withLatLngs(latLngs)
                            .withLineColor(COLOR_TRACK_HALO)
                            .withLineWidth(9f)
                            .withLineOpacity(0.75f)
                    )
                    lines += halo

                    // Linea principale arancione sopra
                    val track = lineManager.create(
                        LineOptions()
                            .withLatLngs(latLngs)
                            .withLineColor(COLOR_TRACK_MAIN)
                            .withLineWidth(5f)
                            .withLineOpacity(1f)
                    )
                    lines += track
                }

                KmlGeometryType.POLYGON -> {
                    if (geom.coordinates.size < 3) continue
                    val ring = geom.coordinates.map { (lon, lat) -> LatLng(lat, lon) }
                    val fill = fillManager.create(
                        FillOptions()
                            .withLatLngs(listOf(ring))
                            .withFillColor(COLOR_POLY_FILL)
                            .withFillOpacity(0.20f)
                    )
                    fills += fill
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
        if (style.getImage(IMG_KML_START) == null)
            style.addImage(IMG_KML_START, makeStartBitmap(context))
        if (style.getImage(IMG_KML_END) == null)
            style.addImage(IMG_KML_END, makeEndBitmap(context))
        if (style.getImage(IMG_KML_POINT) == null)
            style.addImage(IMG_KML_POINT, makeWaypointBitmap(context))
    }

    // ─── Bitmap factory ────────────────────────────────────────────────────────

    /** Pin verde con triangolo "play" (start del percorso). */
    private fun makeStartBitmap(context: Context): Bitmap =
        makePinBitmap(context, COLOR_START) { cx, cy, r, cv, paint ->
            // Triangolo play bianco
            val tri = Path()
            tri.moveTo(cx - r * 0.22f, cy - r * 0.35f)
            tri.lineTo(cx + r * 0.40f, cy)
            tri.lineTo(cx - r * 0.22f, cy + r * 0.35f)
            tri.close()
            paint.color = AndroidColor.WHITE
            paint.style = Paint.Style.FILL
            cv.drawPath(tri, paint)
        }

    /** Pin rosso con quadrato "stop" (arrivo del percorso). */
    private fun makeEndBitmap(context: Context): Bitmap =
        makePinBitmap(context, COLOR_END) { cx, cy, r, cv, paint ->
            val s = r * 0.38f
            paint.color = AndroidColor.WHITE
            paint.style = Paint.Style.FILL
            cv.drawRect(cx - s, cy - s, cx + s, cy + s, paint)
        }

    /** Cerchio blu piccolo per waypoint generici. */
    private fun makeWaypointBitmap(context: Context): Bitmap {
        val dp   = context.resources.displayMetrics.density
        val size = (32 * dp).toInt()
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv   = Canvas(bmp)
        val cx   = size / 2f
        val cy   = size / 2f
        val r    = size / 2f - 2f * dp
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = AndroidColor.argb(60, 0, 0, 0)
        cv.drawCircle(cx + dp * 0.8f, cy + dp * 1f, r, paint)
        paint.color = AndroidColor.WHITE
        cv.drawCircle(cx, cy, r, paint)
        paint.color = COLOR_POINT
        cv.drawCircle(cx, cy, r - 2.5f * dp, paint)
        paint.color = AndroidColor.WHITE
        cv.drawCircle(cx, cy, 4f * dp, paint)

        return bmp
    }

    /**
     * Disegna un pin a forma di mappa (cerchio + punta triangolare in basso).
     * [drawSymbol] riceve (cx, cy, r circolo, canvas, paint) per disegnare l'icona interna.
     * L'anchor deve essere "bottom" nel SymbolOptions per allineare la punta al coordinate.
     */
    private fun makePinBitmap(
        context: Context,
        fillColor: Int,
        drawSymbol: (cx: Float, cy: Float, r: Float, cv: Canvas, paint: Paint) -> Unit
    ): Bitmap {
        val dp      = context.resources.displayMetrics.density
        val bodyR   = 20f * dp   // raggio del cerchio
        val tailH   = 12f * dp   // altezza della punta
        val padding = 3f  * dp   // margine per ombra
        val w       = ((bodyR * 2 + padding * 2)).toInt()
        val h       = ((bodyR * 2 + tailH + padding * 2)).toInt()
        val bmp     = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv      = Canvas(bmp)
        val cx      = w / 2f
        val cy      = padding + bodyR  // centro del cerchio

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Ombra diffusa (disegnata leggermente in basso/destra)
        paint.color = AndroidColor.argb(70, 0, 0, 0)
        cv.drawCircle(cx + dp, cy + dp, bodyR, paint)
        // Punta ombra
        val shadowTail = tailPath(cx + dp, cy + dp, bodyR, tailH)
        cv.drawPath(shadowTail, paint)

        // Anello bianco esterno
        paint.color = AndroidColor.WHITE
        cv.drawCircle(cx, cy, bodyR, paint)
        val whiteTail = tailPath(cx, cy, bodyR, tailH)
        cv.drawPath(whiteTail, paint)

        // Riempimento colorato
        paint.color = fillColor
        cv.drawCircle(cx, cy, bodyR - 2.5f * dp, paint)
        val coloredTail = tailPath(cx, cy, bodyR - 2.5f * dp, tailH - 2.5f * dp)
        cv.drawPath(coloredTail, paint)

        // Simbolo interno
        drawSymbol(cx, cy, bodyR - 2.5f * dp, cv, paint)

        return bmp
    }

    /** Percorso triangolare della punta del pin, centrato in (cx, cy+r). */
    private fun tailPath(cx: Float, cy: Float, bodyR: Float, tailH: Float): Path {
        val baseHalf = bodyR * 0.38f
        val tipY     = cy + bodyR + tailH
        return Path().apply {
            moveTo(cx - baseHalf, cy + bodyR * 0.55f)
            lineTo(cx + baseHalf, cy + bodyR * 0.55f)
            lineTo(cx, tipY)
            close()
        }
    }

    @Suppress("unused")
    private fun makeTextBitmap(context: Context, fillColor: Int, text: String): Bitmap {
        val dp = context.resources.displayMetrics.density
        val size = (40 * dp).toInt()
        val bmp  = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val cv   = Canvas(bmp)
        val cx   = size / 2f; val cy = size / 2f
        val r    = size / 2f - 2.5f * dp
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = AndroidColor.argb(60, 0, 0, 0)
        cv.drawCircle(cx + dp * 0.8f, cy + dp, r, paint)
        paint.color = AndroidColor.WHITE; cv.drawCircle(cx, cy, r, paint)
        paint.color = fillColor; cv.drawCircle(cx, cy, r - 2.5f * dp, paint)
        paint.color = AndroidColor.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize  = 14f * dp
        paint.typeface  = Typeface.DEFAULT_BOLD
        val fm = paint.fontMetrics
        cv.drawText(text, cx, cy - (fm.ascent + fm.descent) / 2f, paint)
        return bmp
    }
}
