package com.telecom.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.support.TestCdrs;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;

/** Large-payload behaviour of /cdrs/stream and /cdrs/batch over real HTTP with the real filters. */
class CdrStreamIngestionIT extends AbstractIntegrationTest {

    private static final String STREAM = "/api/v1/cdrs/stream?source=MSC-01";
    private static final String MSISDN = "9876543210";

    @Test
    void ndjsonIsIngestedAndResendingIsIdempotent() {
        byte[] body = TestCdrs.bytes(TestCdrs.ndjson(1000, "N", MSISDN));

        ResponseEntity<JsonNode> first = post(STREAM + "&batchId=FEED-1", body, NDJSON, null);
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("accepted").asLong()).isEqualTo(1000);
        assertThat(first.getBody().get("batchId").asText()).isEqualTo("FEED-1");
        assertThat(cdrs.count()).isEqualTo(1000);

        ResponseEntity<JsonNode> again = post(STREAM, body, NDJSON, null);
        assertThat(again.getBody().get("accepted").asLong()).isZero();
        assertThat(again.getBody().get("duplicates").asLong()).isEqualTo(1000);
        assertThat(cdrs.count()).isEqualTo(1000);
    }

    @Test
    void gzipBodyIsDecompressedTransparently() {
        byte[] plain = TestCdrs.bytes(TestCdrs.ndjson(1000, "G", MSISDN));
        byte[] gzipped = TestCdrs.gzip(plain);
        assertThat(gzipped.length).isLessThan(plain.length / 5);

        ResponseEntity<JsonNode> response = post(STREAM, gzipped, NDJSON, "gzip");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accepted").asLong()).isEqualTo(1000);
    }

    @Test
    void jsonArrayIsAcceptedOnTheStreamingEndpoint() {
        ResponseEntity<JsonNode> response = post(STREAM,
                TestCdrs.bytes(TestCdrs.jsonArray(120, "A", MSISDN)), MediaType.APPLICATION_JSON, null);

        assertThat(response.getBody().get("accepted").asLong()).isEqualTo(120);
    }

    @Test
    void invalidRecordsAreReportedAndSkipped() {
        String body = TestCdrs.ndjson(10, "V", MSISDN)
                + TestCdrs.record(101, "V", "12345") + "\n"                                   // bad msisdn
                + TestCdrs.record(100, "V", MSISDN).replace("\"SMS\"", "\"FAX\"") + "\n";  // 100 % 3 == SMS -> unknown enum

        JsonNode summary = post(STREAM, TestCdrs.bytes(body), NDJSON, null).getBody();

        assertThat(summary.get("received").asLong()).isEqualTo(12);
        assertThat(summary.get("accepted").asLong()).isEqualTo(10);
        assertThat(summary.get("rejected").asLong()).isEqualTo(2);
        assertThat(summary.get("errors").get(0).get("recordIndex").asLong()).isEqualTo(11);
        assertThat(cdrs.count()).isEqualTo(10);
    }

    @Test
    void declaredBodyOverStreamingLimitIsRejectedBeforeReading() {
        byte[] body = paddedBody(8, 300_000);                       // ~2.4MB > 2MB
        assertThat(body.length).isGreaterThan(2 * 1024 * 1024);

        ResponseEntity<JsonNode> response = post(STREAM, body, NDJSON, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Payload Too Large");
        assertThat(cdrs.count()).isZero();
    }

    @Test
    void chunkedBodyOverStreamingLimitIsStoppedMidStream() {
        byte[] body = paddedBody(8, 300_000);
        // InputStreamResource has no known length -> chunked transfer, no Content-Length header
        InputStreamResource chunked = new InputStreamResource(new ByteArrayInputStream(body));

        ResponseEntity<JsonNode> response = post(STREAM, chunked, NDJSON, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(cdrs.count()).isZero();
    }

    @Test
    void gzipBombIsStoppedAtTheDecompressedLimit() {
        byte[] bomb = TestCdrs.gzip(paddedBody(1, 5_000_000));      // tiny on the wire, 5MB expanded > 4MB
        assertThat(bomb.length).isLessThan(64 * 1024);

        ResponseEntity<JsonNode> response = post(STREAM, bomb, NDJSON, "gzip");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("detail").asText()).contains("Decompressed request body");
    }

    @Test
    void unsupportedContentEncodingIs415() {
        ResponseEntity<JsonNode> response = post(STREAM, new byte[]{1, 2, 3}, NDJSON, "br");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
    }

    @Test
    void bodyThatIsNotReallyGzipIs400() {
        ResponseEntity<JsonNode> response = post(STREAM,
                TestCdrs.bytes(TestCdrs.ndjson(2, "Z", MSISDN)), NDJSON, "gzip");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Invalid Compressed Body");
    }

    @Test
    void malformedStreamKeepsEarlierRecordsAndReportsThem() {
        String body = TestCdrs.ndjson(60, "M", MSISDN) + "{broken";

        ResponseEntity<JsonNode> response = post(STREAM, TestCdrs.bytes(body), NDJSON, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().get("partialResult").get("accepted").asLong()).isEqualTo(60);
        assertThat(cdrs.count()).isEqualTo(60);
    }

    @Test
    void tooManyRecordsIs413() {
        // 5,001 tiny-but-valid records: under the 2MB byte limit, over the 5,000 record limit
        ResponseEntity<JsonNode> response = post(STREAM,
                TestCdrs.gzip(TestCdrs.bytes(TestCdrs.ndjson(5001, "T", MSISDN))), NDJSON, "gzip");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody().get("title").asText()).isEqualTo("Too Many Records");
        assertThat(cdrs.count()).isEqualTo(5000);                  // everything up to the limit was kept
    }

    // ------------------------------------------------------------ buffered batch endpoint

    @Test
    void smallBatchIsAccepted() {
        ResponseEntity<JsonNode> response = post("/api/v1/cdrs/batch",
                TestCdrs.bytes(TestCdrs.batch("MSC-02", 100, "B", MSISDN)), MediaType.APPLICATION_JSON, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accepted").asLong()).isEqualTo(100);
    }

    @Test
    void batchOverDefaultLimitIsRejectedByTheFilter() {
        byte[] body = TestCdrs.bytes(TestCdrs.batch("MSC-02", 400, "B", MSISDN));   // ~95KB > 64KB
        assertThat(body.length).isGreaterThan(64 * 1024);

        ResponseEntity<JsonNode> response = post("/api/v1/cdrs/batch", body, MediaType.APPLICATION_JSON, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(cdrs.count()).isZero();
    }

    @Test
    void gzipBatchIsDecompressedAndBoundAsJson() {
        byte[] body = TestCdrs.gzip(TestCdrs.bytes(TestCdrs.batch("MSC-02", 200, "BZ", MSISDN)));

        ResponseEntity<JsonNode> response = post("/api/v1/cdrs/batch", body, MediaType.APPLICATION_JSON, "gzip");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().get("accepted").asLong()).isEqualTo(200);
    }

    /** A few valid records separated by large runs of whitespace: many bytes, few records. */
    private static byte[] paddedBody(int records, int paddingPerRecord) {
        StringBuilder sb = new StringBuilder();
        String padding = " ".repeat(paddingPerRecord);
        for (int i = 0; i < records; i++) {
            sb.append(TestCdrs.record(i, "P", MSISDN)).append('\n').append(padding);
        }
        return TestCdrs.bytes(sb.toString());
    }
}
