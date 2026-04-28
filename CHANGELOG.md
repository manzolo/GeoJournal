# Changelog

All notable changes to GeoJournal are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

---

## [1.4.4] - 2026-04-28
### Added
- Smart backup skip: if no data has changed since the last backup (fingerprint v1 covers all 4 tables — GeoPoints, Reminders, VisitLogs, KMLs), the worker skips export and cloud upload and records only a "checked" timestamp.
- New UI row in BackupStatusSection: "Checked on <date> — no changes" shown when a skip occurred, distinct from Drive error state.

### Changed
- `BackupManager`: extracted `loadAll()` helper (eliminates 3× duplicated data-loading) and `computeFingerprint()` for change detection.
- `UserPreferencesRepository`: added `setBackupSuccess()` atomic setter (writes `lastLocalBackup`, `lastBackupFingerprint`, `lastBackupChecked` in one DataStore `edit {}`).
- `AutoBackupWorker`: `setForeground()` deferred to real-backup path only — no progress notification on skip.

---

## [1.4.3] - 2026-04-26
### Added
- Emoji picker: categorized tabbed layout (7 categories — Places, Nature, Transport, Food, Sports, Animals, Symbols) replacing the flat scrollable list.

### Changed
- `PointBottomSheet`: secondary actions (share, maps, archive, delete) now reveal progressively on scroll via `NestedScrollConnection`-driven `showMore` state.
- `PointDetailScreen`: actions (share location, share .geoj, archive, delete) moved to a `DropdownMenu` in the TopAppBar; primary navigation actions (map, Google Maps) promoted to a full-width button row at the top of content.
- `MapViewModel` / `PointDetailViewModel`: `toggleFavorite` now applies optimistic UI update before the Room write, eliminating the ~100–500 ms lag on star tap.
- Removed unused `onShareGeoj`, `onArchiveToggle`, `onDelete` parameters from `PointDetailContent` (superseded by TopAppBar menu).

---

## [1.4.2] - 2026-04-26
### Added
- GPX and Garmin FIT track import: the "Import track" button now accepts `.gpx` and `.fit` files in addition to `.kml`. GPX tracks are parsed via `XmlPullParser`; FIT files via the Garmin FIT SDK 21.200.0. Both formats are converted to KML before storage so all existing overlay rendering and backup logic applies unchanged.

### Changed
- `PointKmlRepository`: `importKml` and `importTrackContent` now share a private `upsertKml()` helper; removed duplicate dedup/insert logic and eliminated a no-op `dao.update()` call on re-import.
- `AddEditViewModel.importKml`: file I/O moved off the Main thread (was a potential ANR on large tracks); error handling simplified to a single `try/catch`.

---

## [1.4.1] - 2026-04-24
### Fixed
- KML track line color bug: `#FF4466EE` was parsed by MapLibre as CSS `RRGGBBAA` (rendering as red/dark instead of blue); replaced with unambiguous CSS hex colors.
- KML track line now renders as a double-layer path (white halo 9dp + orange `#FF6633` 5dp) for high contrast on both road and satellite basemaps (Strava-like style).
- KML start/end markers redesigned as map-pin shapes (circle + downward triangular tail) with canvas-drawn play-triangle (▶ start, green) and stop-square (■ end, red) icons; anchor corrected to `"bottom"` so the pin tip aligns to the coordinate.
- GPS pick-location dialog: initial zoom reduced from 19.0 to 15.0 (neighbourhood scale) so the user can see the surrounding context on the map.

