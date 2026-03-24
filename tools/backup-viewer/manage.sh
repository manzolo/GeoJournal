#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

IMAGE=geojournal-viewer
PORT=8080
BOLD='\033[1m'; CYAN='\033[36m'; GREEN='\033[32m'; YELLOW='\033[33m'; RED='\033[31m'; RESET='\033[0m'

_info()    { echo -e "${CYAN}▶ $*${RESET}"; }
_ok()      { echo -e "${GREEN}✓ $*${RESET}"; }
_warn()    { echo -e "${YELLOW}⚠ $*${RESET}"; }
_err()     { echo -e "${RED}✗ $*${RESET}" >&2; exit 1; }
_section() { echo -e "\n${BOLD}$*${RESET}"; }

_check_backup() {
  [[ -f backup.zip ]] || _err "backup.zip non trovato in $SCRIPT_DIR\n  Copia il backup da Google Drive prima di procedere."
}

_open_browser() {
  local url="http://localhost:$PORT"
  _info "Apertura $url …"
  xdg-open "$url" 2>/dev/null || open "$url" 2>/dev/null || _warn "Apri manualmente: $url"
}

cmd="${1:-help}"
case "$cmd" in

  build)
    _section "Build immagine Docker"
    _check_backup
    _info "Building $IMAGE …"
    docker compose build
    _ok "Immagine pronta."
    ;;

  up|start)
    _section "Avvio viewer"
    _check_backup
    _info "Build + start in background …"
    docker compose up -d --build
    sleep 1
    docker compose ps
    _ok "Viewer disponibile → http://localhost:$PORT"
    ;;

  down|stop)
    _section "Stop viewer"
    docker compose down
    _ok "Container rimosso."
    ;;

  restart)
    _section "Restart viewer"
    _check_backup
    _info "Riavvio (utile dopo aver aggiornato backup.zip) …"
    docker compose restart viewer
    _ok "Riavviato."
    ;;

  logs)
    _section "Log container"
    docker compose logs -f viewer
    ;;

  status|ps)
    _section "Stato container"
    docker compose ps
    ;;

  shell)
    _section "Shell nel container"
    docker compose exec viewer bash
    ;;

  open)
    _open_browser
    ;;

  dev)
    _section "Modalità sviluppo (locale, senza Docker)"
    _check_backup
    which flask &>/dev/null || _err "flask non trovato. Esegui: pip install flask"
    _info "Avvio Flask in debug mode su :$PORT …"
    BACKUP_PATH=./backup.zip flask --app app run --debug --port "$PORT"
    ;;

  clean)
    _section "Pulizia"
    _warn "Rimozione container, immagine e cache build …"
    docker compose down --rmi local 2>/dev/null || true
    docker builder prune -f
    _ok "Pulizia completata."
    ;;

  help|--help|-h|*)
    echo -e "\n${BOLD}GeoJournal Backup Viewer — manager${RESET}"
    echo -e "Uso: ${CYAN}./manage.sh <comando>${RESET}\n"
    printf "  ${CYAN}%-12s${RESET} %s\n" \
      "build"   "Build dell'immagine Docker" \
      "up"      "Build + avvio in background" \
      "down"    "Ferma e rimuove i container" \
      "restart" "Riavvia (usa dopo aver sostituito backup.zip)" \
      "logs"    "Segui i log in tempo reale" \
      "status"  "Mostra stato container" \
      "shell"   "Apri bash nel container" \
      "open"    "Apri il browser su http://localhost:$PORT" \
      "dev"     "Avvio locale senza Docker (richiede flask)" \
      "clean"   "Rimuovi container, immagine e cache"
    echo ""
    ;;
esac
