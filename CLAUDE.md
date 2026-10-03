# CLAUDE.md

Context for Claude Code (and other AI assistants) working in this repository.

## What this is

`telecom-cdr-service` is a Spring Boot 3.5 / Java 21 REST API that ingests very large Call Detail Record
feeds (hundreds of MB, millions of records) and serves usage queries. **Bounded resource usage regardless
of payload size is the core feature.** Treat limits, streaming and idempotency as behaviour under test.

## Commands

| Task | Command |
|---|---|
| Run (H2) | `make run` → http://localhost:8080 (console UI) |
| Unit + slice tests | `make test` |
| Integration tests | `make it` |
| Everything + coverage | `make verify` |
| Compose (MySQL + app) | `make up` / `make logs` / `make down` / `make nuke` |
| End-to-end checks | `make smoke` |
| Generate a feed | `make gen-data N=1000000` (or `python3 scripts/gen_cdrs.py --help`) |
| Throughput | `make load-test N=1000000` |
| Kubernetes | `make k8s-deploy` / `make k8s-port-forward` / `make k8s-delete` |

Run `make test` after every change and `make verify` before finishing.

## Layout

```
config/      PayloadProperties (telecom.payload.*), JacksonConfig (StreamReadConstraints),
             PayloadFilterConfig (filter order), AsyncConfig (bounded executor "ingestionExecutor")
filter/      RequestSizeLimitFilter, GzipRequestDecompressionFilter, BoundedServletInputStream, wrappers
controller/  CdrIngestionController (batch/stream/async), CdrQueryController, IngestionJobController
service/     CdrIngestionService (parse→validate→dedupe→chunk insert), IngestionStats,
             AsyncIngestionService (spool + submit, NOT transactional), AsyncIngestionWorker (@Async),
             IngestionJobService, PayloadSpool, CdrQueryService
repository/  CdrRepository (JPA + fragment), CdrBulkRepositoryImpl (JdbcTemplate), UsageTotals projection
```

## Invariants (keep them true, and keep their tests passing)

1. **Memory is O(chunk), not O(payload).** Never read a stream body fully (`readAllBytes`, `@RequestBody String`/`List` on stream endpoints). Only `/batch` binds a DTO, and it is capped by `@Size(max=5000)` and the 10MB default limit.
2. **Every body is bounded.** New endpoints under `/api/*` inherit the default limit. A large-body endpoint must be added to `telecom.payload.streaming-paths`. The filter order is size limit (+10), then gzip (+20).
3. **gzip is bounded twice**: compressed bytes by the size filter, decompressed bytes by the gzip filter.
4. **Idempotency by `recordId`.** Chunks are committed before later chunks check existing ids. Keep the `DuplicateKeyException` retry.
5. **Partial progress is reported, not hidden.** Malformed streams return 400 with `partialResult`, and jobs keep partial counts.
6. **Error output is bounded**: `max-reported-errors` for streams, 20 field errors for batches.
7. **Async**: the job row is committed before `worker.process` is called, the queue is bounded (`TaskRejectedException` → 503), and the spool file is always deleted.
8. **UTC everywhere**: `main()` sets the default TZ, tests and the image pass `-Duser.timezone=UTC`, and Hibernate uses `jdbc.time_zone=UTC`.

## Database

- **dev / test**: H2, Hibernate DDL. **prod**: MySQL 8.4, **Flyway owns the schema** (`ddl-auto=none`). Entity changes need a new `V<n>__*.sql`, which `MySqlFlywayIT` checks.
- Bulk writes go through `CdrBulkRepositoryImpl` (JDBC). Keep the column list in sync with the entity and the migration.
- Aggregates belong in SQL/JPQL (`summarizeUsage`). Never load rows to sum them in Java.

## Testing conventions

- `*Test` covers unit and slice tests (no full context). `*IT` tests extend `AbstractIntegrationTest` (real Tomcat and filters, temp spool, clean DB).
- Use `support/TestCdrs` for payloads. It is deterministic: type = i % 3, VOICE 60s, DATA 1000 bytes, charge 1.5, and `BASE` is midnight UTC ten days ago.
- To test byte limits without record limits, pad records with whitespace (see `paddedBody`).
- Mockito strict stubs are on. Use answer-based stubs when a method sees several different arguments.
- Async ITs must `await()` job completion **and** spool cleanup.

## Known limitations (documented trade-offs)

- The async spool is per pod (`emptyDir`). If a pod dies mid-job, that job stays PENDING or RUNNING (`sql/reports.sql` lists them). For durability, spool to object storage and use a queue.
- Cross-request races on the same `recordId` are handled by a single re-check and retry, not by locking.
- A `PayloadTooLargeException` mid-stream leaves earlier chunks committed. This is intentional, and safe because of idempotency.
