Esegui il deploy del backup-viewer seguendo questi 3 step nell'ordine esatto, senza saltarne nessuno.

Il sorgente è in `tools/backup-viewer/` nel repo.
L'immagine Docker Hub è `manzolo/geojournal-viewer`.
Il server di destinazione è `root@home-server.lan`, path `/root/geojournal-viewer`.

## Step 1 — Build locale

Dalla directory `tools/backup-viewer/`:

```bash
cd tools/backup-viewer && docker build -t manzolo/geojournal-viewer .
```

Se il build fallisce, interrompi e segnala l'errore.

## Step 2 — Push su Docker Hub

```bash
docker push manzolo/geojournal-viewer:latest
```

## Step 3 — Pull e deploy sul server

```bash
ssh root@home-server.lan "cd /root/geojournal-viewer && docker compose pull && docker compose up -d"
```

Al termine conferma che il container risulta `Started` o `Running`.
