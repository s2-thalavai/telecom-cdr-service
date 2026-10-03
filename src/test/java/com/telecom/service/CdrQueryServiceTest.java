package com.telecom.service;

import com.telecom.dto.UsageSummary;
import com.telecom.exception.InvalidRequestException;
import com.telecom.repository.CdrRepository;
import com.telecom.repository.UsageTotals;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CdrQueryServiceTest {

    private static final Instant FROM = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-01T00:00:00Z");

    @Mock
    private CdrRepository repository;

    @InjectMocks
    private CdrQueryService service;

    @Test
    void mapsTotals() {
        when(repository.summarizeUsage("9876543210", FROM, TO)).thenReturn(totals(4L, 240L, 3L, 5000L, new BigDecimal("12.5")));

        UsageSummary usage = service.usage("9876543210", FROM, TO);

        assertThat(usage.voiceCalls()).isEqualTo(4);
        assertThat(usage.voiceSeconds()).isEqualTo(240);
        assertThat(usage.smsCount()).isEqualTo(3);
        assertThat(usage.dataBytes()).isEqualTo(5000);
        assertThat(usage.totalCharge()).isEqualByComparingTo("12.5");
    }

    @Test
    void noRowsMeansZeros() {
        when(repository.summarizeUsage("9876543210", FROM, TO)).thenReturn(totals(null, null, null, null, null));

        UsageSummary usage = service.usage("9876543210", FROM, TO);

        assertThat(usage.voiceCalls()).isZero();
        assertThat(usage.totalCharge()).isEqualByComparingTo("0");
    }

    @Test
    void rejectsReversedRange() {
        assertThatThrownBy(() -> service.usage("9876543210", TO, FROM)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> service.find("9876543210", FROM, FROM, Pageable.unpaged()))
                .isInstanceOf(InvalidRequestException.class);
        verifyNoInteractions(repository);
    }

    @Test
    void rejectsRangeLongerThanAYear() {
        assertThatThrownBy(() -> service.usage("9876543210", FROM, FROM.plus(Duration.ofDays(367))))
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("366 days");
    }

    private static UsageTotals totals(Long voice, Long seconds, Long sms, Long data, BigDecimal charge) {
        return new UsageTotals() {
            public Long getVoiceCalls() { return voice; }
            public Long getVoiceSeconds() { return seconds; }
            public Long getSmsCount() { return sms; }
            public Long getDataBytes() { return data; }
            public BigDecimal getTotalCharge() { return charge; }
        };
    }
}
