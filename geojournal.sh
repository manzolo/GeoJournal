#!/usr/bin/env bash
# =============================================================================
# GeoJournal — Management Script
# =============================================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BUILD_GRADLE="$SCRIPT_DIR/app/build.gradle.kts"
GRADLEW="$SCRIPT_DIR/gradlew"

# ── Colori ────────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo -e "${CYAN}▸ $*${RESET}"; }
success() { echo -e "${GREEN}✓ $*${RESET}"; }
warn()    { echo -e "${YELLOW}⚠ $*${RESET}"; }
error()   { echo -e "${RED}✗ $*${RESET}" >&2; }
die()     { error "$*"; exit 1; }
header()  { echo -e "\n${BOLD}${CYAN}══ $* ══${RESET}\n"; }

# ── Helpers ───────────────────────────────────────────────────────────────────
get_version_name() {
    grep 'versionName' "$BUILD_GRADLE" | sed 's/.*"\(.*\)".*/\1/'
}

get_version_code() {
    grep 'versionCode' "$BUILD_GRADLE" | grep -o '[0-9]*'
}

check_gradlew() {
    [[ -f "$GRADLEW" ]] || die "gradlew non trovato in $SCRIPT_DIR"
    chmod +x "$GRADLEW"
}

run_gradle() {
    check_gradlew
    cd "$SCRIPT_DIR"
    "$GRADLEW" "$@"
}

