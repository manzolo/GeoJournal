import base64
import io
import json
import os
import zipfile
from datetime import datetime, timezone
from xml.sax.saxutils import escape as xml_escape

from flask import Flask, abort, jsonify, redirect, render_template, request, send_file

app = Flask(__name__)

BACKUP_PATH = os.environ.get("BACKUP_PATH", "/data/backup.zip")
_UPLOAD_PATH = "/tmp/uploaded_backup.zip"

_cache: dict | None = None
_override_path: str | None = None


def _current_path() -> str:
    return _override_path or BACKUP_PATH


def _load() -> dict:
    global _cache
    if _cache is None:
        with zipfile.ZipFile(_current_path(), "r") as z:
            with z.open("backup.json") as f:
                _cache = json.load(f)
    return _cache


def _fmt_ts(ts: int, fmt: str = "%d/%m/%Y") -> str:
    return datetime.fromtimestamp(ts / 1000).strftime(fmt)


def _read_exif_date(img_bytes: bytes) -> str | None:
    """Extract EXIF DateTimeOriginal from image bytes. Returns formatted string or None."""
    try:
        from PIL import Image
        from PIL.ExifTags import TAGS
        img = Image.open(io.BytesIO(img_bytes))
        exif_data = img._getexif()
        if not exif_data:
            return None
        for tag_id, value in exif_data.items():
            tag_name = TAGS.get(tag_id, "")
            if tag_name in ("DateTimeOriginal", "DateTime"):
                dt = datetime.strptime(str(value), "%Y:%m:%d %H:%M:%S")
                return dt.strftime("%d %b %Y, %H:%M")
        return None
    except Exception:
        return None


def _list_kmls_in_zip() -> set[str]:
    """Return the set of KML zip paths present in the backup."""
    with zipfile.ZipFile(_current_path(), "r") as z:
        return {n for n in z.namelist() if n.startswith("kmls/") and n.endswith(".kml")}


def _prepare_points(raw: list, reminders: list, visits: list) -> list:
    # Indice per geoPointId
    rem_by_point: dict = {}
    for r in reminders:
        rem_by_point.setdefault(r["geoPointId"], []).append(r)

    vis_by_point: dict = {}
    for v in visits:
        vis_by_point.setdefault(v["geoPointId"], []).append(v)

    kml_zip_paths = _list_kmls_in_zip()

    points = []
    for p in raw:
        web_photos = []
        for url in p.get("photoUrls", []):
            if url.startswith("backup://photos/"):
                web_photos.append(f"/photos/{url.removeprefix('backup://photos/')}")
            elif url.startswith("https://"):
                web_photos.append(url)

        pid = p["id"]

        p_reminders = [
            {
                "title":     r["title"],
                "startDate": _fmt_ts(r["startDate"], "%d/%m/%Y %H:%M"),
                "endDate":   _fmt_ts(r["endDate"], "%d/%m/%Y %H:%M") if r.get("endDate") else None,
                "type":      r["type"],
                "isActive":  r["isActive"],
            }
            for r in rem_by_point.get(pid, [])
        ]

        p_visits = [
            {
                "visitedAt": _fmt_ts(v["visitedAt"], "%d/%m/%Y %H:%M"),
                "note":      v.get("note", ""),
            }
            for v in sorted(vis_by_point.get(pid, []), key=lambda x: x["visitedAt"], reverse=True)
        ]

        # KML files: use metadata from JSON when available, fallback to scanning ZIP
        web_kmls = []
        json_kmls = p.get("kmls", [])
        if json_kmls:
            for k in json_kmls:
                backup_path = k.get("backupPath", "")
                # backupPath = "backup://kmls/{pointId}/{filename}"
                zip_entry = "kmls/" + backup_path.removeprefix("backup://kmls/")
                if zip_entry in kml_zip_paths:
                    web_kmls.append({
                        "name": k.get("name", zip_entry.rsplit("/", 1)[-1]),
                        "url":  f"/kmls/{zip_entry.removeprefix('kmls/')}",
                    })
        else:
            # Older backup without kmls metadata: scan ZIP directly
            for zip_entry in kml_zip_paths:
                parts = zip_entry.split("/")  # ["kmls", pointId, filename]
                if len(parts) == 3 and parts[1] == pid:
                    web_kmls.append({
                        "name": parts[2],
                        "url":  f"/kmls/{pid}/{parts[2]}",
                    })

        points.append({
            **p,
            "createdAt_fmt": _fmt_ts(p["createdAt"]),
            "webPhotos":     web_photos,
            "tagList":       p.get("tags", []),
            "reminders":     p_reminders,
            "visits":        p_visits,
            "notes":         p.get("notes", ""),
            "webKmls":       web_kmls,
            "isFavorite":    p.get("isFavorite", False),
        })
    return points


