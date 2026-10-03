package com.telecom.controller;

import com.telecom.dto.CdrBatchRequest;
import com.telecom.dto.IngestionJobResponse;
import com.telecom.dto.IngestionSummary;
import com.telecom.service.AsyncIngestionService;
import com.telecom.service.CdrIngestionService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/cdrs")
public class CdrIngestionController {

    private final CdrIngestionService ingestion;
    private final AsyncIngestionService asyncIngestion;

    public CdrIngestionController(CdrIngestionService ingestion, AsyncIngestionService asyncIngestion) {
        this.ingestion = ingestion;
        this.asyncIngestion = asyncIngestion;
    }

    /** Buffered: whole JSON object bound + validated up front. Max 5,000 records / 10MB. All-or-nothing validation. */
    @PostMapping(value = "/batch", consumes = MediaType.APPLICATION_JSON_VALUE)
    public IngestionSummary ingestBatch(@Valid @RequestBody CdrBatchRequest request) {
        return ingestion.ingestBatch(request, batchIdOrNew(request.batchId()));
    }

    /** Streaming: JSON array or NDJSON, optionally gzip. Up to 500MB. Per-record validation. */
    @PostMapping(value = "/stream",
                 consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_NDJSON_VALUE})
    public IngestionSummary ingestStream(@RequestParam @NotBlank @Size(max = 50) String source,
                                         @RequestParam(required = false) @Size(max = 64) String batchId,
                                         InputStream body) throws IOException {
        return ingestion.ingestStream(body, source, batchIdOrNew(batchId));
    }

    /** Asynchronous: body spooled to disk, 202 Accepted, poll the job URL. */
    @PostMapping(value = "/async",
                 consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.APPLICATION_NDJSON_VALUE})
    public ResponseEntity<IngestionJobResponse> ingestAsync(@RequestParam @NotBlank @Size(max = 50) String source,
                                                            @RequestParam(required = false) @Size(max = 64) String batchId,
                                                            InputStream body) throws IOException {
        IngestionJobResponse job = asyncIngestion.submit(body, source, batchIdOrNew(batchId));
        return ResponseEntity.accepted().location(URI.create(job.statusUrl())).body(job);
    }

    static String batchIdOrNew(String batchId) {
        return StringUtils.hasText(batchId) ? batchId : "B-" + UUID.randomUUID();
    }
}
