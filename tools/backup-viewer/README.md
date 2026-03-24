# GeoJournal Backup Viewer

Visualizzatore web per i backup dell'app [GeoJournal](https://github.com/manzolo/GeoJournal).
Mostra i tuoi punti su mappa interattiva con foto, tag e dettagli.

---

## Requisiti / Requirements

- Docker + Docker Compose
- Il file `backup.zip` esportato dall'app GeoJournal

---

## Avvio rapido / Quick start

**Con immagine pubblica (nessun build richiesto):**
```bash
cp ~/Downloads/geojournal_backup_YYYYMMDD.zip ./backup.zip
docker run -p 8080:8080 -v ./backup.zip:/data/backup.zip:ro manzolo/geojournal-viewer:latest
```

**Dal sorgente:**
```bash
# 1. Copia il backup nella cartella
cp ~/Downloads/geojournal_backup_YYYYMMDD.zip ./backup.zip

# 2. Avvia
./manage.sh up

# 3. Apri il browser
./manage.sh open   # → http://localhost:8080
```

Docker Hub: [hub.docker.com/r/manzolo/geojournal-viewer](https://hub.docker.com/r/manzolo/geojournal-viewer)

---

## Comandi disponibili / Available commands

| Comando | Descrizione (IT) | Description (EN) |
|---|---|---|
| `./manage.sh up` | Build + avvio in background | Build and start in background |
| `./manage.sh down` | Ferma e rimuove il container | Stop and remove container |
| `./manage.sh restart` | Riavvia (dopo aver aggiornato `backup.zip`) | Restart (after updating `backup.zip`) |
| `./manage.sh logs` | Segui i log in tempo reale | Follow live logs |
| `./manage.sh status` | Stato del container | Container status |
| `./manage.sh open` | Apri il browser | Open browser |
| `./manage.sh build` | Solo build immagine | Build image only |
| `./manage.sh shell` | Shell bash nel container | Bash shell in container |
| `./manage.sh dev` | Avvio locale senza Docker | Local run without Docker |
| `./manage.sh clean` | Rimuovi immagine e cache | Remove image and cache |

Oppure usa il `Makefile`: `make help`

---

## Funzionalità / Features

- **Mappa interattiva** (OpenStreetMap / Dark / Satellite) con marker a fumettino
- **Pannello dettaglio** a slide nel sidebar — la mappa rimane visibile
- **Carosello foto** con swipe touch, tasti ← →, lightbox fullscreen
- **Punti archiviati** visivamente differenziati (marker grigio, badge, filtro dedicato)
- **Filtri**: Tutti / Attivi / Archiviati + ricerca per titolo, tag, descrizione
- **Temi mappa**: OSM standard, CartoDB Dark, Esri Satellite

---

## Formato backup / Backup format

Il backup è uno ZIP con:
- `backup.json` — tutti i punti, reminder e log visite
- `photos/{pointId}/{filename}` — foto locali

Le foto già su Firebase Storage (URL `https://`) vengono caricate direttamente dal cloud.

---

## Configurazione porta / Port configuration

Porta di default: **8080**. Per cambiarla:

```bash
# docker-compose.yml
ports:
  - "9090:8080"   # cambia 9090 con la porta desiderata

# Makefile
PORT := 9090
```

---

## Sviluppo locale / Local development

```bash
pip install flask
./manage.sh dev   # avvia su http://localhost:8080 con hot-reload
```