@app.route("/")
def index():
    data = _load()
    points = _prepare_points(
        data.get("geoPoints", []),
        data.get("reminders", []),
        data.get("visitLogs", []),
    )
    meta = {
        "count":       len(points),
        "exported_at": _fmt_ts(data.get("exportedAt", 0), "%d/%m/%Y %H:%M"),
        "app_version": data.get("appVersion", "—"),
    }
    return render_template("index.html", points=points, meta=meta)


@app.route("/api/points")
def api_points():
    data = _load()
    return jsonify(_prepare_points(
        data.get("geoPoints", []),
        data.get("reminders", []),
        data.get("visitLogs", []),
    ))


@app.route("/photos/<path:photo_path>")
def serve_photo(photo_path):
    zip_path = f"photos/{photo_path}"
    with zipfile.ZipFile(_current_path(), "r") as z:
        if zip_path not in z.namelist():
            abort(404)
        data = z.read(zip_path)

    ext = photo_path.rsplit(".", 1)[-1].lower()
    mime = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}.get(ext, "image/jpeg")
    return send_file(io.BytesIO(data), mimetype=mime)


@app.route("/kmls/<path:kml_path>")
def serve_kml(kml_path):
    zip_entry = f"kmls/{kml_path}"
    with zipfile.ZipFile(_current_path(), "r") as z:
        if zip_entry not in z.namelist():
            abort(404)
        data = z.read(zip_entry)
    return send_file(
        io.BytesIO(data),
        mimetype="application/vnd.google-earth.kml+xml",
        as_attachment=False,
        download_name=kml_path.rsplit("/", 1)[-1],
    )


@app.route("/api/exif/<path:photo_path>")
def photo_exif(photo_path):
    """Returns EXIF date for a local backup photo. Returns {exifDate: null} for HTTPS or missing files."""
    zip_path = f"photos/{photo_path}"
    try:
        with zipfile.ZipFile(_current_path(), "r") as z:
            if zip_path not in z.namelist():
                return jsonify({"exifDate": None})
            img_bytes = z.read(zip_path)
        exif_date = _read_exif_date(img_bytes)
        return jsonify({"exifDate": exif_date})
    except Exception:
        return jsonify({"exifDate": None})


_SEMICIRCLES_TO_DEG = 180.0 / (2 ** 31)

# Self-contained SVG icons for KML start/end markers (no external URLs)
def _svg_b64(svg: str) -> str:
    return "data:image/svg+xml;base64," + base64.b64encode(svg.encode()).decode()

_KML_ICON_START = _svg_b64(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">'
    '<circle cx="16" cy="16" r="14" fill="#22c55e" stroke="white" stroke-width="2.5"/>'
    '<polygon points="12,9 12,23 24,16" fill="white"/>'
    '</svg>'
)
_KML_ICON_END = _svg_b64(
    '<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 32 32">'
    '<circle cx="16" cy="16" r="14" fill="#ef4444" stroke="white" stroke-width="2.5"/>'
    '<rect x="10" y="10" width="12" height="12" fill="white"/>'
    '</svg>'
)


