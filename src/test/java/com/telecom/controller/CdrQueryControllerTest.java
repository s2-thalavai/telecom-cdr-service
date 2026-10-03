package com.telecom.controller;

import com.telecom.dto.PageResponse;
import com.telecom.dto.UsageSummary;
import com.telecom.exception.InvalidRequestException;
import com.telecom.service.CdrQueryService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CdrQueryController.class)
class CdrQueryControllerTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CdrQueryService service;

    @Test
    void usageBindsInstantsAndReturnsSummary() throws Exception {
        when(service.usage("9876543210", FROM, TO)).thenReturn(
                new UsageSummary("9876543210", FROM, TO, 4, 240, 3, 5000, new BigDecimal("12.5")));

        mvc.perform(get("/api/v1/subscribers/9876543210/usage")
                        .param("from", "2026-09-01T00:00:00Z").param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.voiceSeconds").value(240))
                .andExpect(jsonPath("$.totalCharge").value(12.5));
    }

    @Test
    void invalidMsisdnIs400() throws Exception {
        mvc.perform(get("/api/v1/subscribers/12345/usage")
                        .param("from", "2026-09-01T00:00:00Z").param("to", "2026-10-01T00:00:00Z"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void badRangeFromServiceIs400() throws Exception {
        when(service.usage(any(), any(), any())).thenThrow(new InvalidRequestException("'from' must be before 'to'"));

        mvc.perform(get("/api/v1/subscribers/9876543210/usage")
                        .param("from", "2026-10-01T00:00:00Z").param("to", "2026-09-01T00:00:00Z"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("'from' must be before 'to'"));
    }

    @Test
    void pageSizeIsCappedAndDefaultSortApplied() throws Exception {
        when(service.find(eq("9876543210"), eq(FROM), eq(TO), any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 500, 0, 0));

        mvc.perform(get("/api/v1/cdrs").param("msisdn", "9876543210")
                        .param("from", "2026-09-01T00:00:00Z").param("to", "2026-10-01T00:00:00Z")
                        .param("size", "100000"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(service).find(eq("9876543210"), eq(FROM), eq(TO), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(500);   // spring.data.web.pageable.max-page-size
        assertThat(pageable.getValue().getSort().getOrderFor("startTime").getDirection())
                .isEqualTo(Sort.Direction.DESC);
    }
}
