Esegui una release completa di GeoJournal seguendo questi 9 step nell'ordine esatto, senza saltarne nessuno.

## Step 1 — Aggiorna versione

Leggi `app/build.gradle.kts` e incrementa:
- `versionCode` di 1
- `versionName` solo la PATCH (es. 0.4.30 → 0.4.31), a meno che l'utente non abbia specificato una versione diversa nell'argomento del comando

## Step 2 — Esegui i test

```bash
./geojournal.sh test
```

Se i test falliscono, interrompi immediatamente e segnala l'errore. Non proseguire.

## Step 3 — Commit modifiche pendenti + bump

**Step 3a** — Se ci sono modifiche pendenti (escluso `app/build.gradle.kts`), committale prima:

```bash
git add -A
git reset HEAD app/build.gradle.kts
git diff --cached --quiet || git commit -m "<descrizione sintetica delle modifiche>"
```

Usa `git diff --cached --name-only` per capire cosa stai committando e scegli un messaggio adeguato (es. `feat: ...`, `fix: ...`, `refactor: ...`).

**Step 3b** — Poi committa solo il bump di versione:

```bash
git add app/build.gradle.kts
git commit -m "chore: bump versionCode=N, versionName=X.Y.Z"
```

## Step 4 — Aggiorna CHANGELOG.md

Aggiorna (o crea se non esiste) `CHANGELOG.md` nella root del progetto seguendo il formato [Keep a Changelog](https://keepachangelog.com/):

```markdown
## [X.Y.Z] - YYYY-MM-DD
### Added / Fixed / Changed / Removed
- <voce sintetica per ogni commit dal tag precedente>
```

Per ottenere i commit dall'ultima versione precedente usa:
```bash
git log --oneline $(git describe --tags --abbrev=0 HEAD~1)..HEAD
```

Escludi i commit di tipo `chore: bump` e `docs: update CHANGELOG` dall'elenco. Raggruppa le voci per tipo (`feat`→Added, `fix`→Fixed, `refactor`/`chore`→Changed, ecc.). Aggiungi la nuova sezione in cima (sotto `# Changelog`), prima della sezione precedente. Poi committa:

```bash
git add CHANGELOG.md
git commit -m "docs: update CHANGELOG for vX.Y.Z"
```

## Step 5 — Genera AAB release

```bash
./geojournal.sh aab
```

- NON usare `./gradlew bundleRelease` → non carica `.env`, keystore non trovato
- NON usare `./gradlew assembleRelease` → genera APK, non AAB

## Step 6 — Push

```bash
git push origin main
```

## Step 7 — Tag

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

## Step 8 — GitHub Release

```bash
gh release create vX.Y.Z --generate-notes --title "vX.Y.Z"
```

## Step 9 — Aggiorna memoria

Aggiorna la versione nei file di memoria del progetto:

- In `/home/manzolo/.claude/projects/-home-manzolo-AndroidStudioProjects-GeoJournal/memory/project_geojournal_overview.md`: aggiorna la riga `**Versione corrente:**` con il nuovo versionName e versionCode, e aggiungi le feature/fix rilevanti della release nella tabella roadmap.
- In `/home/manzolo/.claude/projects/-home-manzolo-AndroidStudioProjects-GeoJournal/memory/MEMORY.md`: aggiorna la riga "Versione corrente" nelle Note rapide.

---

Al termine, riporta il link alla GitHub Release creata.
