# GeoJournal — CLAUDE.md

App Android per diario geo-personale con GPS, foto, reminders e backup.

---

## Stack tecnico (v0.3.0)

| Tool | Versione |
|---|---|
| AGP | 9.0.0 |
| Gradle | 9.3.1 |
| Kotlin | 2.2.10 |
| KSP | 2.2.10-2.0.2 |
| Compose BOM | 2026.03.00 (UI 1.10.5, M3 1.4.0) |
| Hilt | 2.59 |
| Room | 2.7.0 (schema v3) |
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
│   │   ├── dao/          # Room DAOs (GeoPoint, Reminder, Visit)
│   │   ├── database/     # GeoJournalDatabase (schema v3)
│   │   └── datastore/    # UserPreferencesRepository
│   ├── remote/           # FirebaseAuthRepositoryImpl, FirestoreRepository
│   └── backup/           # BackupManager, GeoPointExporter, AutoBackupWorker
├── domain/
│   └── repository/       # interfacce (AuthRepository, GeoPointRepository, ...)
├── ui/
│   ├── auth/             # AuthScreen + AuthViewModel
│   ├── map/              # MapScreen + MapViewModel (OSMDroid)
│   ├── list/             # ListScreen + ListViewModel
│   ├── detail/           # PointDetailScreen
│   ├── addedit/          # AddEditScreen
│   ├── calendar/         # CalendarScreen
│   ├── profile/          # ProfileScreen + ProfileViewModel + BackupViewModel
│   ├── navigation/       # NavGraph, Routes
│   └── theme/            # GeoJournalTheme
└── di/                   # Hilt modules
```

**Entità principale — GeoPoint:**
`id, title, description, lat, lon, emoji, tags, photoUrls, ownerId, isShared, rating (0=nessun rating), createdAt, updatedAt`

**UserPreferences (DataStore):**
`isDarkTheme, isPro, userId, isGuest, lastSyncTimestamp, autoBackupEnabled, driveBackupUri`

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

---

## Roadmap

| # | Task | Stato |
|---|---|---|
| 8h | Migrazione Google Sign-In → Credential Manager | Da fare |
| 9 | Trasferimento dati guest → cloud al login | Da fare |
| 10 | Offline-first + Sync bidirezionale Firestore | Da fare |
| 7 | Google Play Billing (unlock Pro) | Da fare (ultimo) |

---

## Warning build residui (non bloccanti)

| Warning | Nota |
|---|---|
| `GoogleSignIn`/`GoogleSignInOptions` deprecated | Task 8h |
| `disallowKotlinSourceSets=false` experimental | Atteso, KSP 2.3+ fix |
| KSP `MainApplication_ComponentTreeDeps` incremental | Atteso, KSP 2.3+ fix |
