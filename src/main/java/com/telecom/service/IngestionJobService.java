package com.telecom.service;

import com.telecom.dto.IngestionJobResponse;
import com.telecom.dto.IngestionSummary;
import com.telecom.dto.PageResponse;
import com.telecom.entity.IngestionJob;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.repository.IngestionJobRepository;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Each method is its own short transaction, so status changes are visible immediately. */
@Service
public class IngestionJobService {

    private final IngestionJobRepository repository;

    public IngestionJobService(IngestionJobRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public IngestionJobResponse create(String source, String batchId, long receivedBytes) {
        return IngestionJobResponse.from(repository.save(new IngestionJob(source, batchId, receivedBytes)));
    }

    @Transactional
    public void markRunning(String id) {
        find(id).start();
    }

    @Transactional
    public void markCompleted(String id, IngestionSummary summary) {
        find(id).complete(summary);
    }

    @Transactional
    public void markFailed(String id, String message, IngestionSummary partial) {
        find(id).fail(message, partial);
    }

    @Transactional(readOnly = true)
    public IngestionJobResponse get(String id) {
        return IngestionJobResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public PageResponse<IngestionJobResponse> recent(Pageable pageable) {
        return PageResponse.from(repository.findAllByOrderByCreatedAtDesc(pageable).map(IngestionJobResponse::from));
    }

    private IngestionJob find(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingestion job not found: " + id));
    }
}
