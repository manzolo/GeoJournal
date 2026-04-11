# GeoJournal — CLAUDE.md

App Android per diario geo-personale con GPS, foto, reminders e backup.

---

## Stack tecnico

| Tool | Versione |
|---|---|
| AGP | 9.0.0 |
| Gradle | 9.3.1 |
| Kotlin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2026.03.00 (UI 1.10.5, M3 1.4.0) |
| Hilt | 2.59 |
| Room | 2.7.0 (schema v6) |
| Firebase BOM | 33.7.0 |
| minSdk / targetSdk | 26 / 36 |

Tutti i processori (Hilt + Room) usano **KSP**, niente KAPT.

---

## Comandi utili

```bash
./gradlew assembleDebug          # build debug
./gradlew assembleRelease        # build release (richiede env vars keystore)
./gradlew kspDebugKotlin         # solo generazione codice KSP
```

**"bump"** = aggiorna versionName+versionCode in build.gradle.kts → commit → push → tag → push tag (GitHub Release parte in automatico via CI).

---

## Architettura

```
app/
├── data/
│   ├── local/
│   │   ├── dao/          # Room DAOs (GeoPoint, Reminder, Visit, PointKml)
│   │   ├── database/     # GeoJournalDatabase (schema v6)
│   │   └── datastore/    # UserPreferencesRepository
│   ├── remote/           # FirebaseAuthRepositoryImpl, FirestoreRepository
│   ├── backup/           # BackupManager, GeoPointExporter, AutoBackupWorker
│   ├── kml/              # KmlParser (XmlPullParser), KmlWriter (genera KML da traccia)
│   ├── photo/            # ExifReader (ExifInterface, solo file locali)
│   ├── tracking/         # LocationTrackingService (foreground), TrackingManager (singleton state)
│   └── repository/       # PointKmlRepositoryImpl (filesDir/kmls/{geoPointId}/)
├── domain/
│   ├── model/            # GeoPoint, PointKml, ...
│   └── repository/       # interfacce (AuthRepository, GeoPointRepository, PointKmlRepository, ...)
├── ui/
│   ├── auth/             # AuthScreen + AuthViewModel
│   ├── map/              # MapScreen + MapViewModel (OSMDroid) + KmlOverlayManager
│   ├── list/             # ListScreen + ListViewModel
│   ├── detail/           # PointDetailScreen
│   ├── addedit/          # AddEditScreen (sezione "Dettagli aggiuntivi" collassabile)
│   ├── calendar/         # CalendarScreen
│   ├── profile/          # ProfileScreen + ProfileViewModel + BackupViewModel
│   ├── navigation/       # NavGraph, Routes
│   └── theme/            # GeoJournalTheme
└── di/                   # Hilt modules
```

**Entità principale — GeoPoint:**
`id, title, description, lat, lon, emoji, tags, photoUrls, ownerId, isShared, rating (0=nessun rating), notes, createdAt, updatedAt`

**PointKml (DB v6 — tabella `point_kmls`):**
`id, geoPointId, name, filePath (filesDir/kmls/{geoPointId}/{uuid}.kml), importedAt`

**UserPreferences (DataStore):**
`isDarkTheme, isPro, userId, isGuest, lastSyncTimestamp, autoBackupEnabled, driveBackupUri, driveAccountEmail`

---

## Pattern importanti

- **isGuest logic:** `isGuest = user == null && prefs.isGuest` — mai mostrare banner "dati non al sicuro" se Firebase ha un utente loggato
- **Navigazione mappa:** usare `SharedFlow` per eventi di focus, mai `savedStateHandle`
- **hiltViewModel import:** `androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel` (non il vecchio `hilt.navigation.compose`)
- **Kotlin 2.x DSL:** usare `kotlin { compilerOptions { } }`, non `kotlinOptions { }`

---

## Workaround build (AGP 9 + KSP)

In `gradle.properties`:
```properties
android.disallowKotlinSourceSets=false   # obbligatorio per KSP 2.x + AGP 9
```
Warning "experimental" atteso — sparirà con KSP 2.3+.

In `app/build.gradle.kts`:
```kotlin
hilt { enableAggregatingTask = false }
ksp { arg("hilt.enableAggregatingTask", "true") }
```

---

## Modalità utente

