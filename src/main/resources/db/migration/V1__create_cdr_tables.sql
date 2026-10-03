-- MySQL 8.x schema. Keep in sync with com.telecom.entity.*
CREATE TABLE call_detail_records (
    id                 BIGINT         NOT NULL AUTO_INCREMENT,
    record_id          VARCHAR(64)    NOT NULL,
    msisdn             VARCHAR(15)    NOT NULL,
    called_number      VARCHAR(20)    NULL,
    call_type          VARCHAR(10)    NOT NULL,
    start_time         DATETIME(6)    NOT NULL,
    duration_seconds   INT            NOT NULL,
    data_volume_bytes  BIGINT         NOT NULL,
    cell_id            VARCHAR(20)    NULL,
    charge_amount      DECIMAL(12, 4) NOT NULL,
    source             VARCHAR(50)    NOT NULL,
    batch_id           VARCHAR(64)    NOT NULL,
    ingested_at        DATETIME(6)    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_cdr_record_id UNIQUE (record_id),
    INDEX idx_cdr_msisdn_start (msisdn, start_time),
    INDEX idx_cdr_batch (batch_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE ingestion_jobs (
    id                VARCHAR(36)   NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    source            VARCHAR(50)   NOT NULL,
    batch_id          VARCHAR(64)   NOT NULL,
    received_bytes    BIGINT        NOT NULL,
    received_records  BIGINT        NOT NULL DEFAULT 0,
    accepted          BIGINT        NOT NULL DEFAULT 0,
    duplicates        BIGINT        NOT NULL DEFAULT 0,
    rejected          BIGINT        NOT NULL DEFAULT 0,
    error_message     VARCHAR(1000) NULL,
    created_at        DATETIME(6)   NOT NULL,
    started_at        DATETIME(6)   NULL,
    completed_at      DATETIME(6)   NULL,
    PRIMARY KEY (id),
    INDEX idx_jobs_status (status)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
