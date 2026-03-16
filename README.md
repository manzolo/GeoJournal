# 🗺️ GeoJournal

**Il tuo diario personale dei luoghi.** Salva i posti che ami, arricchiscili con foto, note e tag, e ritrovali sempre sulla mappa.

---

## 🔒 I tuoi dati sono tuoi

**I punti che salvi appartengono solo a te.**

- Tutti i dati (punti, foto, note, tag) vengono salvati **esclusivamente sul tuo dispositivo**, nel database locale dell'app.
- **Nessun punto viene mai trasmesso automaticamente** a server esterni o terze parti.
- Se vuoi condividere un punto puoi farlo tu esplicitamente dall'app, tramite il bottone "Condividi" — viene creato un file `.geoj` che puoi inviare a chi vuoi.

### Sincronizzazione cloud (opzionale)

Se scegli di accedere con un account Google o email, i tuoi punti vengono sincronizzati su **Firebase Firestore** (Google) per averli disponibili su più dispositivi o dopo una reinstallazione. Questa funzione è **completamente opzionale**: l'app funziona al 100% offline senza registrazione.

---

## ✨ Funzionalità

| | |
|---|---|
| 📍 Mappa interattiva | OpenStreetMap, senza API key né abbonamento |
| 📝 Punti personalizzati | Titolo, descrizione, emoji, tag, rating 1–5 stelle |
| 📷 Foto | Dalla fotocamera o dalla galleria |
| 🔔 Promemoria | Data precisa, anniversario annuale, ricorrenza |
| 📅 Calendario | Vista mensile con promemoria e visite |
| 💾 Backup | Export/import ZIP manuale, backup automatico anche su Google Drive |
| 📤 Condivisione | Condividi singoli punti come file `.geoj` |
| ☁️ Sync cloud | Opzionale, tramite Firebase (solo se loggato) |

---

## 🛠️ Stack tecnico

| Tool | Versione |
|---|---|
| Language | Kotlin 2.2.10 |
| Build | AGP 9.0.1, Gradle 9.3.1 |
| UI | Jetpack Compose + Material3 (BOM 2026.03.00) |
| DI | Hilt 2.59 |
| DB locale | Room 2.7.0 |
| Mappa | OSMDroid 6.1.20 |
| Auth / Cloud | Firebase BOM 33.7.0 |
| Min SDK | 26 (Android 8.0) |

---

## 🔐 Privacy

[Leggi la Privacy Policy completa →](https://manzolo.github.io/GeoJournal/privacy)

In sintesi:
- Modalità ospite: **nessun dato trasmesso**, tutto rimane sul dispositivo
- Login: email e punti sincronizzati su Firebase (Google), eliminabili in qualsiasi momento
- Nessuna pubblicità, nessun tracciamento, nessuna vendita di dati

---

## ☕ Supporta il progetto

Se ti piace GeoJournal, offrimi un caffè su [buymeacoffee.com/manzolo](https://www.buymeacoffee.com/manzolo) ☕

---

*Sviluppato con ❤️ da [Manzolo](https://github.com/manzolo)*
