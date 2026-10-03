package com.telecom.dto;

import com.telecom.entity.IngestionJob;
import com.telecom.entity.JobStatus;

import java.time.Instant;

public record IngestionJobResponse(String id, JobStatus status, String source, String batchId, long receivedBytes,
                                   long receivedRecords, long accepted, long duplicates, long rejected,
                                   String errorMessage, Instant createdAt, Instant startedAt, Instant completedAt,
                                   String statusUrl) {

    public static IngestionJobResponse from(IngestionJob j) {
        return new IngestionJobResponse(j.getId(), j.getStatus(), j.getSource(), j.getBatchId(),
                j.getReceivedBytes(), j.getReceivedRecords(), j.getAccepted(), j.getDuplicates(), j.getRejected(),
                j.getErrorMessage(), j.getCreatedAt(), j.getStartedAt(), j.getCompletedAt(),
                "/api/v1/ingestion-jobs/" + j.getId());
    }
}
