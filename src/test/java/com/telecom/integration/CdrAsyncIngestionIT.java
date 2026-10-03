package com.telecom.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.support.TestCdrs;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** 202 Accepted + polling: spool to disk, process on the bounded executor, report via the job resource. */
class CdrAsyncIngestionIT extends AbstractIntegrationTest {

    private static final String ASYNC = "/api/v1/cdrs/async?source=MSC-05";

    private JsonNode awaitFinished(String statusUrl) {
        await().atMost(Duration.ofSeconds(30)).pollInterval(Duration.ofMillis(200)).until(() -> {
            String status = rest.getForObject(statusUrl, JsonNode.class).get("status").asText();
            return status.equals("COMPLETED") || status.equals("FAILED");
        });
        return rest.getForObject(statusUrl, JsonNode.class);
    }

    @Test
    void asyncIngestionCompletesAndCleansUpTheSpool() {
        byte[] body = TestCdrs.gzip(TestCdrs.bytes(TestCdrs.ndjson(500, "AS", "9876543210")));

        ResponseEntity<JsonNode> accepted = post(ASYNC + "&batchId=ASYNC-1", body, NDJSON, "gzip");

        assertThat(accepted.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        String location = accepted.getHeaders().getLocation().toString();
        assertThat(location).startsWith("/api/v1/ingestion-jobs/");
        assertThat(accepted.getBody().get("receivedBytes").asLong()).isGreaterThan(body.length);   // decompressed

        JsonNode job = awaitFinished(location);
        assertThat(job.get("status").asText()).isEqualTo("COMPLETED");
        assertThat(job.get("accepted").asLong()).isEqualTo(500);
        assertThat(cdrs.countByBatchId("ASYNC-1")).isEqualTo(500);
        await().atMost(Duration.ofSeconds(5)).until(() -> spoolFileCount() == 0);   // deleted right after completion
    }

    @Test
    void malformedAsyncPayloadFailsTheJobWithPartialCounts() {
        String body = TestCdrs.ndjson(70, "AM", "9876543210") + "{broken";

        ResponseEntity<JsonNode> accepted = post(ASYNC, TestCdrs.bytes(body), NDJSON, null);
        JsonNode job = awaitFinished(accepted.getHeaders().getLocation().toString());

        assertThat(job.get("status").asText()).isEqualTo("FAILED");
        assertThat(job.get("accepted").asLong()).isEqualTo(70);
        assertThat(job.get("errorMessage").asText()).contains("unreadable");
        await().atMost(Duration.ofSeconds(5)).until(() -> spoolFileCount() == 0);
    }

    @Test
    void asyncBodyOverLimitIsRejectedAndNothingIsSpooled() {
        byte[] tooBig = new byte[2 * 1024 * 1024 + 1];

        ResponseEntity<JsonNode> response = post(ASYNC, tooBig, NDJSON, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(jobs.count()).isZero();
        assertThat(spoolFileCount()).isZero();
    }

    @Test
    void recentJobsAreListedAndUnknownJobIs404() {
        ResponseEntity<JsonNode> accepted = post(ASYNC, TestCdrs.bytes(TestCdrs.ndjson(5, "AL", "9876543210")), NDJSON, null);
        awaitFinished(accepted.getHeaders().getLocation().toString());

        JsonNode page = rest.getForObject("/api/v1/ingestion-jobs", JsonNode.class);
        assertThat(page.get("totalElements").asLong()).isEqualTo(1);

        assertThat(rest.getForEntity("/api/v1/ingestion-jobs/does-not-exist", JsonNode.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