require_env() {
    local missing=()
    for var in "$@"; do
        [[ -z "${!var:-}" ]] && missing+=("$var")
    done
    if [[ ${#missing[@]} -gt 0 ]]; then
        die "Variabili d'ambiente mancanti: ${missing[*]}\n  Esporta le variabili o copia .env.example in .env e caricalo con: source .env"
    fi
}

load_env() {
    local envfile="$SCRIPT_DIR/.env"
    if [[ -f "$envfile" ]]; then
        info "Carico variabili da .env"
        # shellcheck disable=SC1090
        set -a; source "$envfile"; set +a
    fi
}

# ── Comandi ───────────────────────────────────────────────────────────────────

cmd_info() {
    header "GeoJournal Info"
    local vn vc
    vn=$(get_version_name)
    vc=$(get_version_code)
    echo -e "  ${BOLD}Version:${RESET}  $vn (code $vc)"
    echo -e "  ${BOLD}Branch:${RESET}   $(git -C "$SCRIPT_DIR" rev-parse --abbrev-ref HEAD)"
    echo -e "  ${BOLD}Commit:${RESET}   $(git -C "$SCRIPT_DIR" log -1 --format='%h %s')"
    echo -e "  ${BOLD}Tags:${RESET}     $(git -C "$SCRIPT_DIR" tag --sort=-v:refname | head -3 | tr '\n' ' ')"
    echo ""
    local apk_debug="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    local apk_rel="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
    local aab_rel="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
    [[ -f "$apk_debug" ]] && echo -e "  ${GREEN}APK debug:${RESET}   $(du -sh "$apk_debug" | cut -f1)  →  $apk_debug"
    [[ -f "$apk_rel"  ]] && echo -e "  ${GREEN}APK release:${RESET} $(du -sh "$apk_rel"  | cut -f1)  →  $apk_rel"
    [[ -f "$aab_rel"  ]] && echo -e "  ${GREEN}AAB release:${RESET} $(du -sh "$aab_rel"  | cut -f1)  →  $aab_rel"
}

cmd_clean() {
    header "Clean"
    run_gradle clean
    success "Clean completato"
}

cmd_test() {
    header "Unit Test (JVM)"
    run_gradle test
    success "Tutti i test superati"
}

cmd_ksp() {
    header "KSP Code Generation (debug)"
    run_gradle kspDebugKotlin
    success "KSP completato"
}

cmd_debug() {
    header "Build APK Debug"
    run_gradle assembleDebug
    local out="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    success "APK debug → $out"
}

cmd_release() {
    header "Build APK Release"
    load_env
    require_env RELEASE_KEYSTORE_PATH RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD
    export RELEASE_KEYSTORE_PATH RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD
    run_gradle assembleRelease
    local vn out_src out_dst
    vn=$(get_version_name)
    out_src="$SCRIPT_DIR/app/build/outputs/apk/release/app-release.apk"
    out_dst="$SCRIPT_DIR/app/build/outputs/apk/release/geojournal-${vn}.apk"
    [[ -f "$out_src" ]] && mv "$out_src" "$out_dst" && info "Rinominato → geojournal-${vn}.apk"
    success "APK release → $out_dst"
}

cmd_aab() {
    header "Build AAB Release (Play Store)"
    load_env
    require_env RELEASE_KEYSTORE_PATH RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD
    export RELEASE_KEYSTORE_PATH RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD
    run_gradle bundleRelease
    local out="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
    success "AAB release → $out"
}

cmd_install() {
    header "Install APK Debug su dispositivo"
    local apk="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk"
    if [[ ! -f "$apk" ]]; then
        warn "APK debug non trovato, lo buildo prima..."
        cmd_debug
    fi
    if ! command -v adb &>/dev/null; then
        die "adb non trovato nel PATH. Installa Android SDK platform-tools."
    fi
    local devices
    devices=$(adb devices | grep -v "^List" | grep "device$" | wc -l)
    [[ "$devices" -eq 0 ]] && die "Nessun dispositivo/emulatore connesso (adb devices)"
    adb install -r "$apk"
    success "Installato su dispositivo"
}

cmd_logcat() {
    header "Logcat — GeoJournal"
    if ! command -v adb &>/dev/null; then
        die "adb non trovato nel PATH."
    fi
    info "Premi Ctrl+C per uscire"
    adb logcat -v time | grep -i "geojournal\|manzolo\|E AndroidRuntime"
}

cmd_bump() {
    header "Bump versione"
    local cur_vn cur_vc new_vn new_vc
    cur_vn=$(get_version_name)
    cur_vc=$(get_version_code)

    echo -e "  Versione corrente: ${BOLD}$cur_vn${RESET} (code $cur_vc)"

    if [[ -n "${1:-}" ]]; then
        new_vn="$1"
    else
        read -rp "  Nuova versionName [invio per patch auto]: " new_vn
        if [[ -z "$new_vn" ]]; then
            # auto-incrementa patch (x.y.Z)
            IFS='.' read -r maj min pat <<< "$cur_vn"
            new_vn="${maj}.${min}.$((pat + 1))"
        fi
    fi

    new_vc=$((cur_vc + 1))

    info "Aggiorno: $cur_vn → $new_vn  |  code $cur_vc → $new_vc"
    read -rp "  Confermi? [y/N] " confirm
    [[ "${confirm,,}" == "y" ]] || die "Annullato"

    # Aggiorna build.gradle.kts
    sed -i "s/versionCode = $cur_vc/versionCode = $new_vc/" "$BUILD_GRADLE"
    sed -i "s/versionName = \"$cur_vn\"/versionName = \"$new_vn\"/" "$BUILD_GRADLE"
    success "build.gradle.kts aggiornato"

    cd "$SCRIPT_DIR"
    git add app/build.gradle.kts
    git commit -m "chore: bump versionCode=${new_vc}, versionName=${new_vn}"
    success "Commit creato"

    git push
    success "Push effettuato"

    git tag "v${new_vn}"
    git push origin "v${new_vn}"
    success "Tag v${new_vn} creato e pushato → CI avviata"

    echo ""
    read -rp "  Vuoi buildare l'AAB per Play Store ora? [Y/n] " build_aab
    if [[ "${build_aab,,}" != "n" ]]; then
        cmd_aab
        local aab="$SCRIPT_DIR/app/build/outputs/bundle/release/app-release.aab"
        if [[ -f "$aab" ]]; then
            echo ""
            success "AAB pronto per Play Console → $aab"
        fi
    fi
}

cmd_tag_list() {
    header "Tag / Releases"
    git -C "$SCRIPT_DIR" tag --sort=-v:refname | head -15
}

cmd_env_check() {
    header "Verifica variabili d'ambiente release"
    load_env
    local vars=(RELEASE_KEYSTORE_PATH RELEASE_KEYSTORE_PASSWORD RELEASE_KEY_ALIAS RELEASE_KEY_PASSWORD)
    for v in "${vars[@]}"; do
        if [[ -n "${!v:-}" ]]; then
            success "$v = ${!v}"
        else
            warn "$v → NON impostata"
        fi
    done
}

cmd_env_template() {
    local tpl="$SCRIPT_DIR/.env.example"
    if [[ -f "$tpl" ]]; then
        warn ".env.example già esistente, skip"
        return
    fi
    cat > "$tpl" <<'EOF'
# GeoJournal — Release signing env vars
# Copia in .env e compilalo (non committare .env!)
RELEASE_KEYSTORE_PATH=/path/to/geojournal-release.jks
RELEASE_KEYSTORE_PASSWORD=changeme
RELEASE_KEY_ALIAS=geojournal-release
RELEASE_KEY_PASSWORD=changeme
EOF
    success "Creato .env.example — copialo in .env e compilalo"
}

# ── Keystore / Certificati ────────────────────────────────────────────────────

_require_keytool() {
    command -v keytool &>/dev/null || die "keytool non trovato. Installa un JDK (es. sudo apt install default-jdk)."
}

_ks_path() {
    # $1 = "release" | "debug" | percorso assoluto
    case "${1:-release}" in
        release) echo "${RELEASE_KEYSTORE_PATH:-$SCRIPT_DIR/geojournal-release.jks}" ;;
        debug)   echo "${DEBUG_KEYSTORE_PATH:-$SCRIPT_DIR/geojournal-debug.jks}" ;;
        *)       echo "$1" ;;
    esac
}

