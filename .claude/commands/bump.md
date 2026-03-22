Esegui una release completa di GeoJournal seguendo questi 7 step nell'ordine esatto, senza saltarne nessuno.

## Step 1 — Aggiorna versione

Leggi `app/build.gradle.kts` e incrementa:
- `versionCode` di 1
- `versionName` solo la PATCH (es. 0.4.30 → 0.4.31), a meno che l'utente non abbia specificato una versione diversa nell'argomento del comando

## Step 2 — Esegui i test

```bash
./geojournal.sh test
```

Se i test falliscono, interrompi immediatamente e segnala l'errore. Non proseguire.

## Step 3 — Commit bump

Fai il commit SOLO di `app/build.gradle.kts`:

```bash
git add app/build.gradle.kts
git commit -m "chore: bump versionCode=N, versionName=X.Y.Z"
```

## Step 4 — Genera AAB release

```bash
./geojournal.sh aab
```

- NON usare `./gradlew bundleRelease` → non carica `.env`, keystore non trovato
- NON usare `./gradlew assembleRelease` → genera APK, non AAB

## Step 5 — Push

```bash
git push origin main
```

## Step 6 — Tag

```bash
git tag vX.Y.Z
git push origin vX.Y.Z
```

## Step 7 — GitHub Release

```bash
gh release create vX.Y.Z --generate-notes --title "vX.Y.Z"
```

---

Al termine, riporta il link alla GitHub Release creata.
