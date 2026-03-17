# 🗺️ GeoJournal

**Il tuo diario personale dei luoghi.** Salva i posti che ami, arricchiscili con foto, note e tag, e ritrovali sempre sulla mappa.

---

## 🔒 I tuoi dati sono tuoi

**I punti che salvi appartengono solo a te. Noi sviluppatori non accediamo mai ai tuoi dati.**

### Modalità ospite (default)

- Tutti i dati (punti, foto, note, tag) rimangono **esclusivamente sul tuo dispositivo**.
- **Nessuna trasmissione automatica** verso server esterni o terze parti.
- Zero raccolta dati da parte nostra.

### Sincronizzazione cloud (opzionale)

Se scegli di fare login con Google, i tuoi punti vengono sincronizzati su **Firebase Firestore** — un servizio di Google, non un nostro server. I dati sono associati al **tuo account Google**, non a noi come sviluppatori: non abbiamo accesso ai tuoi punti e non li vediamo.

Questa funzione è **completamente opzionale**: l'app funziona al 100% offline senza registrazione.

### Cancellazione

Puoi eliminare il tuo account e tutti i dati (locali + cloud) in qualsiasi momento direttamente dall'app: Profilo → "Elimina account e tutti i dati". Nessuna richiesta da inviare, nessuna attesa.

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
- **Modalità ospite:** nessun dato trasmesso, tutto rimane sul dispositivo
- **Modalità cloud:** login con Google → i dati vanno su Firebase Firestore (Google), associati al tuo account — non ai nostri server
- Noi sviluppatori non raccogliamo né vediamo i tuoi dati
- Puoi eliminare tutto in autonomia dall'app in qualsiasi momento
- Nessuna pubblicità, nessun tracciamento, nessuna vendita di dati

---

## ☕ Supporta il progetto

Se ti piace GeoJournal, offrimi un caffè su [buymeacoffee.com/manzolo](https://www.buymeacoffee.com/manzolo) ☕

---

*Sviluppato con ❤️ da [Manzolo](https://github.com/manzolo)*
