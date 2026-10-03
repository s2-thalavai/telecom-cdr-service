# Telecom CDR Service

A Spring Boot 3.5 / Java 21 REST API that ingests **large payloads** of Call Detail Records (CDRs) and
answers usage queries. It shows three ways to take in big request bodies (buffered, streaming, async),
gzip request bodies, layered size limits, parser hardening, chunked JDBC batch inserts, and idempotent
re-delivery. It comes with unit and integration tests.

| | |
|---|---|
| Stack | Spring Web, Jackson streaming, Bean Validation, Spring Data JPA + JdbcTemplate, Flyway, Actuator |
| Databases | H2 (dev, tests), MySQL 8.4 (prod, Testcontainers IT) |
| Tests | JUnit 5, Mockito, MockMvc slices, `@DataJpaTest`, TestRestTemplate on real Tomcat, Awaitility, Testcontainers, JaCoCo |
| Infra | Dockerfile, Docker Compose, Kubernetes (kustomize + HPA), Makefile, GitHub Actions |
| Tools | `scripts/gen_cdrs.py` feed generator, smoke and load tests, ingestion console UI |

## Quick start

```bash
make run                    # dev profile -> http://localhost:8080 (ingestion console)
make test                   # unit + slice tests
make verify                 # all tests + coverage
make up && make smoke       # Docker Compose (MySQL) + end-to-end checks
make gen-data N=1000000 && make load-test N=1000000
```

## Endpoints

| Method | Path | Body | Notes |
|---|---|---|---|
| `POST` | `/api/v1/cdrs/batch` | JSON `{source, batchId?, records[≤5000]}` | buffered, ≤ 10MB, all-or-nothing validation |
| `POST` | `/api/v1/cdrs/stream?source=&batchId=` | NDJSON or JSON array, optional `Content-Encoding: gzip` | streaming, ≤ 500MB, per-record validation |
| `POST` | `/api/v1/cdrs/async?source=&batchId=` | same as stream | 202 + `Location`; 503 when the queue is full |
| `GET` | `/api/v1/ingestion-jobs[/{id}]` | | job status and counts |
| `GET` | `/api/v1/subscribers/{msisdn}/usage?from=&to=` | | aggregated in SQL |
| `GET` | `/api/v1/cdrs?msisdn=&from=&to=&page=&size=` | | paged, size capped at 500 |

See [HOW_TO_RUN.md](HOW_TO_RUN.md), [docs/large-payloads.md](docs/large-payloads.md), [docs/testing.md](docs/testing.md) and [CLAUDE.md](CLAUDE.md).
# telecom-cdr-service