_ks_password() {
    case "${1:-release}" in
        release) echo "${RELEASE_KEYSTORE_PASSWORD:-}" ;;
        debug)   echo "${DEBUG_KEYSTORE_PASSWORD:-android}" ;;
        *)       echo "" ;;
    esac
}

_ks_alias() {
    case "${1:-release}" in
        release) echo "${RELEASE_KEY_ALIAS:-geojournal-release}" ;;
        debug)   echo "${DEBUG_KEY_ALIAS:-androiddebugkey}" ;;
        *)       echo "" ;;
    esac
}

cmd_keystore_info() {
    # ./geojournal.sh ks-info [release|debug|/path/to/file.jks]
    header "Keystore Info — ${1:-release}"
    _require_keytool
    load_env
    local ks pass
    ks=$(_ks_path "${1:-release}")
    pass=$(_ks_password "${1:-release}")
    [[ -f "$ks" ]] || die "Keystore non trovato: $ks"
    if [[ -z "$pass" ]]; then
        read -rsp "  Password keystore: " pass; echo ""
    fi
    echo ""
    keytool -list -v -keystore "$ks" -storepass "$pass" 2>/dev/null \
        | grep -E "Alias name|Creation date|Entry type|Certificate fingerprint|SHA1|SHA256|Owner|Issuer|Valid from|until"
}

cmd_ks_fingerprints() {
    # ./geojournal.sh ks-fingerprints [release|debug]
    header "Fingerprint certificato — ${1:-release}"
    _require_keytool
    load_env
    local ks pass alias
    ks=$(_ks_path "${1:-release}")
    pass=$(_ks_password "${1:-release}")
    alias=$(_ks_alias "${1:-release}")
    [[ -f "$ks" ]] || die "Keystore non trovato: $ks"
    if [[ -z "$pass" ]]; then
        read -rsp "  Password keystore: " pass; echo ""
    fi
    echo ""
    local output
    output=$(keytool -list -v -keystore "$ks" -storepass "$pass" -alias "$alias" 2>/dev/null)
    local sha1 sha256
    sha1=$(echo "$output"  | grep "SHA1:"   | sed 's/.*SHA1: *//')
    sha256=$(echo "$output" | grep "SHA256:" | sed 's/.*SHA256: *//')
    echo -e "  ${BOLD}SHA-1  (Firebase / Play Console):${RESET}"
    echo -e "  ${GREEN}$sha1${RESET}"
    echo ""
    echo -e "  ${BOLD}SHA-256 (Play App Signing):${RESET}"
    echo -e "  ${GREEN}$sha256${RESET}"
    echo ""
    echo -e "  ${BOLD}SHA-1 compatto (senza ':'):${RESET}"
    echo -e "  ${CYAN}$(echo "$sha1" | tr -d ':')${RESET}"
}

