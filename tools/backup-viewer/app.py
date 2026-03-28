import io
import json
import os
import zipfile
from datetime import datetime

from flask import Flask, abort, jsonify, render_template, send_file

app = Flask(__name__)

BACKUP_PATH = os.environ.get("BACKUP_PATH", "/data/backup.zip")

_cache: dict | None = None


def _load() -> dict:
    global _cache
    if _cache is None:
        with zipfile.ZipFile(BACKUP_PATH, "r") as z:
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


def _prepare_points(raw: list, reminders: list, visits: list) -> list:
    # Indice per geoPointId
    rem_by_point: dict = {}
    for r in reminders:
        rem_by_point.setdefault(r["geoPointId"], []).append(r)

    vis_by_point: dict = {}
    for v in visits:
        vis_by_point.setdefault(v["geoPointId"], []).append(v)

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
                "startDate": _fmt_ts(r["startDate"]),
                "endDate":   _fmt_ts(r["endDate"]) if r.get("endDate") else None,
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

        points.append({
            **p,
            "createdAt_fmt": _fmt_ts(p["createdAt"]),
            "webPhotos":     web_photos,
            "tagList":       p.get("tags", []),
            "reminders":     p_reminders,
            "visits":        p_visits,
            "notes":         p.get("notes", ""),
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
    with zipfile.ZipFile(BACKUP_PATH, "r") as z:
        if zip_path not in z.namelist():
            abort(404)
        data = z.read(zip_path)

    ext = photo_path.rsplit(".", 1)[-1].lower()
    mime = {"jpg": "image/jpeg", "jpeg": "image/jpeg", "png": "image/png", "webp": "image/webp"}.get(ext, "image/jpeg")
    return send_file(io.BytesIO(data), mimetype=mime)


@app.route("/api/exif/<path:photo_path>")
def photo_exif(photo_path):
    """Returns EXIF date for a local backup photo. Returns {exifDate: null} for HTTPS or missing files."""
    zip_path = f"photos/{photo_path}"
    try:
        with zipfile.ZipFile(BACKUP_PATH, "r") as z:
            if zip_path not in z.namelist():
                return jsonify({"exifDate": None})
            img_bytes = z.read(zip_path)
        exif_date = _read_exif_date(img_bytes)
        return jsonify({"exifDate": exif_date})
    except Exception:
        return jsonify({"exifDate": None})


if __name__ == "__main__":
    print(f"Loading backup from: {BACKUP_PATH}")
    _load()  # eagerly load at startup to catch errors early
    app.run(host="0.0.0.0", port=8080, debug=False)
