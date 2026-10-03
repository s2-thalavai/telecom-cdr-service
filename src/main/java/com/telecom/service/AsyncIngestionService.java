package com.telecom.service;

import com.telecom.dto.IngestionJobResponse;
import com.telecom.dto.SpooledPayload;
import com.telecom.exception.ServiceBusyException;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;

/**
 * Deliberately NOT @Transactional: the job row must be committed before the worker
 * (another thread) tries to load it.
 */
@Service
public class AsyncIngestionService {

    private final PayloadSpool spool;
    private final IngestionJobService jobs;
    private final AsyncIngestionWorker worker;

    public AsyncIngestionService(PayloadSpool spool, IngestionJobService jobs, AsyncIngestionWorker worker) {
        this.spool = spool;
        this.jobs = jobs;
        this.worker = worker;
    }

    public IngestionJobResponse submit(InputStream body, String source, String batchId) throws IOException {
        SpooledPayload spooled = spool.spool(body);
        IngestionJobResponse job;
        try {
            job = jobs.create(source, batchId, spooled.sizeBytes());
        } catch (RuntimeException e) {
            spool.delete(spooled.file());
            throw e;
        }
        try {
            worker.process(job.id(), spooled.file(), source, batchId);
        } catch (TaskRejectedException e) {
            jobs.markFailed(job.id(), "Rejected: ingestion queue is full", null);
            spool.delete(spooled.file());
            throw new ServiceBusyException("Ingestion queue is full; retry later");
        }
        return job;
    }
}
