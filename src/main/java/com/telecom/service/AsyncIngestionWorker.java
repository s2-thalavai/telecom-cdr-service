package com.telecom.service;

import com.telecom.dto.IngestionSummary;
import com.telecom.exception.MalformedPayloadException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class AsyncIngestionWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncIngestionWorker.class);

    private final CdrIngestionService ingestion;
    private final IngestionJobService jobs;
    private final PayloadSpool spool;

    public AsyncIngestionWorker(CdrIngestionService ingestion, IngestionJobService jobs, PayloadSpool spool) {
        this.ingestion = ingestion;
        this.jobs = jobs;
        this.spool = spool;
    }

    @Async("ingestionExecutor")
    public void process(String jobId, Path payload, String source, String batchId) {
        jobs.markRunning(jobId);
        try (InputStream in = new BufferedInputStream(Files.newInputStream(payload), 64 * 1024)) {
            IngestionSummary summary = ingestion.ingestStream(in, source, batchId);
            jobs.markCompleted(jobId, summary);
        } catch (MalformedPayloadException e) {
            jobs.markFailed(jobId, e.getMessage(), e.getPartialResult());
        } catch (Exception e) {
            log.error("Ingestion job {} failed", jobId, e);
            jobs.markFailed(jobId, e.getMessage(), null);
        } finally {
            spool.delete(payload);
        }
    }
}
