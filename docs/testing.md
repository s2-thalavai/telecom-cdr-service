# Testing Strategy

| Layer | Naming | Plugin | Command | Runs |
|---|---|---|---|---|
| Unit, web slice, JPA slice | `*Test.java` | Surefire | `make test` | JUnit 5, Mockito, `@WebMvcTest`, `@DataJpaTest` (H2) |
| Integration | `*IT.java` | Failsafe | `make it` | `@SpringBootTest(RANDOM_PORT)`, TestRestTemplate, Awaitility, Testcontainers |
| All + coverage | | JaCoCo | `make verify` | report at `target/site/jacoco/index.html` |

Both plugins run with `-Duser.timezone=UTC`, matching `main()` and the Docker image. JDBC `Timestamp` writes and JPA `Instant` reads must agree on the zone. If you run tests from an IDE, add `-Duser.timezone=UTC` to the run configuration.

## Unit tests

| Class | Covers |
|---|---|
| `BoundedServletInputStreamTest` | bulk and single-byte counting, limit message, non-blocking reads unsupported |
| `RequestSizeLimitFilterTest` | 413 on declared Content-Length (chain never called, `Connection: close`), larger limit for streaming paths, context path, **chunked body counted while reading**, `getReader()` bounded |
| `GzipRequestDecompressionFilterTest` | decompression, Content-Encoding/Length hidden from downstream, identity passthrough, 415 for `br`, **zip bomb stopped** |
| `CdrIngestionServiceTest` | NDJSON chunking (3 inserts for 7 records at chunk 3), JSON array, validation and unknown-enum rejections with record index, error cap, duplicates in-chunk and in DB, **DuplicateKeyException retry**, malformed JSON keeps earlier records, record limit, parser nesting constraint, size-limit exception propagates unchanged, empty body, buffered batch chunking |
| `AsyncIngestionServiceTest` | hand-off to worker, **queue full → job FAILED + spool deleted + 503**, spool failure creates no job, job-creation failure deletes spool |
| `PayloadSpoolTest` | write/delete, failed write leaves no file |
| `CdrQueryServiceTest` | totals mapping, null aggregates → zeros, range validation |
| `IngestionJobTest` | lifecycle, partial counts on failure, message truncation |
| `CdrIngestionControllerTest` | batch binding and generated batchId, `@Size(max=5000)`, **validation errors capped at 20 with totalErrors**, missing/blank source, 415, 413 mappings, `partialResult`, 202 + Location, 503 + Retry-After |
| `CdrQueryControllerTest` | Instant binding, msisdn validation, **page size capped at 500**, default sort |
| `IngestionJobControllerTest` | job JSON, 404 |
| `CdrRepositoryTest` (`@DataJpaTest`) | JDBC batch insert readable via JPA (UTC round trip), existing-id lookup, duplicate → `DuplicateKeyException`, **JPQL aggregate**, paged finder range and order |

## Integration tests

The `test` profile (`src/test/resources/application-test.properties`) shrinks the limits so they're cheap to hit:

| Setting | Test value |
|---|---|
| default body limit | 64KB |
| streaming body limit | 2MB |
| decompressed limit | 4MB |
| chunk size | 50 |
| max records per request | 5,000 |
| async workers / queue | 1 / 2 |

| Class | Covers |
|---|---|
| `CdrStreamIngestionIT` | 1,000-record NDJSON + **idempotent resend**, gzip, JSON array, rejected records, **declared and chunked bodies over 2MB → 413** (padded with whitespace: many bytes, few records), **gzip bomb → 413**, `br` → 415, fake gzip → 400, malformed stream keeps 60 records, 5,001 records → 413 with 5,000 kept, batch endpoint OK / over 64KB → 413 / gzip batch |
| `CdrAsyncIngestionIT` | 202 → poll → COMPLETED, spool cleaned up, malformed → FAILED with partial counts, over-limit → no job and no spool file, job listing, 404 |
| `CdrQueryIT` | usage aggregation per subscriber, empty range → zeros, paging newest-first, bad range → 400 |
| `MySqlFlywayIT` | prod profile on **MySQL 8.4 (Testcontainers)**: Flyway V1, 3,000 gzip records, dedup on resend, usage on MySQL. Skipped without Docker |

## Running one test

```bash
mvn test -Dtest=CdrIngestionServiceTest
mvn test -Dtest='CdrIngestionServiceTest#malformedJsonKeepsEarlierRecordsAndReportsPartialResult'
mvn verify -Dtest=SkipUnitTests -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=CdrStreamIngestionIT
```