cmd_ks_create_release() {
    header "Crea nuovo keystore RELEASE"
    _require_keytool
    local out="$SCRIPT_DIR/geojournal-release.jks"
    if [[ -f "$out" ]]; then
        warn "Keystore già esistente: $out"
        read -rp "  Sovrascrivere? [y/N] " ow
        [[ "${ow,,}" == "y" ]] || die "Annullato"
        mv "$out" "${out}.bak.$(date +%Y%m%d_%H%M%S)"
        info "Backup salvato con suffisso .bak.*"
    fi

    read -rp   "  Alias [geojournal-release]: "   alias;   alias="${alias:-geojournal-release}"
    read -rsp  "  Keystore password: "             ks_pass; echo ""
    read -rsp  "  Key password (invio = uguale): " k_pass;  echo ""
    k_pass="${k_pass:-$ks_pass}"
    read -rp   "  Nome (CN) [GeoJournal]: "        cn;      cn="${cn:-GeoJournal}"
    read -rp   "  Organizzazione (O) [manzolo]: "  org;     org="${org:-manzolo}"
    read -rp   "  Paese (C) [IT]: "                country; country="${country:-IT}"
    read -rp   "  Validità in anni [25]: "         years;   years="${years:-25}"

    keytool -genkeypair \
        -keystore "$out" \
        -alias "$alias" \
        -keyalg RSA -keysize 2048 \
        -validity $((years * 365)) \
        -storepass "$ks_pass" \
        -keypass "$k_pass" \
        -dname "CN=$cn, O=$org, C=$country"

    success "Keystore release creato → $out"
    echo ""
    info "Aggiungi queste righe al tuo .env:"
    echo "  RELEASE_KEYSTORE_PATH=$out"
    echo "  RELEASE_KEYSTORE_PASSWORD=$ks_pass"
    echo "  RELEASE_KEY_ALIAS=$alias"
    echo "  RELEASE_KEY_PASSWORD=$k_pass"
    echo ""
    info "SHA-1 del nuovo certificato:"
    keytool -list -v -keystore "$out" -storepass "$ks_pass" -alias "$alias" 2>/dev/null \
        | grep -E "SHA1:|SHA256:"
}

cmd_ks_create_debug() {
    header "Crea nuovo keystore DEBUG"
    _require_keytool
    local out="${1:-$SCRIPT_DIR/geojournal-debug.jks}"
    if [[ -f "$out" ]]; then
        warn "Keystore debug già esistente: $out"
        read -rp "  Sovrascrivere? [y/N] " ow
        [[ "${ow,,}" == "y" ]] || die "Annullato"
        mv "$out" "${out}.bak.$(date +%Y%m%d_%H%M%S)"
    fi

    keytool -genkeypair \
        -keystore "$out" \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 \
        -validity 10000 \
        -storepass android \
        -keypass android \
        -dname "CN=Android Debug, O=Android, C=US"

    success "Keystore debug creato → $out"
    info "  password/alias standard: android / androiddebugkey"
    keytool -list -v -keystore "$out" -storepass android -alias androiddebugkey 2>/dev/null \
        | grep -E "SHA1:|SHA256:"
}

cmd_ks_change_password() {
    # ./geojournal.sh ks-change-password [release|debug]
    header "Cambia password keystore — ${1:-release}"
    _require_keytool
    load_env
    local ks
    ks=$(_ks_path "${1:-release}")
    [[ -f "$ks" ]] || die "Keystore non trovato: $ks"

    read -rsp "  Password attuale: "  old_pass; echo ""
    read -rsp "  Nuova password: "    new_pass; echo ""
    read -rsp "  Conferma nuova: "    new_pass2; echo ""
    [[ "$new_pass" == "$new_pass2" ]] || die "Le password non coincidono"

    keytool -storepasswd -keystore "$ks" -storepass "$old_pass" -new "$new_pass"
    success "Password keystore aggiornata"
    warn "Ricorda di aggiornare .env e i GitHub Secrets!"
}

cmd_ks_export_cert() {
    # ./geojournal.sh ks-export-cert [release|debug] [output.pem]
    header "Esporta certificato in PEM — ${1:-release}"
    _require_keytool
    load_env
    local ks pass alias out_pem
    ks=$(_ks_path "${1:-release}")
    pass=$(_ks_password "${1:-release}")
    alias=$(_ks_alias "${1:-release}")
    out_pem="${2:-$SCRIPT_DIR/${1:-release}-cert.pem}"
    [[ -f "$ks" ]] || die "Keystore non trovato: $ks"
    if [[ -z "$pass" ]]; then
        read -rsp "  Password keystore: " pass; echo ""
    fi

    keytool -exportcert -rfc \
        -keystore "$ks" -storepass "$pass" \
        -alias "$alias" \
        -file "$out_pem"

    success "Certificato PEM esportato → $out_pem"
    openssl x509 -in "$out_pem" -noout -subject -issuer -dates -fingerprint -sha1 2>/dev/null || true
}

