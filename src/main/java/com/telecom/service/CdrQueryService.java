package com.telecom.service;

import com.telecom.dto.CdrResponse;
import com.telecom.dto.PageResponse;
import com.telecom.dto.UsageSummary;
import com.telecom.exception.InvalidRequestException;
import com.telecom.repository.CdrRepository;
import com.telecom.repository.UsageTotals;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Service
@Transactional(readOnly = true)
public class CdrQueryService {

    static final Duration MAX_RANGE = Duration.ofDays(366);

    private final CdrRepository repository;

    public CdrQueryService(CdrRepository repository) {
        this.repository = repository;
    }

    public PageResponse<CdrResponse> find(String msisdn, Instant from, Instant to, Pageable pageable) {
        validateRange(from, to);
        return PageResponse.from(repository
                .findByMsisdnAndStartTimeGreaterThanEqualAndStartTimeLessThan(msisdn, from, to, pageable)
                .map(CdrResponse::from));
    }

    public UsageSummary usage(String msisdn, Instant from, Instant to) {
        validateRange(from, to);
        UsageTotals t = repository.summarizeUsage(msisdn, from, to);
        if (t == null) {
            return new UsageSummary(msisdn, from, to, 0, 0, 0, 0, BigDecimal.ZERO);
        }
        return new UsageSummary(msisdn, from, to,
                orZero(t.getVoiceCalls()), orZero(t.getVoiceSeconds()), orZero(t.getSmsCount()),
                orZero(t.getDataBytes()), t.getTotalCharge() == null ? BigDecimal.ZERO : t.getTotalCharge());
    }

    private static void validateRange(Instant from, Instant to) {
        if (!from.isBefore(to)) {
            throw new InvalidRequestException("'from' must be before 'to'");
        }
        if (Duration.between(from, to).compareTo(MAX_RANGE) > 0) {
            throw new InvalidRequestException("Range must not exceed 366 days");
        }
    }

    private static long orZero(Long value) {
        return value == null ? 0 : value;
    }
}
