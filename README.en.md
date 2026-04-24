# GeoJournal

**Your personal journal of places.**
Save the spots you love, enrich them with photos and tags, and find them again on the map — even without an internet connection.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png" height="60" alt="Get it on Google Play">](https://play.google.com/store/apps/details?id=com.manzolo.geojournal)
&nbsp;
[<img src="https://img.shields.io/github/license/manzolo/GeoJournal?style=flat-square" alt="AGPL-3.0 License">](LICENSE)
&nbsp;
[<img src="https://img.shields.io/github/v/tag/manzolo/GeoJournal?label=version&style=flat-square" alt="Version">](https://github.com/manzolo/GeoJournal/releases)

> Open source AGPL-3.0

---

## Features

| | |
|---|---|
| 📍 **Interactive map** | OpenStreetMap — no API key, no subscription |
| 📝 **Custom points** | Title, description, emoji, tags, 1–5 star rating |
| 📷 **Photos** | From camera or gallery |
| 🔔 **Reminders** | One-time date, yearly anniversary, or custom recurrence |
| 📅 **Calendar** | Monthly view with reminders and visit logs |
| 💾 **Backup** | Manual ZIP export/import + automatic backup (also to Google Drive) |
| 📤 **Sharing** | Share a single point as a `.geoj` file |
| ☁️ **Cloud sync** | Optional — via Firebase, only when logged in |
| 🗂️ **Archive** | Archive points without deleting them, with a dedicated filter |

---

## Privacy: your data belongs to you

**Guest mode (default)** — everything stays on your device: no data is transmitted, no sign-up required. The app works 100% offline.

**Cloud mode (optional)** — sign in with Google → your points are synced to Firebase Firestore, linked to your Google account. As developers, we have no access to your data.

**Delete everything** (local + cloud) at any time directly from the app: Profile → *Delete account and all data*.

No ads, no tracking, no data selling.

[Read the full Privacy Policy →](https://manzolo.github.io/GeoJournal/privacy)

---

## Backup Viewer — browse your backups in the browser

You can explore an app-exported backup on an interactive map right in your browser, running entirely locally via Docker.

```bash
# Pull and run instantly (no build required)
docker run -p 8080:8080 \
  -v /path/to/geojournal_backup_YYYYMMDD.zip:/data/backup.zip:ro \
  manzolo/geojournal-viewer:latest
# → http://localhost:8080
```

Or, starting from source in the `tools/backup-viewer/` folder:

```bash
cp ~/Downloads/geojournal_backup_YYYYMMDD.zip tools/backup-viewer/backup.zip
cd tools/backup-viewer
./manage.sh up    # build + start
./manage.sh open  # opens http://localhost:8080
```

The viewer displays all your points on the map with photos, tags, descriptions, and archived points. It runs entirely locally — no data is sent to any external server.

[Docker Hub →](https://hub.docker.com/r/manzolo/geojournal-viewer) · [Full documentation →](tools/backup-viewer/README.md)

---

## Tech stack

| | |
|---|---|
| Language | Kotlin 2.2.10 |
| Build | AGP 9.0.0 · Gradle 9.3.1 |
| UI | Jetpack Compose + Material 3 (BOM 2026.03.00) |
| Dependency Injection | Hilt 2.59 |
| Local database | Room 2.7.0 |
| Map | MapLibre Android 11.12.2 (OpenFreeMap / OpenTopoMap / ESRI) |
| Auth / Cloud | Firebase BOM 33.7.0 |
| Min SDK | 26 (Android 8.0 Oreo) |

---

## Build from source

```bash
git clone https://github.com/manzolo/GeoJournal.git
cd GeoJournal
./gradlew assembleDebug
```

Release builds require keystore files (see GitHub Secrets in the CI workflow).

---

## Contributing

Pull requests and issues are welcome. Read [CLAUDE.md](CLAUDE.md) for the internal architecture and patterns used in this project.

---

## License

[AGPL-3.0](LICENSE) — free software; derivative works must remain open source.

---

*Built with ❤️ by [Manzolo](https://github.com/manzolo) · [☕ Buy me a coffee](https://www.buymeacoffee.com/manzolo)*