def _fit_to_kml(fit_bytes: bytes, activity_name: str) -> str:
    """Parse a Garmin .fit file and return a KML string."""
    from fitparse import FitFile

    ff = FitFile(io.BytesIO(fit_bytes))

    track_points: list[tuple[float, float, float]] = []   # (lon, lat, alt)
    activity_ts: datetime | None = None

    for record in ff.get_messages("record"):
        fields = {f.name: f.value for f in record}
        lat_sc = fields.get("position_lat")
        lon_sc = fields.get("position_long")
        if lat_sc is None or lon_sc is None:
            continue
        lat = lat_sc * _SEMICIRCLES_TO_DEG
        lon = lon_sc * _SEMICIRCLES_TO_DEG
        alt = float(fields.get("altitude") or fields.get("enhanced_altitude") or 0)
        track_points.append((lon, lat, alt))
        if activity_ts is None:
            ts = fields.get("timestamp")
            if ts and hasattr(ts, "replace"):
                activity_ts = ts

    if not track_points:
        raise ValueError("Nessun punto GPS trovato nel file .fit")

    date_str = activity_ts.strftime("%d %b %Y, %H:%M") if activity_ts else ""
    doc_name = xml_escape(f"{activity_name} {date_str}".strip())

    coord_lines = "\n          ".join(f"{lon:.7f},{lat:.7f},{alt:.1f}" for lon, lat, alt in track_points)

    start_lon, start_lat, start_alt = track_points[0]
    end_lon,   end_lat,   end_alt   = track_points[-1]

    kml = f"""<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
  <Document>
    <name>{doc_name}</name>
    <Style id="trackStyle">
      <LineStyle><color>ff0055ff</color><width>3</width></LineStyle>
      <PolyStyle><color>330055ff</color></PolyStyle>
    </Style>
    <Style id="startStyle">
      <IconStyle><color>ff00cc00</color><scale>1.2</scale>
        <Icon><href>{_KML_ICON_START}</href></Icon>
      </IconStyle>
    </Style>
    <Style id="endStyle">
      <IconStyle><color>ff0000cc</color><scale>1.2</scale>
        <Icon><href>{_KML_ICON_END}</href></Icon>
      </IconStyle>
    </Style>
    <Placemark>
      <name>Traccia</name>
      <description>{doc_name} — {len(track_points)} punti</description>
      <styleUrl>#trackStyle</styleUrl>
      <LineString>
        <tessellate>1</tessellate>
        <coordinates>
          {coord_lines}
        </coordinates>
      </LineString>
    </Placemark>
    <Placemark>
      <name>Partenza</name>
      <styleUrl>#startStyle</styleUrl>
      <Point><coordinates>{start_lon:.7f},{start_lat:.7f},{start_alt:.1f}</coordinates></Point>
    </Placemark>
    <Placemark>
      <name>Arrivo</name>
      <styleUrl>#endStyle</styleUrl>
      <Point><coordinates>{end_lon:.7f},{end_lat:.7f},{end_alt:.1f}</coordinates></Point>
    </Placemark>
  </Document>
</kml>"""
    return kml


@app.route("/fit/convert", methods=["POST"])
def fit_convert():
    """Accept a .fit file, return a .kml download."""
    if "fit_file" not in request.files:
        abort(400, "Campo 'fit_file' mancante")
    f = request.files["fit_file"]
    if not f.filename or not f.filename.lower().endswith(".fit"):
        abort(400, "Il file deve avere estensione .fit")

    fit_bytes = f.read()
    name_base = f.filename[:-4]  # strip .fit

    try:
        kml_str = _fit_to_kml(fit_bytes, name_base)
    except Exception as exc:
        abort(422, str(exc))

    kml_bytes = io.BytesIO(kml_str.encode("utf-8"))
    download_name = f"{name_base}.kml"
    return send_file(
        kml_bytes,
        mimetype="application/vnd.google-earth.kml+xml",
        as_attachment=True,
        download_name=download_name,
    )


@app.route("/upload", methods=["POST"])
def upload_backup():
    global _cache, _override_path
    if "backup_file" not in request.files:
        abort(400, "Campo 'backup_file' mancante")
    f = request.files["backup_file"]
    if not f.filename or not f.filename.lower().endswith(".zip"):
        abort(400, "Il file deve avere estensione .zip")
    data = f.read()
    # Validate it's a valid backup zip before accepting
    try:
        with zipfile.ZipFile(io.BytesIO(data), "r") as z:
            if "backup.json" not in z.namelist():
                abort(422, "Il file non contiene backup.json — non è un backup GeoJournal valido")
    except zipfile.BadZipFile:
        abort(422, "File ZIP non valido")
    with open(_UPLOAD_PATH, "wb") as out:
        out.write(data)
    _override_path = _UPLOAD_PATH
    _cache = None
    return redirect("/")


if __name__ == "__main__":
    print(f"Loading backup from: {BACKUP_PATH}")
    _load()  # eagerly load at startup to catch errors early
    app.run(host="0.0.0.0", port=8080, debug=False)
