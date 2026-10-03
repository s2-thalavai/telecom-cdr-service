package com.telecom.entity;

import com.telecom.dto.IngestionSummary;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IngestionJobTest {

    private static final IngestionSummary SUMMARY = new IngestionSummary("B", "S", 10, 7, 2, 1, 5, List.of());

    @Test
    void lifecycleCompletes() {
        IngestionJob job = new IngestionJob("MSC-01", "B-1", 1234);
        assertThat(job.getStatus()).isEqualTo(JobStatus.PENDING);
        assertThat(job.isNew()).isTrue();
        assertThat(job.getId()).hasSize(36);

        job.start();
        assertThat(job.getStatus()).isEqualTo(JobStatus.RUNNING);
        assertThat(job.getStartedAt()).isNotNull();

        job.complete(SUMMARY);
        assertThat(job.getStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getAccepted()).isEqualTo(7);
        assertThat(job.getDuplicates()).isEqualTo(2);
        assertThat(job.getRejected()).isEqualTo(1);
        assertThat(job.getReceivedRecords()).isEqualTo(10);
        assertThat(job.getCompletedAt()).isNotNull();
    }

    @Test
    void failureKeepsPartialCountsAndTruncatesMessage() {
        IngestionJob job = new IngestionJob("MSC-01", "B-1", 1);

        job.fail("x".repeat(5000), SUMMARY);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).hasSize(IngestionJob.MAX_ERROR_LENGTH);
        assertThat(job.getAccepted()).isEqualTo(7);
    }

    @Test
    void failureWithoutPartialResult() {
        IngestionJob job = new IngestionJob("MSC-01", "B-1", 1);

        job.fail(null, null);

        assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage()).isNull();
        assertThat(job.getAccepted()).isZero();
    }
}
