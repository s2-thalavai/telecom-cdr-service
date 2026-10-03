package com.telecom.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.MappingIterator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.telecom.config.PayloadProperties;
import com.telecom.dto.CdrBatchRequest;
import com.telecom.dto.CdrRequest;
import com.telecom.dto.IngestionSummary;
import com.telecom.exception.MalformedPayloadException;
import com.telecom.exception.TooManyRecordsException;
import com.telecom.repository.CdrRepository;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Core ingestion: parse incrementally, validate per record, de-duplicate, insert in chunks.
 * Memory use is O(chunk size), independent of payload size.
 */
@Service
public class CdrIngestionService {

    private static final Logger log = LoggerFactory.getLogger(CdrIngestionService.class);

    private final CdrRepository repository;
    private final Validator validator;
    private final TransactionTemplate transactionTemplate;
    private final ObjectReader cdrReader;
    private final PayloadProperties properties;

    public CdrIngestionService(CdrRepository repository, Validator validator, TransactionTemplate transactionTemplate,
                               ObjectMapper objectMapper, PayloadProperties properties) {
        this.repository = repository;
        this.validator = validator;
        this.transactionTemplate = transactionTemplate;
        this.cdrReader = objectMapper.readerFor(CdrRequest.class);   // same constraints as MVC
        this.properties = properties;
    }

    /** Buffered batch: already parsed and validated by @Valid; persisted in chunks. */
    public IngestionSummary ingestBatch(CdrBatchRequest request, String batchId) {
        IngestionStats stats = new IngestionStats(properties.maxReportedErrors());
        List<CdrRequest> records = request.records();
        stats.received(records.size());
        for (int from = 0; from < records.size(); from += properties.chunkSize()) {
            int to = Math.min(from + properties.chunkSize(), records.size());
            persistChunk(new ArrayList<>(records.subList(from, to)), request.source(), batchId, stats);
        }
        IngestionSummary summary = stats.toSummary(batchId, request.source());
        log.info("Batch {} from {}: received={} accepted={} duplicates={} rejected={} in {}ms", batchId,
                request.source(), summary.received(), summary.accepted(), summary.duplicates(),
                summary.rejected(), summary.durationMs());
        return summary;
    }

    /** Streaming: JSON array or NDJSON, one record in memory at a time. */
    public IngestionSummary ingestStream(InputStream body, String source, String batchId) throws IOException {
        IngestionStats stats = new IngestionStats(properties.maxReportedErrors());
        List<CdrRequest> chunk = new ArrayList<>(properties.chunkSize());

        try (MappingIterator<CdrRequest> records = cdrReader.readValues(body)) {
            while (records.hasNextValue()) {
                long index = stats.received() + 1;
                if (index > properties.maxRecordsPerRequest()) {
                    persistChunk(chunk, source, batchId, stats);
                    throw new TooManyRecordsException("More than " + properties.maxRecordsPerRequest()
                            + " records in one request; split the feed. Records 1.." + (index - 1)
                            + " were processed.");
                }
                stats.received(1);

                CdrRequest record;
                try {
                    record = records.nextValue();
                } catch (JsonMappingException e) {
                    // wrong type / unknown enum etc.: skip this record, the iterator re-syncs to the next one
                    stats.rejected(index, null, "Unreadable record: " + e.getOriginalMessage());
                    continue;
                }

                Set<ConstraintViolation<CdrRequest>> violations = validator.validate(record);
                if (!violations.isEmpty()) {
                    stats.rejected(index, record.recordId(), describe(violations));
                    continue;
                }

                chunk.add(record);
                if (chunk.size() >= properties.chunkSize()) {
                    persistChunk(chunk, source, batchId, stats);
                }
            }
        } catch (JsonProcessingException e) {
            // broken syntax / parser constraints: keep what was read before the error, report the rest
            persistChunk(chunk, source, batchId, stats);
            throw new MalformedPayloadException("Payload became unreadable after record " + stats.received()
                    + ": " + e.getOriginalMessage(), stats.toSummary(batchId, source));
        }

        persistChunk(chunk, source, batchId, stats);
        IngestionSummary summary = stats.toSummary(batchId, source);
        log.info("Stream {} from {}: received={} accepted={} duplicates={} rejected={} in {}ms", batchId, source,
                summary.received(), summary.accepted(), summary.duplicates(), summary.rejected(), summary.durationMs());
        return summary;
    }

    /** One short transaction per chunk; duplicates (by recordId) are skipped, so re-sending a feed is safe. */
    private void persistChunk(List<CdrRequest> chunk, String source, String batchId, IngestionStats stats) {
        if (chunk.isEmpty()) {
            return;
        }
        Map<String, CdrRequest> unique = new LinkedHashMap<>();
        for (CdrRequest r : chunk) {
            if (unique.putIfAbsent(r.recordId(), r) != null) {
                stats.duplicates(1);                                   // duplicate inside this chunk
            }
        }
        // earlier chunks are already committed, so this also catches duplicates across chunks
        List<CdrRequest> fresh = withoutExisting(unique.values());
        stats.duplicates(unique.size() - fresh.size());

        if (!fresh.isEmpty()) {
            try {
                transactionTemplate.executeWithoutResult(s -> repository.batchInsert(fresh, source, batchId));
                stats.accepted(fresh.size());
            } catch (DuplicateKeyException e) {
                // a concurrent request inserted some of these ids meanwhile: re-check once and retry
                List<CdrRequest> retry = withoutExisting(fresh);
                stats.duplicates(fresh.size() - retry.size());
                if (!retry.isEmpty()) {
                    transactionTemplate.executeWithoutResult(s -> repository.batchInsert(retry, source, batchId));
                }
                stats.accepted(retry.size());
            }
        }
        chunk.clear();
    }

    private List<CdrRequest> withoutExisting(Collection<CdrRequest> records) {
        Set<String> existing = repository.findExistingRecordIds(
                records.stream().map(CdrRequest::recordId).toList());
        return records.stream().filter(r -> !existing.contains(r.recordId())).toList();
    }

    private static String describe(Set<ConstraintViolation<CdrRequest>> violations) {
        return violations.stream()
                .map(v -> v.getPropertyPath() + " " + v.getMessage())
                .sorted()
                .collect(Collectors.joining("; "));
    }
}