- **Guest:** dati solo Room locale, `isGuest=true` in DataStore, banner in ProfileScreen
- **Cloud:** Google/email login → Firebase Auth → `userId` + `isGuest=false` in DataStore
- **Logout:** pulisce solo DataStore (userId, isGuest), i dati Room rimangono intatti

---

## File MIME personalizzati

- `.geoj` = `application/x-geojournal-point` (formato ZIP con punto.json + foto)
- Intent filter registra anche `application/octet-stream` per compatibilità WhatsApp
- `MainActivity.resolveFileName()` verifica l'estensione `.geoj` via `ContentResolver` prima di processare
- `.geoj` **schemaVersion = 3**: esclusi `rating` e `notes` (campi personali, non condivisibili)

---

## Privacy dei dati

| Campo | .geoj (condivisione) | backup.zip | Firestore (`syncGeoPoints`) |
|---|---|---|---|
| title, description, lat/lon, emoji, tags | ✅ | ✅ | opt-in |
| photoUrls | ✅ (foto copiate) | ✅ | opt-in (`syncPhotos`) |
| rating | ❌ escluso (v3) | ✅ | opt-in |
| **notes** | ❌ escluso | ✅ | ❌ **mai** (locale-only by design) |
| KML files | ❌ escluso | ✅ (file fisici) | ❌ **mai** (locale-only) |
| reminders | ❌ | ✅ | opt-in (`syncReminders`) |

I 4 flag in ProfileScreen (`syncGeoPoints`, `syncPhotos`, `syncReminders`, `syncVisitLogs`) coprono tutti i casi.
`notes` e KML non hanno flag separati perché non hanno percorso verso Firestore.

---

## Roadmap

| # | Task | Stato |
|---|---|---|
| 8h | Migrazione Google Sign-In → Credential Manager | ✅ Done |
| 9 | Trasferimento dati guest → cloud al login | Da fare |
| 10 | Offline-first + Sync bidirezionale Firestore | Da fare |

---

## Feature principali

- **Sezione "Dettagli aggiuntivi" (AddEditScreen):** collassata di default, auto-espansa se il punto ha già tag/reminders/rating/notes/kml. Badge riassuntivi quando collassata.
- **Sezione "Sincronizzazione & Privacy" (ProfileScreen):** collassata di default, auto-espansa se almeno un toggle è attivo. Badge riassuntivo (nomi dei toggle attivi) quando collassata.
- **Backup su Google Drive (Drive REST API):** `DriveApiClient` usa `GoogleAuthUtil.getToken()` + scope `drive.file` + `HttpURLConnection` multipart upload. Upload immediato senza dipendere dall'app Drive. SAF mantenuto come fallback. `driveAccountEmail` in DataStore identifica l'account connesso. `Identity.getAuthorizationClient().authorize()` gestisce il consent OAuth.
- **KML overlay mappa:** SmallFAB "KML" + ModalBottomSheet con switch per-file. Parser custom `XmlPullParser` (no osmdroid-bonuspack). Storage in `filesDir/kmls/{geoPointId}/`. Marker custom Canvas (verde ▶ Partenza, rosso ■ Arrivo). `KmlMarker` subclass esclusa dal `removeAll` clustering.
- **Tracciamento GPS (tracking):** `LocationTrackingService` foreground service (tipo `location`), avviato da `PointDetailScreen`. Ogni 5s o 5m registra coordinate in `TrackingManager` (singleton Hilt). Allo stop genera KML via `KmlWriter` e lo salva via `PointKmlRepository.saveKml()`. Permessi: `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_LOCATION`.
- **Note personali (`notes`):** campo libero in AddEditScreen, visibile in PointDetailScreen (solo se non vuoto), incluso in backup.zip, **mai** esportato in .geoj o Firestore.
- **EXIF foto:** `ExifReader` legge `dateTaken` + `cameraModel` da file locali (off-thread via IO dispatcher). Null silenzioso per URL HTTPS. Mostrato come strip semitrasparente nel PhotoViewerDialog.

---

## Warning build residui (non bloccanti)

| Warning | Nota |
|---|---|
| ~~`GoogleSignIn`/`GoogleSignInOptions` deprecated~~ | ✅ Migrato a Credential Manager |
| `disallowKotlinSourceSets=false` experimental | Atteso, KSP 2.3+ fix |
| KSP `MainApplication_ComponentTreeDeps` incremental | Atteso, KSP 2.3+ fix |
