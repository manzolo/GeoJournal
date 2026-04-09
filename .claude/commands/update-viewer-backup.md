Aggiorna il backup.zip sul server home-server.lan scaricandolo da Google Drive, poi riavvia il viewer.

Il server è `root@home-server.lan`, path `/root/geojournal-viewer`.
Lo script sul server è `/root/geojournal-viewer/update-backup.sh`.
Il log è in `/var/log/geojournal-update.log`.

## Step 1 — Esegui lo script di aggiornamento sul server

```bash
ssh root@home-server.lan "/root/geojournal-viewer/update-backup.sh"
```

Se il comando fallisce (exit code != 0), interrompi e segnala l'errore.

## Step 2 — Verifica il log

```bash
ssh root@home-server.lan "tail -20 /var/log/geojournal-update.log"
```

Controlla che l'ultima riga contenga "Completato." e che rclone non abbia riportato errori.

## Step 3 — Verifica che il container sia running

```bash
ssh root@home-server.lan "docker compose -f /root/geojournal-viewer/docker-compose.yml ps"
```

Conferma che `geojournal-viewer` risulti `running`.

---

Al termine riporta:
- se il backup è stato aggiornato o era già aggiornato (rclone non ha scaricato nulla)
- stato finale del container
