.DEFAULT_GOAL := help

.PHONY: build test run run-all stop clean docker-build frontend-install frontend-run help

build: ## Build the backend jar (mvn package, skip tests)
	mvn -q package -DskipTests

test: ## Run backend unit/integration tests
	mvn -q test

run: ## Start the 4 local instances (scripts/start-local.sh)
	scripts/start-local.sh

run-all: ## Start the 4 local instances plus the frontend
	scripts/start-local.sh --with-frontend

stop: ## Stop all local instances (and frontend if running)
	scripts/stop-local.sh

clean: ## Remove build artifacts, logs, pids, and local data
	mvn -q clean
	rm -rf data logs .pids

docker-build: ## Build the backend Docker image
	docker build -t distributed-batch-orchestrator:1.0.0 .

frontend-install: ## Install frontend dependencies
	cd frontend && npm install

frontend-run: ## Start the frontend dev server (npm start)
	cd frontend && npm start

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(MAKEFILE_LIST) | sort | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-18s\033[0m %s\n", $$1, $$2}'