## [1.4.0] - 2026-04-24
### Changed
- **Map engine migrated from OSMDroid to MapLibre Android 11.12.2.** OSMDroid was archived in November 2024 and used deprecated Android APIs (`setStatusBarColor`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES`) flagged in Play Console on Android 15+. MapLibre is actively maintained with vector tile support.
- Road layer now uses OpenFreeMap Liberty (vector tiles, free, no API key); TOPO and SATELLITE layers unchanged (OpenTopoMap + ESRI World Imagery raster).
- User location: replaced custom `MyLocationOverlay` (radial gradient + rotation sensor) with built-in `LocationComponent` (accuracy circle + bearing puck).
- KML overlay rendering: now uses MapLibre annotation plugin (`SymbolManager` / `LineManager` / `FillManager`) with dedicated managers isolated from main marker manager.
- Cluster click logic refactored via sealed `SymbolTarget { Single | Cluster }` — cleaner dispatch between single-marker bottom sheet and cluster zoom/picker.

### Removed
- OSMDroid 6.1.20 dependency.
- `WRITE_EXTERNAL_STORAGE` permission (was used for OSMDroid tile cache; MapLibre uses private app cache).
- Unused `com.google.android.geo.API_KEY` manifest meta-data placeholder.

---

## [1.3.3] - 2026-04-22
### Changed
- GPS dialog: improved layout with better alignment, button arrangement, and "Choose location" label

---

## [1.3.2] - 2026-04-22
### Added
- Complete localization of error and success messages across all screens (IT/EN) — MainViewModel import messages, map controls, profile operations, auth errors, backup notifications

---

## [1.3.1] - 2026-04-22
### Changed
- Album mode: migrated from local state to navigation-based routing for improved navigation consistency
- Album mode: added full localization support (IT/EN) for all UI strings

---

## [1.3.0] - 2026-04-21
### Added
- List: album mode toggle in ListScreen — switch between list and full-screen photo pager via TopAppBar icon
- Backup viewer: album mode for photo gallery browsing with pager navigation

---

## [1.2.4] - 2026-04-21
### Fixed
- Map: auto-disable favorites-only filter when selecting a non-favorite point via deeplink

---

## [1.2.3] - 2026-04-20
### Changed
- Auto-backup worker: now runs as a foreground worker with a progress notification and 10-minute operation timeout
- Auto-backup receiver: uses `REPLACE` policy and `setExpedited` for higher-priority scheduling; network constraint moved out of receiver
- Cloud upload: explicit SAF fallback if Drive REST API fails and a SAF URI is configured
- Auto-backup: conditional re-arm after work completes — re-schedules next alarm only if `autoBackupEnabled` is true
- Auto-backup: cloud upload failure triggers a `BigTextStyle` error notification with exception detail

---

## [1.2.2] - 2026-04-19
### Changed
- Map favorite bubble markers: replaced inline ⭐ text with a circular gold badge overlapping the top-right corner of the bubble (badge-style, white ★ inside)

---

## [1.2.1] - 2026-04-19
### Fixed
- WorkManager crash on Android 14+: added `FOREGROUND_SERVICE_DATA_SYNC` permission and `foregroundServiceType=dataSync` on `SystemForegroundService`; `ForegroundInfo` now passes `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29+

### Added
- backup-viewer: `isFavorite` star badge on card, bubble and detail; Preferiti filter

---

## [1.2.0] - 2026-04-18
### Added
- `isFavorite` field (Room schema v7, MIGRATION_6_7): star toggle in map FAB, BottomSheet, DetailScreen, ListScreen
- Smart default: map opens in favorites-only mode when favorites exist
- Favorites filter chip in ListScreen; search always runs across all active points
- Included in backup.zip; excluded from .geoj and Firestore (locale-only by design)

---

## [1.1.4] - 2026-04-11
### Changed
- Replaced `PeriodicWorkRequest` with `AlarmManager.setExactAndAllowWhileIdle` + `OneTimeWorkRequest` chain for nightly backup (fixes App Standby bucket skip)
- Dropped `requiresCharging` constraint (too restrictive for a nightly job)
- `RescheduleWorker` re-arms the alarm on device boot

### Fixed
- Retry transient IO/SSL errors (connection abort, SSL reset) with exponential backoff (0 → 20 s → 60 s) in `DriveApiClient`

---

## [1.1.3] - 2026-04-09
_(no functional changes — version bump only; retry logic landed in 1.1.4)_

---

## [1.1.2] - 2026-04-07
### Fixed
- Prevent indefinite hang in `AutoBackupWorker`: `withTimeout(10 min)` on `doWork()` + `withTimeout(30 s)` + `runInterruptible` on `getAccessToken()`

---

## [1.1.1] - 2026-04-05
### Changed
- Simplified IT+EN strings for general audience: KML → "Tracciati GPS", removed technical jargon; fixed inaccuracies in privacy descriptions

---

## [1.1.0] - 2026-04-03
### Changed
- BackupCard Drive section: vertical layout (email on its own line with ellipsis, full-width buttons)

---

## [1.0.9] - 2026-04-01
### Added
- Google Drive REST API v3 backup (`drive.file` scope, `HttpURLConnection` multipart, 401 retry with `clearToken`); SAF kept as fallback
- `SyncPrivacyCard`: collapsible by default with active-toggle badge summary
- Context-aware backup info dialog (Drive API vs SAF)

### Changed
- Updated privacy policy: OAuth2 `drive.file` data flow section (IT+EN)

---

## [1.0.8] - 2026-03-30
### Fixed
- Nightly backup: `KEEP` enqueue policy (no reset on each app open); relaxed constraints to `CONNECTED + charging`

---

## [1.0.7] - 2026-03-27
### Added
- Map: compact peek BottomSheet (title + coords + Edit/Detail only; description/tags in expanded)
- Archive/Delete point directly from BottomSheet (confirm dialog + snackbar)
- AddEdit: "Dettagli aggiuntivi" always collapsed by default; reordered Note→Reminders→KML→Tags→Rating
- Photo AddEdit: tap-select + swap replaces arrow ←

### Fixed
- Search bar: dismiss keyboard before opening sheet (prevents full-expand)

---

## [1.0.6] - 2026-03-25
### Fixed
- Map search bar: replaced `Close` icon with `ArrowBack`, adjusted padding

---

## [1.0.5] - 2026-03-23
### Added
- Backup progress and error notifications; Wi-Fi + charging constraints for auto-backup Drive

---

## [1.0.4] - 2026-03-21
### Changed
- Add-point button on map: replaced `FloatingActionButton` with `Surface+IconButton` (consistent with map controls style)

---

## [1.0.3] - 2026-03-19
### Fixed
- Confirm dialog on swipe archive/unarchive in list (prevents accidental archiving)
- Drive backup reliability: `openFileDescriptor("wt")` + `zip.flush()` + `fd.sync()` + `NOTIFY_SYNC_TO_NETWORK`

---

## [1.0.2] - 2026-03-17
### Added
- KML panel: track length (LineString distance) per file
- Map controls grouped into pill Surface (Map: Layer+KML; Actions: Track+Parking+Location)

### Fixed
- `BackHandler` with `enabled=`; distance shows "—" without GPS fix

---

## [1.0.1] - 2026-03-15
### Added
- Google Maps-style search bar on map (marker filter + result list + distance)
- KML panel: distance in group headers

### Changed
- Reorganized right-side FABs with uniform spacing

---

## [1.0.0] - 2026-03-13
### Added
- First stable release for Play Store production
