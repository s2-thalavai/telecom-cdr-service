package com.telecom.controller;

import com.telecom.dto.CdrBatchRequest;
import com.telecom.dto.IngestionJobResponse;
import com.telecom.dto.IngestionSummary;
import com.telecom.entity.JobStatus;
import com.telecom.exception.MalformedPayloadException;
import com.telecom.exception.PayloadTooLargeException;
import com.telecom.exception.ServiceBusyException;
import com.telecom.exception.TooManyRecordsException;
import com.telecom.service.AsyncIngestionService;
import com.telecom.service.CdrIngestionService;
import com.telecom.support.TestCdrs;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Web slice: binding, validation and error mapping. Filters are covered by their own unit tests. */
@WebMvcTest(CdrIngestionController.class)
class CdrIngestionControllerTest {

    private static final String MSISDN = "9876543210";
    private static final IngestionSummary SUMMARY = new IngestionSummary("B-1", "MSC-01", 3, 3, 0, 0, 5, List.of());

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CdrIngestionService ingestion;

    @MockitoBean
    private AsyncIngestionService asyncIngestion;

    // ------------------------------------------------------------ buffered batch

    @Test
    void validBatchIsBoundAndIngested() throws Exception {
        when(ingestion.ingestBatch(any(CdrBatchRequest.class), anyString())).thenReturn(SUMMARY);

        mvc.perform(post("/api/v1/cdrs/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(TestCdrs.batch("MSC-01", 3, "B", MSISDN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accepted").value(3));

        ArgumentCaptor<CdrBatchRequest> request = ArgumentCaptor.forClass(CdrBatchRequest.class);
        ArgumentCaptor<String> batchId = ArgumentCaptor.forClass(String.class);
        verify(ingestion).ingestBatch(request.capture(), batchId.capture());
        assertThat(request.getValue().records()).hasSize(3);
        assertThat(batchId.getValue()).startsWith("B-");                  // generated when absent
    }

    @Test
    void batchOverRecordLimitIs400() throws Exception {
        mvc.perform(post("/api/v1/cdrs/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(TestCdrs.batch("MSC-01", CdrBatchRequest.MAX_RECORDS + 1, "L", MSISDN)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.records").exists());
        verifyNoInteractions(ingestion);
    }

    @Test
    void batchValidationErrorsAreCapped() throws Exception {
        mvc.perform(post("/api/v1/cdrs/batch").contentType(MediaType.APPLICATION_JSON)
                        .content(TestCdrs.batch("MSC-01", 50, "X", "123")))      // 50 invalid msisdns
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.totalErrors").value(50))
                .andExpect(jsonPath("$.errors.*", hasSize(20)));
    }

    @Test
    void batchWithoutSourceIs400() throws Exception {
        mvc.perform(post("/api/v1/cdrs/batch").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":[" + TestCdrs.record(0, "S", MSISDN) + "]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.source").exists());
    }

    @Test
    void malformedBatchJsonIs400() throws Exception {
        mvc.perform(post("/api/v1/cdrs/batch").contentType(MediaType.APPLICATION_JSON).content("{\"source\":"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Malformed Body"));
    }

    // ------------------------------------------------------------ streaming

    @Test
    void streamPassesRawBodyAndParameters() throws Exception {
        byte[] body = TestCdrs.bytes(TestCdrs.ndjson(3, "S", MSISDN));
        when(ingestion.ingestStream(any(InputStream.class), eq("MSC-01"), eq("FEED-1"))).thenAnswer(inv -> {
            InputStream in = inv.getArgument(0);
            assertThat(in.readAllBytes()).isEqualTo(body);
            return SUMMARY;
        });

        mvc.perform(post("/api/v1/cdrs/stream").param("source", "MSC-01").param("batchId", "FEED-1")
                        .contentType(MediaType.APPLICATION_NDJSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(3));
    }

    @Test
    void streamRequiresSource() throws Exception {
        mvc.perform(post("/api/v1/cdrs/stream").contentType(MediaType.APPLICATION_NDJSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ingestion);
    }

    @Test
    void streamRejectsBlankSource() throws Exception {
        mvc.perform(post("/api/v1/cdrs/stream").param("source", " ")
                        .contentType(MediaType.APPLICATION_NDJSON).content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(ingestion);
    }

    @Test
    void streamRejectsUnsupportedContentType() throws Exception {
        mvc.perform(post("/api/v1/cdrs/stream").param("source", "MSC-01")
                        .contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    void payloadTooLargeIs413() throws Exception {
        when(ingestion.ingestStream(any(), anyString(), anyString()))
                .thenThrow(new PayloadTooLargeException("Request body exceeds the limit of 10 bytes"));

        mvc.perform(post("/api/v1/cdrs/stream").param("source", "MSC-01")
                        .contentType(MediaType.APPLICATION_NDJSON).content("{}"))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.title").value("Payload Too Large"));
    }

    @Test
    void tooManyRecordsIs413() throws Exception {
        when(ingestion.ingestStream(any(), anyString(), anyString()))
                .thenThrow(new TooManyRecordsException("More than 5 records"));

        mvc.perform(post("/api/v1/cdrs/stream").param("source", "MSC-01")
                        .contentType(MediaType.APPLICATION_NDJSON).content("{}"))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void malformedStreamIs400WithPartialResult() throws Exception {
        when(ingestion.ingestStream(any(), anyString(), anyString()))
                .thenThrow(new MalformedPayloadException("Payload became unreadable after record 3", SUMMARY));

        mvc.perform(post("/api/v1/cdrs/stream").param("source", "MSC-01")
                        .contentType(MediaType.APPLICATION_NDJSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.partialResult.accepted").value(3));
    }

    // ------------------------------------------------------------ async

    @Test
    void asyncReturns202WithJobLocation() throws Exception {
        when(asyncIngestion.submit(any(), eq("MSC-01"), anyString())).thenReturn(new IngestionJobResponse(
                "job-1", JobStatus.PENDING, "MSC-01", "B-1", 10, 0, 0, 0, 0, null, Instant.now(), null, null,
                "/api/v1/ingestion-jobs/job-1"));

        mvc.perform(post("/api/v1/cdrs/async").param("source", "MSC-01")
                        .contentType(MediaType.APPLICATION_NDJSON).content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/ingestion-jobs/job-1"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void asyncQueueFullIs503WithRetryAfter() throws Exception {
        when(asyncIngestion.submit(any(), anyString(), anyString()))
                .thenThrow(new ServiceBusyException("Ingestion queue is full; retry later"));

        mvc.perform(post("/api/v1/cdrs/async").param("source", "MSC-01")
                        .contentType(MediaType.APPLICATION_NDJSON).content("{}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "30"))
                .andExpect(jsonPath("$.detail", startsWith("Ingestion queue is full")));
    }
}