cmd_ks_backup() {
    # ./geojournal.sh ks-backup [release|debug]
    header "Backup keystore — ${1:-release}"
    load_env
    local ks backup
    ks=$(_ks_path "${1:-release}")
    [[ -f "$ks" ]] || die "Keystore non trovato: $ks"
    backup="${ks}.bak.$(date +%Y%m%d_%H%M%S)"
    cp "$ks" "$backup"
    success "Backup → $backup"
    warn "Conserva questo file in un posto sicuro (non nel repo)!"
}

cmd_ks_base64() {
    # ./geojournal.sh ks-base64 [release|debug]
    # Genera la stringa base64 da incollare in GitHub Secrets
    header "Base64 keystore — ${1:-release} (per GitHub Secrets)"
    load_env
    local ks
    ks=$(_ks_path "${1:-release}")
    [[ -f "$ks" ]] || die "Keystore non trovato: $ks"
    local b64
    b64=$(base64 -w 0 "$ks")
    echo ""
    echo -e "${BOLD}Copia questo valore in GitHub Secret → RELEASE_KEYSTORE_BASE64:${RESET}"
    echo ""
    echo "$b64"
    echo ""
    success "Lunghezza: ${#b64} caratteri"
}

cmd_apk_verify() {
    # ./geojournal.sh apk-verify [path/to/file.apk|release|debug]
    header "Verifica firma APK"
    local apk_path
    case "${1:-release}" in
        release) apk_path=$(ls -t "$SCRIPT_DIR"/app/build/outputs/apk/release/*.apk 2>/dev/null | head -1) ;;
        debug)   apk_path="$SCRIPT_DIR/app/build/outputs/apk/debug/app-debug.apk" ;;
        *)       apk_path="$1" ;;
    esac
    [[ -f "$apk_path" ]] || die "APK non trovato: $apk_path"
    info "APK: $apk_path"
    echo ""

    # apksigner (preferito) o jarsigner come fallback
    if command -v apksigner &>/dev/null; then
        apksigner verify --verbose --print-certs "$apk_path" 2>&1 | grep -E "Verified|signer|SHA|scheme"
    elif command -v jarsigner &>/dev/null; then
        jarsigner -verify -verbose -certs "$apk_path" 2>&1 | grep -E "verified|CN=|SHA"
    else
        warn "Né apksigner né jarsigner trovati."
        warn "Installa Android SDK build-tools o un JDK per verificare le firme."
    fi
}

cmd_status() {
    header "Git Status"
    cd "$SCRIPT_DIR"
    git status --short
    echo ""
    git log --oneline -8
}

cmd_help() {
    echo -e "${BOLD}GeoJournal Management Script${RESET}"
    echo -e "Versione: $(get_version_name) (code $(get_version_code))\n"
    echo -e "Uso: ${BOLD}./geojournal.sh <comando> [args]${RESET}\n"
    echo -e "${BOLD}Build:${RESET}"
    echo "  debug               Build APK debug"
    echo "  release             Build APK release (firmato)"
    echo "  aab                 Build AAB release (Play Store)"
    echo "  ksp                 Solo generazione codice KSP (debug)"
    echo "  clean               Pulisce la build"
    echo "  test                Esegue tutti gli unit test JVM"
    echo ""
    echo -e "${BOLD}Dispositivo:${RESET}"
    echo "  install             Installa APK debug su dispositivo/emulatore"
    echo "  logcat              Logcat filtrato per GeoJournal"
    echo "  apk-verify [r|d]    Verifica firma APK (release/debug)"
    echo ""
    echo -e "${BOLD}Versione / Release:${RESET}"
    echo "  bump [x.y.z]        Aggiorna versione → commit → push → tag → CI"
    echo "  tags                Lista ultimi tag/release"
    echo "  status              Git status + ultimi commit"
    echo ""
    echo -e "${BOLD}Keystore / Certificati:${RESET}"
    echo "  ks-info [r|d]       Dettagli completi keystore (release o debug)"
    echo "  ks-fingerprints [r|d]  SHA-1 e SHA-256 per Firebase / Play Console"
    echo "  ks-create-release   Genera nuovo keystore release (interattivo)"
    echo "  ks-create-debug     Genera nuovo keystore debug standard"
    echo "  ks-change-password [r|d]  Cambia password keystore"
    echo "  ks-export-cert [r|d] [out.pem]  Esporta certificato in PEM"
    echo "  ks-backup [r|d]     Copia timestampata del keystore"
    echo "  ks-base64 [r|d]     Base64 del keystore (GitHub Secrets)"
    echo ""
    echo -e "${BOLD}Configurazione:${RESET}"
    echo "  info                Mostra versione e artefatti prodotti"
    echo "  env-check           Verifica variabili d'ambiente keystore"
    echo "  env-template        Crea .env.example con le variabili necessarie"
    echo ""
    echo -e "${YELLOW}Tip:${RESET} Crea .env con le credenziali keystore, viene caricato automaticamente."
    echo -e "${YELLOW}Tip:${RESET} [r|d] = 'release' oppure 'debug' (default: release)."
}

# ── Menu interattivo ──────────────────────────────────────────────────────────
cmd_menu() {
    header "GeoJournal $(get_version_name) — Menu"
    echo -e "${BOLD}Build / Deploy${RESET}"
    echo "   1) info                   Versione + artefatti"
    echo "   2) debug                  Build APK debug"
    echo "   3) release                Build APK release"
    echo "   4) aab                    Build AAB release (Play Store)"
    echo "   5) install                Installa debug su dispositivo"
    echo "   6) logcat                 Logcat filtrato"
    echo "   7) bump                   Bump versione + tag + CI"
    echo "   8) status                 Git status"
    echo "   9) clean                  Clean build"
    echo ""
    echo -e "${BOLD}Keystore / Certificati${RESET}"
    echo "  10) ks-info release        Dettagli keystore release"
    echo "  11) ks-info debug          Dettagli keystore debug"
    echo "  12) ks-fingerprints        SHA-1 / SHA-256 release"
    echo "  13) ks-create-release      Crea nuovo keystore release"
    echo "  14) ks-create-debug        Crea nuovo keystore debug"
    echo "  15) ks-backup release      Backup keystore release"
    echo "  16) ks-base64 release      Base64 per GitHub Secrets"
    echo "  17) ks-export-cert         Esporta certificato PEM"
    echo "  18) apk-verify release     Verifica firma APK release"
    echo ""
    echo "   0) Esci"
    echo ""
    read -rp "Scelta: " choice
    case "$choice" in
        1)  cmd_info ;;
        2)  cmd_debug ;;
        3)  cmd_release ;;
        4)  cmd_aab ;;
        5)  cmd_install ;;
        6)  cmd_logcat ;;
        7)  cmd_bump ;;
        8)  cmd_status ;;
        9)  cmd_clean ;;
        10) cmd_keystore_info release ;;
        11) cmd_keystore_info debug ;;
        12) cmd_ks_fingerprints release ;;
        13) cmd_ks_create_release ;;
        14) cmd_ks_create_debug ;;
        15) cmd_ks_backup release ;;
        16) cmd_ks_base64 release ;;
        17) cmd_ks_export_cert release ;;
        18) cmd_apk_verify release ;;
        0)  exit 0 ;;
        *)  warn "Scelta non valida" ;;
    esac
}

# ── Dispatcher ────────────────────────────────────────────────────────────────
case "${1:-menu}" in
    info)                cmd_info ;;
    clean)               cmd_clean ;;
    test)                cmd_test ;;
    ksp)                 cmd_ksp ;;
    debug)               cmd_debug ;;
    release)             cmd_release ;;
    aab)                 cmd_aab ;;
    install)             cmd_install ;;
    logcat)              cmd_logcat ;;
    bump)                cmd_bump "${2:-}" ;;
    tags|tag-list)       cmd_tag_list ;;
    status)              cmd_status ;;
    env-check)           cmd_env_check ;;
    env-template)        cmd_env_template ;;
    ks-info|keystore-info) cmd_keystore_info "${2:-release}" ;;
    ks-fingerprints)     cmd_ks_fingerprints "${2:-release}" ;;
    ks-create-release)   cmd_ks_create_release ;;
    ks-create-debug)     cmd_ks_create_debug "${2:-}" ;;
    ks-change-password)  cmd_ks_change_password "${2:-release}" ;;
    ks-export-cert)      cmd_ks_export_cert "${2:-release}" "${3:-}" ;;
    ks-backup)           cmd_ks_backup "${2:-release}" ;;
    ks-base64)           cmd_ks_base64 "${2:-release}" ;;
    apk-verify)          cmd_apk_verify "${2:-release}" ;;
    help|-h|--help)      cmd_help ;;
    menu)                cmd_menu ;;
    *) error "Comando sconosciuto: $1"; cmd_help; exit 1 ;;
esac
