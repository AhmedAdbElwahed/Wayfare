# Wayfare local dev stack. Jars are built on the host and copied into thin
# runtime images (see any wayfare-*/Dockerfile), so `up` packages first.
#
#   make up                              start everything
#   make rebuild svc=wayfare-auth-service   reship one service
#   make logs svc=wayfare-api-gateway     tail one service
#   make minio                           MinIO console URL + credentials
#   make clean                           stop, drop DB + MinIO volumes, mvn clean

MVN     := ./mvnw
COMPOSE := docker compose
SKIP    := -DskipTests
SERVICES ?= $(shell $(COMPOSE) config --services | grep -E 'wayfare-')

define need_svc
	@test -n "$(svc)" || { \
		echo "usage: make $@ svc=<service>"; \
		echo "services:"; \
		for s in $(SERVICES); do echo "  - $$s"; done; \
		exit 1; \
	}
endef

.DEFAULT_GOAL := help
.PHONY: help package up down restart rebuild logs ps db minio clean

help: ## List targets
	@grep -E '^[a-z-]+:.*?## .*$$' $(MAKEFILE_LIST) \
	| awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-9s\033[0m %s\n", $$1, $$2}'

package: ## Build every module jar (tests skipped)
	$(MVN) -q package $(SKIP)

up: package ## Build jars, then start the whole stack in the background
	$(COMPOSE) up -d --build
	@$(MAKE) --no-print-directory ps

down: ## Stop the stack, keeping the database volume
	$(COMPOSE) down

restart: ## Restart one service without rebuilding: make restart svc=...
	$(call need_svc)
	$(COMPOSE) restart $(svc)

rebuild: ## Repackage and restart one service: make rebuild svc=...
	$(call need_svc)
	$(MVN) -q -pl $(svc) -am package $(SKIP)
	$(COMPOSE) up -d --build $(svc)

logs: ## Tail logs for everything, or one service with svc=...
	$(COMPOSE) logs -f $(svc)

ps: ## Show container status and health
	$(COMPOSE) ps

db: ## Open a psql shell on the dev database
	$(COMPOSE) exec postgres psql -U wayfare

minio: ## Show the MinIO console URL and credentials
	@echo "console: http://localhost:$${MINIO_CONSOLE_PORT:-9001}   user: wayfare   password: wayfare-secret"
	@echo "S3 API:  http://localhost:$${MINIO_PORT:-9000}"

clean: ## Stop the stack, drop the DB and MinIO volumes, and mvn clean
	$(COMPOSE) down -v
	$(MVN) -q clean
