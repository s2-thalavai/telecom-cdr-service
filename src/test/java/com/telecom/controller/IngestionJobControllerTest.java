package com.telecom.controller;

import com.telecom.dto.IngestionJobResponse;
import com.telecom.entity.JobStatus;
import com.telecom.exception.ResourceNotFoundException;
import com.telecom.service.IngestionJobService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IngestionJobController.class)
class IngestionJobControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private IngestionJobService service;

    @Test
    void returnsJob() throws Exception {
        when(service.get("j1")).thenReturn(new IngestionJobResponse("j1", JobStatus.COMPLETED, "MSC-01", "B-1",
                100, 10, 9, 1, 0, null, Instant.now(), Instant.now(), Instant.now(), "/api/v1/ingestion-jobs/j1"));

        mvc.perform(get("/api/v1/ingestion-jobs/j1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.accepted").value(9));
    }

    @Test
    void unknownJobIs404() throws Exception {
        when(service.get("nope")).thenThrow(new ResourceNotFoundException("Ingestion job not found: nope"));

        mvc.perform(get("/api/v1/ingestion-jobs/nope"))
                .andExpect(status().isNotFound());
    }
}
