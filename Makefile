# Telecom CDR service – common tasks. Run `make` or `make help`.
APP_NAME  := telecom-cdr-service
VERSION   := 1.0.0
IMAGE     := $(APP_NAME):$(VERSION)
MVN       ?= mvn
KUBECTL   ?= kubectl
BASE_URL  ?= http://localhost:8080
NAMESPACE := telecom-cdr
N         ?= 100000

.DEFAULT_GOAL := help
.PHONY: help build test it verify coverage run clean docker-build docker-run env-file up down nuke logs ps tools \
	    smoke load-test gen-data sample-data ingest-sample db-shell db-reports \
	    k8s-deploy k8s-status k8s-logs k8s-port-forward k8s-delete

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## .*$$' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "}; {printf "  \033[36m%-18s\033[0m %s\n", $$1, $$2}'

# ---------------- Build & tests ----------------
build: ## Package the jar (skip tests)
	$(MVN) -B clean package -DskipTests

test: ## Unit + slice tests only (*Test.java, fast, no Docker)
	$(MVN) -B test

it: ## Integration tests only (*IT.java; MySQL IT runs if Docker is available)
	$(MVN) -B verify -Dtest=SkipUnitTests -Dsurefire.failIfNoSpecifiedTests=false

verify: ## Everything: unit + integration tests + coverage report
	$(MVN) -B clean verify

coverage: verify ## Run all tests and print the coverage report location
	@echo "Coverage report: target/site/jacoco/index.html"

run: ## Run locally with the dev profile (H2)
	./scripts/run-dev.sh

clean: ## Remove build output, local data and generated feeds
	$(MVN) -B clean
	rm -rf data sample-data/*-generated*

# ---------------- Data ----------------
gen-data: ## Generate a big gzip NDJSON feed: make gen-data N=1000000
	python3 scripts/gen_cdrs.py $(N) --gzip --prefix GEN$$(date +%s) -o sample-data/cdrs-generated.ndjson.gz
	@ls -lh sample-data/cdrs-generated.ndjson.gz

sample-data: ## Regenerate the small sample files (fresh timestamps)
	python3 scripts/gen_cdrs.py 20 --prefix SAMPLE --seed 1 -o sample-data/cdrs-small.ndjson
	python3 scripts/gen_cdrs.py 30 --prefix DIRTY --seed 2 --error-rate 0.2 --duplicate-rate 0.1 -o sample-data/cdrs-with-errors.ndjson
	python3 scripts/gen_cdrs.py 5 --prefix BATCH --seed 3 --format batch --source MSC-03 -o sample-data/batch.json

ingest-sample: ## Stream the generated feed (make gen-data first)
	curl -s -X POST --data-binary @sample-data/cdrs-generated.ndjson.gz -H "Content-Type: application/x-ndjson" \
	  -H "Content-Encoding: gzip" "$(BASE_URL)/api/v1/cdrs/stream?source=MAKE" | python3 -m json.tool

smoke: ## End-to-end large-payload checks against BASE_URL
	BASE_URL=$(BASE_URL) ./scripts/smoke-test.sh

load-test: ## Throughput test: make load-test N=1000000
	BASE_URL=$(BASE_URL) N=$(N) ./scripts/load-test.sh

# ---------------- Docker ----------------
docker-build: ## Build the Docker image
	docker build -t $(IMAGE) .

docker-run: docker-build ## Run the image alone on the dev profile (H2)
	docker run --rm -p 8080:8080 -e SPRING_PROFILES_ACTIVE=dev $(IMAGE)

env-file: ## Create .env from .env.example if missing
	@test -f .env || (cp .env.example .env && echo "Created .env from .env.example")

up: env-file ## Start MySQL + app with Docker Compose (prod profile)
	docker compose up -d --build
	@echo "App: $(BASE_URL)   (first start ~60s; 'make logs' to follow)"

down: ## Stop Compose (keeps volumes)
	docker compose down

nuke: ## Stop Compose and delete DB + spool volumes
	docker compose --profile tools down -v

logs: ## Follow app logs
	docker compose logs -f app

ps: ## Show Compose services (+ memory: docker stats)
	docker compose ps

tools: env-file ## Start Adminer DB UI on http://localhost:8081
	docker compose --profile tools up -d adminer

db-shell: ## MySQL shell in the Compose DB
	./scripts/db-shell.sh

db-reports: ## Run sql/reports.sql against the Compose DB
	docker compose exec -T mysql sh -c 'mysql -u"$$MYSQL_USER" -p"$$MYSQL_PASSWORD" "$$MYSQL_DATABASE"' < sql/reports.sql

# ---------------- Kubernetes ----------------
k8s-deploy: ## Build, load into local cluster, apply k8s/ manifests
	./scripts/k8s-deploy.sh

k8s-status: ## Pods, services, ingress, HPA
	$(KUBECTL) -n $(NAMESPACE) get pods,svc,ingress,hpa

k8s-logs: ## Follow app logs in Kubernetes
	$(KUBECTL) -n $(NAMESPACE) logs -f deployment/$(APP_NAME)

k8s-port-forward: ## Forward localhost:8080 to the service
	$(KUBECTL) -n $(NAMESPACE) port-forward svc/$(APP_NAME) 8080:80

k8s-delete: ## Delete all K8s resources including PVCs (data lost)
	./scripts/k8s-teardown.sh
