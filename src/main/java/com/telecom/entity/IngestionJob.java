package com.telecom.entity;

import com.telecom.dto.IngestionSummary;
import jakarta.persistence.*;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "ingestion_jobs")
public class IngestionJob implements Persistable<String> {

    static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @Column(length = 36)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private JobStatus status;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "received_bytes", nullable = false)
    private long receivedBytes;

    @Column(name = "received_records", nullable = false)
    private long receivedRecords;

    @Column(nullable = false)
    private long accepted;

    @Column(nullable = false)
    private long duplicates;

    @Column(nullable = false)
    private long rejected;

    @Column(name = "error_message", length = MAX_ERROR_LENGTH)
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    /** Assigned UUID ids: tell Spring Data the entity is new, so save() inserts without a SELECT first. */
    @Transient
    private boolean isNew = true;

    protected IngestionJob() {
        // JPA
    }

    public IngestionJob(String source, String batchId, long receivedBytes) {
        this.id = UUID.randomUUID().toString();
        this.status = JobStatus.PENDING;
        this.source = source;
        this.batchId = batchId;
        this.receivedBytes = receivedBytes;
        this.createdAt = Instant.now();
    }

    public void start() {
        this.status = JobStatus.RUNNING;
        this.startedAt = Instant.now();
    }

    public void complete(IngestionSummary summary) {
        apply(summary);
        this.status = JobStatus.COMPLETED;
        this.completedAt = Instant.now();
    }

    public void fail(String message, IngestionSummary partial) {
        if (partial != null) {
            apply(partial);
        }
        this.status = JobStatus.FAILED;
        this.errorMessage = message == null ? null : message.substring(0, Math.min(message.length(), MAX_ERROR_LENGTH));
        this.completedAt = Instant.now();
    }

    private void apply(IngestionSummary s) {
        this.receivedRecords = s.received();
        this.accepted = s.accepted();
        this.duplicates = s.duplicates();
        this.rejected = s.rejected();
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }

    @Override
    public boolean isNew() { return isNew; }
    @Override
    public String getId() { return id; }
    public JobStatus getStatus() { return status; }
    public String getSource() { return source; }
    public String getBatchId() { return batchId; }
    public long getReceivedBytes() { return receivedBytes; }
    public long getReceivedRecords() { return receivedRecords; }
    public long getAccepted() { return accepted; }
    public long getDuplicates() { return duplicates; }
    public long getRejected() { return rejected; }
    public String getErrorMessage() { return errorMessage; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
