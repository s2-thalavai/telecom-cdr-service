package com.telecom.controller;

import com.telecom.dto.CdrResponse;
import com.telecom.dto.PageResponse;
import com.telecom.dto.UsageSummary;
import com.telecom.service.CdrQueryService;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1")
public class CdrQueryController {

    private static final String MSISDN = "^[6-9]\\d{9}$";

    private final CdrQueryService service;

    public CdrQueryController(CdrQueryService service) {
        this.service = service;
    }

    /** Large result sets are paged; size is capped by spring.data.web.pageable.max-page-size. */
    @GetMapping("/cdrs")
    public PageResponse<CdrResponse> find(
            @RequestParam @Pattern(regexp = MSISDN) String msisdn,
            @RequestParam Instant from,
            @RequestParam Instant to,
            @PageableDefault(size = 50, sort = "startTime", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.find(msisdn, from, to, pageable);
    }

    @GetMapping("/subscribers/{msisdn}/usage")
    public UsageSummary usage(@PathVariable @Pattern(regexp = MSISDN) String msisdn,
                              @RequestParam Instant from,
                              @RequestParam Instant to) {
        return service.usage(msisdn, from, to);
    }
}
