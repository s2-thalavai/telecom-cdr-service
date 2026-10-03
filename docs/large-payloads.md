# Large Request Payloads – Design Notes

## The layers, and where each lives in this code base

| # | Layer | Protects | Where |
|---|---|---|---|
| 1 | Tomcat timeouts, connections, swallow size | sockets, threads | `server.tomcat.*` in `application.properties` |
| 2 | Body size limit per path (Content-Length check **and** byte counting for chunked bodies) | memory, disk, time | `filter/RequestSizeLimitFilter`, `filter/BoundedServletInputStream` |
| 3 | gzip request bodies + decompressed limit (zip bomb) | bandwidth, memory | `filter/GzipRequestDecompressionFilter` |
| 4 | Parser constraints (nesting, string, number length) | CPU, stack | `config/JacksonConfig` |
| 5 | Binding strategy: buffered DTO with `@Size(max=5000)` vs `InputStream` + `MappingIterator` | heap | `controller/CdrIngestionController`, `service/CdrIngestionService` |
| 6 | Per-record validation, capped error list | response size, memory | `service/IngestionStats` |
| 7 | Chunked transactions + JDBC batch insert + idempotency by `recordId` | DB, lock time | `repository/CdrBulkRepositoryImpl`, `persistChunk` |
| 8 | Async with a bounded queue (503 + Retry-After when full) | threads, disk | `config/AsyncConfig`, `service/AsyncIngestion*` |
| 9 | Paging and response compression | response size | `spring.data.web.pageable.*`, `server.compression.*` |
| 10 | Proxy and ingress limits | the whole path | `k8s/22-ingress.yaml` |

## Facts worth remembering

- Tomcat's `max-http-form-post-size` applies only to **form** bodies. JSON bodies have **no** default limit, which is why the filter exists.
- Neither Spring nor Tomcat decompresses **request** bodies. `server.compression` is for **responses** only.
- Jackson's `@RequestBody` builds the whole object graph, so memory grows with the payload. `MappingIterator` reads one record at a time.
- `MappingIterator` reads both NDJSON and a top-level JSON array with the same code.
- Per-record binding errors (for example an unknown enum) surface as `JsonMappingException`, and the iterator re-syncs to the next record. Syntax errors end the stream: earlier chunks stay committed, and the response carries `partialResult`.
- `PayloadTooLargeException` is an `IOException` on purpose. Parsers propagate it unchanged, and when MVC wraps it in `HttpMessageNotReadableException`, the handler unwraps it to 413.
- `IDENTITY` ids disable Hibernate insert batching, so bulk writes use `JdbcTemplate.batchUpdate`. On MySQL, `rewriteBatchedStatements=true` turns each batch into multi-row INSERTs.
- Idempotency works because earlier chunks are committed before later chunks check for existing ids. Re-sending a feed returns everything as `duplicates`.
- The async endpoint must not be `@Transactional`: the job row has to be committed before the worker thread loads it.

## Status codes

| Situation | Status |
|---|---|
| Body over limit (declared or counted), gzip expands over limit, more than `max-records-per-request` records | 413 |
| `Content-Encoding` other than gzip/identity, wrong `Content-Type` | 415 |
| Malformed JSON (with `partialResult` on streams), invalid gzip, validation errors in a batch, bad parameters | 400 |
| Async queue full | 503 + `Retry-After: 30` |
| Unknown job | 404 |
