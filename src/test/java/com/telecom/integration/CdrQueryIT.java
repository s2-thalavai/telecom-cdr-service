package com.telecom.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.support.TestCdrs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Aggregates and paging over data ingested through the real API. */
class CdrQueryIT extends AbstractIntegrationTest {

    private static final String SUBSCRIBER = "9876543210";
    private static final String OTHER = "9000000001";
    private static final Instant FROM = TestCdrs.BASE;
    private static final Instant TO = TestCdrs.BASE.plus(1, ChronoUnit.DAYS);

    @BeforeEach
    void ingest() {
        post("/api/v1/cdrs/stream?source=MSC-01", TestCdrs.bytes(TestCdrs.ndjson(30, "Q", SUBSCRIBER)), NDJSON, null);
        post("/api/v1/cdrs/stream?source=MSC-01", TestCdrs.bytes(TestCdrs.ndjson(9, "O", OTHER)), NDJSON, null);
    }

    @Test
    void usageIsAggregatedPerSubscriber() {
        JsonNode usage = rest.getForObject("/api/v1/subscribers/{m}/usage?from={f}&to={t}", JsonNode.class,
                SUBSCRIBER, FROM, TO);

        assertThat(usage.get("voiceCalls").asLong()).isEqualTo(10);
        assertThat(usage.get("voiceSeconds").asLong()).isEqualTo(600);
        assertThat(usage.get("smsCount").asLong()).isEqualTo(10);
        assertThat(usage.get("dataBytes").asLong()).isEqualTo(10_000);
        assertThat(usage.get("totalCharge").decimalValue()).isEqualByComparingTo("45");
    }

    @Test
    void usageOutsideTheRangeIsZero() {
        JsonNode usage = rest.getForObject("/api/v1/subscribers/{m}/usage?from={f}&to={t}", JsonNode.class,
                SUBSCRIBER, TO, TO.plus(1, ChronoUnit.DAYS));

        assertThat(usage.get("voiceCalls").asLong()).isZero();
        assertThat(usage.get("totalCharge").decimalValue()).isEqualByComparingTo("0");
    }

    @Test
    void cdrListingIsPagedNewestFirst() {
        JsonNode page = rest.getForObject("/api/v1/cdrs?msisdn={m}&from={f}&to={t}&size=7", JsonNode.class,
                SUBSCRIBER, FROM, TO);

        assertThat(page.get("totalElements").asLong()).isEqualTo(30);
        assertThat(page.get("totalPages").asInt()).isEqualTo(5);
        assertThat(page.get("content")).hasSize(7);
        assertThat(page.get("content").get(0).get("recordId").asText()).isEqualTo("Q-29");
    }

    @Test
    void invalidRangeIs400() {
        ResponseEntity<JsonNode> response = rest.getForEntity("/api/v1/subscribers/{m}/usage?from={f}&to={t}",
                JsonNode.class, SUBSCRIBER, TO, FROM);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
