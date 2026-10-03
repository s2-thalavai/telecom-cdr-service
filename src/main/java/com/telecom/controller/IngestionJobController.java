package com.telecom.controller;

import com.telecom.dto.IngestionJobResponse;
import com.telecom.dto.PageResponse;
import com.telecom.service.IngestionJobService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/ingestion-jobs")
public class IngestionJobController {

    private final IngestionJobService service;

    public IngestionJobController(IngestionJobService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<IngestionJobResponse> recent(@PageableDefault(size = 20) Pageable pageable) {
        return service.recent(pageable);
    }

    @GetMapping("/{id}")
    public IngestionJobResponse get(@PathVariable String id) {
        return service.get(id);
    }
}
