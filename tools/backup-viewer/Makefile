IMAGE     := manzolo/geojournal-viewer
CONTAINER := geojournal-viewer
PORT      := 8080

.PHONY: build run up down restart logs shell clean open dev help

help: ## Mostra questo help
	@grep -E '^[a-zA-Z_-]+:.*##' $(MAKEFILE_LIST) | \
	  awk 'BEGIN {FS = ":.*## "}; {printf "  \033[36m%-12s\033[0m %s\n", $$1, $$2}'

build: ## Build dell'immagine Docker
	docker build -t $(IMAGE) .

run: build ## Build + run in foreground (ctrl+C per fermare)
	docker run --rm -p $(PORT):8080 \
	  -v "$(PWD)/backup.zip:/data/backup.zip:ro" \
	  --name $(CONTAINER) $(IMAGE)

up: ## Avvia con docker-compose in background
	docker compose up -d --build

down: ## Ferma e rimuove i container
	docker compose down

restart: ## Riavvia il container (utile dopo aver cambiato il backup)
	docker compose restart viewer

logs: ## Segui i log in tempo reale
	docker compose logs -f viewer

shell: ## Shell bash nel container
	docker compose exec viewer bash

open: ## Apri il browser su localhost:$(PORT)
	xdg-open http://localhost:$(PORT) 2>/dev/null || open http://localhost:$(PORT) 2>/dev/null || echo "Apri http://localhost:$(PORT)"

dev: ## Run locale senza Docker (richiede flask installato)
	BACKUP_PATH=./backup.zip flask --app app run --debug --port $(PORT)

push: ## Push immagine su Docker Hub
	docker push $(IMAGE):latest

clean: ## Rimuovi container, immagine e cache build
	docker compose down --rmi local 2>/dev/null || true
	docker rmi $(IMAGE) 2>/dev/null || true
	docker builder prune -f
