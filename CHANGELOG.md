# Changelog

All notable changes to GeoJournal are documented here.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/)

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
