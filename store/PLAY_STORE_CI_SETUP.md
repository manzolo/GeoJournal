# Deploy automatico su Google Play Store via GitHub Actions

Guida per configurare il deploy automatico di un'app Android su Google Play Store
tramite GitHub Actions, usando un Service Account Google.

---

## Prerequisiti

- App già pubblicata su Google Play Console (almeno una volta manualmente)
- Repository GitHub con i secrets del keystore già configurati
- Workflow GitHub Actions che builda l'AAB (`bundleRelease`)

---

## Passo 1 — Crea il Service Account su Google Cloud Console

1. Vai su [console.cloud.google.com](https://console.cloud.google.com)
2. Seleziona il progetto collegato alla tua app (es. lo stesso progetto Firebase)
3. Menu → **IAM e amministrazione → Account di servizio**
4. Clicca **Crea account di servizio**
   - Nome: es. `play-deploy`
   - ID: viene generato automaticamente
   - Clicca **Crea e continua**
5. Nella schermata "Concedi l'accesso": **non assegnare nessun ruolo** qui → clicca **Continua** → **Fine**
6. Clicca sull'account appena creato → tab **Chiavi**
7. **Aggiungi chiave → Crea nuova chiave → JSON** → Scarica il file
   - ⚠️ Conserva questo file: serve subito e non è recuperabile

---

## Passo 2 — Collega il Service Account a Google Play Console

1. Vai su [play.google.com/console](https://play.google.com/console)
2. Menu in alto a sinistra → **Setup → API access**
3. Nella sezione "Google Cloud project" clicca **View in Google Cloud Console**
   (questo collega Play Console al tuo progetto Google Cloud)
4. Torna su Play Console → **Setup → API access**
5. Nella lista "Service accounts" troverai l'account appena creato
6. Clicca **Grant access** accanto ad esso
7. Assegna il ruolo: **Release Manager**
   (permette di caricare AAB e gestire le release, senza accesso a dati finanziari)
8. Clicca **Invite user** → **Send invite**

---

## Passo 3 — Aggiungi il secret su GitHub

1. Vai su `github.com/<utente>/<repo>` → **Settings → Secrets and variables → Actions**
2. Clicca **New repository secret**
   - **Name:** `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON`
   - **Value:** incolla il contenuto **completo** del file JSON scaricato al Passo 1
3. Clicca **Add secret**

---

## Passo 4 — Configura il workflow GitHub Actions

Aggiungi questo step al tuo workflow, **dopo** la build dell'AAB:

```yaml
- name: Upload AAB to Google Play
  if: startsWith(github.ref, 'refs/tags/')
  uses: r0adkll/upload-google-play@v1
  with:
    serviceAccountJsonPlainText: ${{ secrets.GOOGLE_PLAY_SERVICE_ACCOUNT_JSON }}
    packageName: com.tuo.package.name
    releaseFiles: app/build/outputs/bundle/release/app-release.aab
    track: internal
    status: completed
```

### Track disponibili

| Track | Descrizione |
|---|---|
| `internal` | Internal Testing (fino a 100 tester, review istantanea) |
| `alpha` | Closed Testing |
| `beta` | Open Testing |
| `production` | Produzione |

**Consiglio:** usa sempre `internal` nel workflow CI. Poi promuovi manualmente
da Play Console verso i track superiori quando sei pronto.

---

## Passo 5 — Evitare i warning Node.js (opzionale ma consigliato)

Aggiungi questa variabile d'ambiente al job per usare Node.js 24:

```yaml
jobs:
  build:
    runs-on: ubuntu-latest
    env:
      FORCE_JAVASCRIPT_ACTIONS_TO_NODE24: true
```

---

## Flusso completo dopo la configurazione

```
/bump (locale)
  → incrementa versionCode/versionName
  → commit + tag vX.Y.Z
  → git push origin vX.Y.Z
      → GitHub Actions si attiva
          → build APK + AAB (firmati con release keystore)
          → crea GitHub Release con APK allegato
          → carica AAB su Play Store (internal track)
              → vai su Play Console e promuovi manualmente
```

---

## Note importanti

- Il **versionCode** deve essere sempre crescente — Play Store rifiuta versioni con
  versionCode uguale o inferiore a quello già pubblicato
- Il Service Account non ha accesso ai dati finanziari né agli utenti: solo alle release
- Il file JSON del Service Account è sensibile: non committarlo mai nel repo
- La firma dell'AAB deve usare lo stesso keystore registrato su Play Store:
  verifica che `RELEASE_KEYSTORE_BASE64` su GitHub corrisponda al tuo `.jks` locale
