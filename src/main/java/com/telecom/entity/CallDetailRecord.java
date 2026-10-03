package com.telecom.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Read through JPA; written in bulk through JDBC (CdrBulkRepositoryImpl) for speed. */
@Entity
@Table(name = "call_detail_records",
       uniqueConstraints = @UniqueConstraint(name = "uk_cdr_record_id", columnNames = "record_id"),
       indexes = {
               @Index(name = "idx_cdr_msisdn_start", columnList = "msisdn, start_time"),
               @Index(name = "idx_cdr_batch", columnList = "batch_id")
       })
public class CallDetailRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Network element's unique id for the record: makes ingestion idempotent. */
    @Column(name = "record_id", nullable = false, length = 64)
    private String recordId;

    @Column(nullable = false, length = 15)
    private String msisdn;

    @Column(name = "called_number", length = 20)
    private String calledNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "call_type", nullable = false, length = 10)
    private CallType callType;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "duration_seconds", nullable = false)
    private int durationSeconds;

    @Column(name = "data_volume_bytes", nullable = false)
    private long dataVolumeBytes;

    @Column(name = "cell_id", length = 20)
    private String cellId;

    @Column(name = "charge_amount", nullable = false, precision = 12, scale = 4)
    private BigDecimal chargeAmount;

    @Column(nullable = false, length = 50)
    private String source;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "ingested_at", nullable = false)
    private Instant ingestedAt;

    protected CallDetailRecord() {
        // JPA
    }

    public Long getId() { return id; }
    public String getRecordId() { return recordId; }
    public String getMsisdn() { return msisdn; }
    public String getCalledNumber() { return calledNumber; }
    public CallType getCallType() { return callType; }
    public Instant getStartTime() { return startTime; }
    public int getDurationSeconds() { return durationSeconds; }
    public long getDataVolumeBytes() { return dataVolumeBytes; }
    public String getCellId() { return cellId; }
    public BigDecimal getChargeAmount() { return chargeAmount; }
    public String getSource() { return source; }
    public String getBatchId() { return batchId; }
    public Instant getIngestedAt() { return ingestedAt; }
}
