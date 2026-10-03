# How to Run

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 21 | local run, tests |
| Maven | 3.9+ | local run, tests (or build inside Docker) |
| Python | 3.8+ | `scripts/gen_cdrs.py`, smoke and load tests |
| Docker + Compose v2 | recent | Compose stack, image, MySQL integration test |
| kubectl + minikube / kind / Docker Desktop | recent | Kubernetes |
| make, bash, curl, gzip | any | Makefile, scripts (Windows: WSL2 or Git Bash) |

There is no Maven wrapper bundled. Run `mvn -N wrapper:wrapper` once if you want `./mvnw`.

---

## 1. Local (dev profile, H2)

```bash
make run          # or: mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

| URL | What |
|---|---|
| http://localhost:8080 | ingestion console (send feeds, watch jobs, usage) |
| http://localhost:8080/h2-console | JDBC URL `jdbc:h2:mem:telecomdb`, user `sa`, empty password |
| http://localhost:8080/actuator/health | health |

Try it:

```bash
curl -X POST --data-binary @sample-data/cdrs-small.ndjson -H "Content-Type: application/x-ndjson" \
     "http://localhost:8080/api/v1/cdrs/stream?source=MSC-01"
curl -X POST --data-binary @sample-data/cdrs-with-errors.ndjson -H "Content-Type: application/x-ndjson" \
     "http://localhost:8080/api/v1/cdrs/stream?source=MSC-02"          # partial acceptance report
curl -X POST -H "Content-Type: application/json" --data-binary @sample-data/batch.json \
     http://localhost:8080/api/v1/cdrs/batch
```

The sample files carry timestamps from when they were generated. Run `make sample-data` to refresh them.

## 2. Tests

```bash
make test         # unit + @WebMvcTest + @DataJpaTest   (mvn test)
make it           # integration tests (*IT)
make verify       # all + JaCoCo coverage               (mvn clean verify)
```

`MySqlFlywayIT` starts MySQL 8.4 with Testcontainers and is **skipped when Docker isn't running**. See [docs/testing.md](docs/testing.md).

## 3. Docker Compose (MySQL + app, prod profile)

```bash
make up                         # creates .env, builds, starts MySQL + app
make logs
make smoke                      # ~20k-record end-to-end run (N=... to change)
make gen-data N=1000000         # ~1M records, gzip
make load-test N=1000000        # prints records/s; watch `docker stats telecom-cdr-app` meanwhile
make db-reports                 # sql/reports.sql
make tools                      # Adminer at http://localhost:8081 (server: mysql)
make down / make nuke
```

Limits and tuning come from `.env` (`TELECOM_PAYLOAD_*`, `DB_POOL_SIZE`). The app container is capped at 1GB of memory, which is enough for any feed size because nothing buffers the whole payload.

## 4. Kubernetes (minikube example)

```bash
minikube start --cpus=4 --memory=8g
minikube addons enable ingress
minikube addons enable metrics-server
make k8s-deploy
make k8s-status
make k8s-port-forward           # http://localhost:8080
make smoke                      # in another terminal
make k8s-delete
```

| Manifest | Large-payload choice |
|---|---|
| `22-ingress.yaml` | `proxy-body-size: 512m` (nginx default is 1MB), `proxy-request-buffering: off`, 600s timeouts |
| `20-app-deployment.yaml` | 1Gi memory limit (flat), `emptyDir` spool and tmp, 90s grace period for in-flight feeds |
| `10-mysql.yaml` | larger InnoDB buffer pool, `max-allowed-packet=64M` for multi-row inserts |
| `23-hpa.yaml` | 2–6 replicas on CPU |

## 5. Generating feeds

```bash
python3 scripts/gen_cdrs.py --help
python3 scripts/gen_cdrs.py 1000000 --gzip -o sample-data/big-generated.ndjson.gz
python3 scripts/gen_cdrs.py 50000 --error-rate 0.02 --duplicate-rate 0.01 -o sample-data/dirty-generated.ndjson
python3 scripts/gen_cdrs.py 4000 --format batch --source MSC-03 -o sample-data/batch-generated.json
```

Change `--prefix` between runs if you want new records. The same prefix and seed produce the same `recordId`s, which are reported as duplicates.

## Configuration reference

| Property / env var | Default | Meaning |
|---|---|---|
| `telecom.payload.default-max-request-size` | 10MB | body limit, normal endpoints |
| `telecom.payload.streaming-max-request-size` | 500MB | body limit, `streaming-paths` |
| `telecom.payload.max-decompressed-size` | 2GB | gzip expansion limit |
| `telecom.payload.chunk-size` | 1000 | records per transaction and JDBC batch |
| `telecom.payload.max-records-per-request` | 5,000,000 | record cap per feed |
| `telecom.payload.max-reported-errors` | 100 | error details returned |
| `telecom.payload.async-workers` / `async-queue-capacity` | 2 / 20 | async backpressure |
| `TELECOM_SPOOL_DIRECTORY` | `./data/spool` | async spool |
| `server.tomcat.max-swallow-size` | 10MB | rejected-body drain |
| `spring.data.web.pageable.max-page-size` | 500 | response size cap |
| `DB_HOST` … `DB_PASSWORD`, `DB_POOL_SIZE` | – | prod database |

## Troubleshooting

| Symptom | Fix |
|---|---|
| 413 with an **HTML** body | A proxy rejected it, not the app. Raise `proxy-body-size` / `client_max_body_size` |
| 413 from the app for a gzip feed | The decompressed size exceeded `max-decompressed-size`, or the record count exceeded `max-records-per-request` |
| Client sees "connection reset" on 413 | The body was bigger than `server.tomcat.max-swallow-size`. That's expected for huge rejected bodies |
| Ingestion is slow on MySQL | Check that `rewriteBatchedStatements=true` is in the URL, then tune `chunk-size` and the InnoDB buffer pool |
| Usage numbers off by hours | Timezone mismatch. Run the JVM with `-Duser.timezone=UTC` (the Makefile, image and tests already do) |
| Everything reported as `duplicates` | Those `recordId`s were already ingested. Use a new `--prefix` |
| 503 on `/cdrs/async` | The queue is full. Retry after `Retry-After`, or raise `async-workers` / `async-queue-capacity` |
| Async job stuck RUNNING after a restart | Per-pod spool limitation (see CLAUDE.md). Re-send the feed; it's idempotent |
| `MySqlFlywayIT` skipped | Docker isn't running |
