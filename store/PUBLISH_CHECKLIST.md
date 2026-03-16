# Checklist pubblicazione Google Play Store

## 1. Account sviluppatore Google Play
- [ ] Crea account su https://play.google.com/console (costo una tantum: $25)
- [ ] Verifica identità (carta di credito / documento)

---

## 2. Build release

```bash
# Assicurati che le env var del keystore siano impostate
export RELEASE_KEYSTORE_PATH=/path/to/geojournal-release.jks
export RELEASE_KEYSTORE_PASSWORD=...
export RELEASE_KEY_ALIAS=...
export RELEASE_KEY_PASSWORD=...

# Genera l'App Bundle (formato preferito dal Play Store)
./gradlew bundleRelease

# L'AAB si trova in:
# app/build/outputs/bundle/release/app-release.aab
```

---

## 3. Firebase — SHA-1 del keystore di release

Per far funzionare Google Sign-In in produzione devi registrare il fingerprint
SHA-1 del keystore di release nella Firebase Console.

```bash
keytool -list -v -keystore /path/to/geojournal-release.jks -alias geojournal-key
```

Poi: Firebase Console → Project Settings → Android app → Aggiungi fingerprint SHA-1

---

## 4. Privacy Policy (OBBLIGATORIA)

Google richiede una Privacy Policy per le app con login o accesso alla posizione.
Puoi usare un generatore gratuito come https://www.privacypolicygenerator.info/ o
https://app.privacypolicies.com/

Dati da dichiarare:
- Posizione GPS (solo in uso, per salvare coordinate)
- Account Google (autenticazione opzionale)
- Foto (opzionale, scattate dall'utente)
- Firestore: ID utente e punti geografici (solo se loggato)

Pubblica la policy su una pagina web pubblica (es. GitHub Pages) e incolla l'URL
nel Play Console.

---

## 5. Play Console — Crea l'app

1. Play Console → "Crea app"
2. Nome: `GeoJournal – Diario dei luoghi`
3. Lingua predefinita: Italiano
4. Tipo: App
5. Gratuita / a pagamento: Gratuita

---

## 6. Store listing

### Testi
- [ ] Titolo (max 30 car.): vedi `store/title.txt`
- [ ] Descrizione breve (max 80 car.): vedi `store/short_description_it.txt` / `_en.txt`
- [ ] Descrizione completa (max 4000 car.): vedi `store/description_it.txt` / `_en.txt`
- [ ] Aggiungi traduzione inglese nella sezione "Traduzioni"

### Grafica
- [ ] Icona app (512x512 PNG, già presente nel progetto): `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png`
- [ ] Feature graphic (1024x500 PNG): vedi `store/screenshots/feature_graphic.png`
- [ ] Screenshot telefono (min 2, max 8): vedi `store/screenshots/`

---

## 7. Classificazione contenuti

Play Console → Classificazione contenuti → Compila il questionario
- Nessuna violenza, nessun contenuto per adulti
- Classificazione attesa: **PEGI 3 / Everyone**

---

## 8. Data Safety (Sicurezza dei dati)

Play Console → Sicurezza dei dati → Dichiara:

| Tipo di dato | Raccolta | Condivisione | Uso |
|---|---|---|---|
| Posizione approssimativa | Solo se concessa | No | Funzionalità app |
| Email | Solo se login | No | Autenticazione |
| Foto | Solo se concessa | No | Funzionalità app |
| ID utente (Firebase) | Solo se login | No | Sincronizzazione |

Nota: se l'utente usa la modalità offline/guest, non viene raccolto nulla.

---

## 9. Prezzi e distribuzione

- [ ] Gratuita
- [ ] Paesi: tutti (o seleziona)
- [ ] Classificazione età: PEGI 3

---

## 10. Upload AAB e test interno

1. Play Console → Release → Test → Test interno → Crea release
2. Carica `app-release.aab`
3. Aggiungi te stesso come tester
4. Installa e testa l'app dal link di test interno
5. Verifica Google Sign-In con il keystore di release

---

## 11. Promozione a produzione

Quando sei soddisfatto del test interno:
1. Play Console → Release → Produzione → Crea release
2. Carica lo stesso AAB (o uno nuovo)
3. Inserisci note di rilascio (IT + EN)
4. Invia per revisione (Google ci impiega 1-3 giorni lavorativi)

---

## Cose ancora da fare nell'app prima del Play Store

- [ ] **Task 10 (parziale)**: pull dati da Firestore al login
       → senza questo, reinstallare l'app fa perdere i dati cloud
- [ ] **Privacy Policy URL**: da aggiungere in app (link in ProfileScreen) e nel Play Console
- [ ] Verificare che Google Sign-In funzioni con keystore di release (SHA-1 registrato)
- [ ] Icona app ad alta risoluzione 512x512 (verificare qualità)
