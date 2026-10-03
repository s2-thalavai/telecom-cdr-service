package com.telecom.service;

import com.telecom.dto.IngestionSummary;
import com.telecom.dto.RecordError;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/** Mutable per-request counters. Error details are capped so memory stays bounded. */
final class IngestionStats {

    private final int maxErrors;
    private final long startedNanos = System.nanoTime();
    private final List<RecordError> errors = new ArrayList<>();
    private long received;
    private long accepted;
    private long duplicates;
    private long rejected;

    IngestionStats(int maxErrors) {
        this.maxErrors = maxErrors;
    }

    long received() {
        return received;
    }

    void received(long n) {
        received += n;
    }

    void accepted(long n) {
        accepted += n;
    }

    void duplicates(long n) {
        duplicates += n;
    }

    void rejected(long recordIndex, String recordId, String message) {
        rejected++;
        if (errors.size() < maxErrors) {
            errors.add(new RecordError(recordIndex, recordId, message));
        }
    }

    IngestionSummary toSummary(String batchId, String source) {
        return new IngestionSummary(batchId, source, received, accepted, duplicates, rejected,
                Duration.ofNanos(System.nanoTime() - startedNanos).toMillis(), List.copyOf(errors));
    }
}
