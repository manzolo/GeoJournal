# GeoJournal

**Il tuo diario personale dei luoghi.**
Salva i posti che ami, arricchiscili con foto, note vocali e tag, ritrovali sulla mappa — anche senza connessione.

[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/it_badge_web_generic.png" height="60" alt="Disponibile su Google Play">](https://play.google.com/store/apps/details?id=com.manzolo.geojournal)
&nbsp;
[<img src="https://img.shields.io/github/license/manzolo/GeoJournal?style=flat-square" alt="Licenza AGPL-3.0">](LICENSE)
&nbsp;
[<img src="https://img.shields.io/github/v/tag/manzolo/GeoJournal?label=versione&style=flat-square" alt="Versione">](https://github.com/manzolo/GeoJournal/releases)

> Open source AGPL-3.0

---

## Funzionalità principali

| | |
|---|---|
| 📍 **Mappa interattiva** | OpenStreetMap — nessuna API key, nessun abbonamento |
| 📝 **Punti personalizzati** | Titolo, descrizione, emoji, tag, rating 1–5 stelle |
| 📷 **Foto** | Dalla fotocamera o dalla galleria |
| 🎙️ **Note vocali** | Registra e riascolta direttamente dal punto |
| 🔔 **Promemoria** | Data singola, anniversario annuale o ricorrenza personalizzata |
| 📅 **Calendario** | Vista mensile con promemoria e log visite |
| 💾 **Backup** | Export/import ZIP manuale + backup automatico (anche su Google Drive) |
| 📤 **Condivisione** | Condividi un singolo punto come file `.geoj` |
| ☁️ **Sync cloud** | Opzionale — tramite Firebase, solo se loggato |
| 🗂️ **Archivio** | Archivia i punti senza cancellarli, con filtro dedicato |

---

## Privacy: i tuoi dati sono tuoi

**Modalità ospite (default)** — tutto rimane sul dispositivo: nessun dato trasmesso, nessuna registrazione richiesta. L'app funziona al 100% offline.

**Modalità cloud (opzionale)** — login con Google → i punti vengono sincronizzati su Firebase Firestore, associati al tuo account Google. Come sviluppatori non abbiamo accesso ai tuoi dati.

**Puoi eliminare tutto** (locale + cloud) direttamente dall'app: Profilo → *Elimina account e tutti i dati*.

Nessuna pubblicità, nessun tracciamento, nessuna vendita di dati.

[Leggi la Privacy Policy completa →](https://manzolo.github.io/GeoJournal/privacy)

---

## Backup Viewer — visualizza i tuoi backup via browser

Puoi esplorare un backup esportato dall'app su una mappa interattiva nel browser, in locale, tramite Docker.

```bash
# Pull e avvio istantaneo (nessun build richiesto)
docker run -p 8080:8080 \
  -v /percorso/geojournal_backup_YYYYMMDD.zip:/data/backup.zip:ro \
  manzolo/geojournal-viewer:latest
# → http://localhost:8080
```

Oppure, partendo dal sorgente nella cartella `tools/backup-viewer/`:

```bash
cp ~/Downloads/geojournal_backup_YYYYMMDD.zip tools/backup-viewer/backup.zip
cd tools/backup-viewer
./manage.sh up    # build + avvio
./manage.sh open  # apre http://localhost:8080
```

Il viewer mostra tutti i punti sulla mappa con foto, tag, descrizioni e punti archiviati. Gira interamente in locale — nessun dato viene inviato a server esterni.

[Docker Hub →](https://hub.docker.com/r/manzolo/geojournal-viewer) · [Documentazione completa →](tools/backup-viewer/README.md)

---

## Stack tecnico

| | |
|---|---|
| Linguaggio | Kotlin 2.2.10 |
| Build | AGP 9.0.0 · Gradle 9.3.1 |
| UI | Jetpack Compose + Material 3 (BOM 2026.03.00) |
| Dependency Injection | Hilt 2.59 |
| Database locale | Room 2.7.0 |
| Mappa | OSMDroid 6.1.20 |
| Auth / Cloud | Firebase BOM 33.7.0 |
| Min SDK | 26 (Android 8.0 Oreo) |

---

## Build dal sorgente

```bash
git clone https://github.com/manzolo/GeoJournal.git
cd GeoJournal
./gradlew assembleDebug
```

Per la build release sono necessari i file keystore (vedi GitHub Secrets nel workflow CI).

---

## Contribuire

Pull request e issue sono benvenuti. Leggi il file [CLAUDE.md](CLAUDE.md) per l'architettura interna e i pattern adottati.

---

## Licenza

[AGPL-3.0](LICENSE) — software libero, derivati devono rimanere open source.

---

*Sviluppato con ❤️ da [Manzolo](https://github.com/manzolo) · [☕ Offrimi un caffè](https://www.buymeacoffee.com/manzolo)*
