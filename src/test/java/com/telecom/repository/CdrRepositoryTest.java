package com.telecom.repository;

import com.telecom.dto.CdrRequest;
import com.telecom.entity.CallDetailRecord;
import com.telecom.entity.CallType;
import com.telecom.support.TestCdrs;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** JPA slice on H2: the JDBC bulk fragment, the JPQL aggregate and the paged finder against a real database. */
@DataJpaTest
@ActiveProfiles("test")
class CdrRepositoryTest {

    private static final String MSISDN = "9876543210";

    @Autowired
    private CdrRepository repository;

    private static List<CdrRequest> records(int count, String prefix, String msisdn) {
        return IntStream.range(0, count).mapToObj(i -> {
            CallType type = CallType.values()[i % 3];
            return new CdrRequest(prefix + i, msisdn, type == CallType.DATA ? null : "9123400000", type,
                    TestCdrs.BASE.plusSeconds(i * 60L), type == CallType.VOICE ? 60 : 0,
                    type == CallType.DATA ? 1000 : 0, "CELL-1", new BigDecimal("1.5"));
        }).toList();
    }

    @Test
    void batchInsertWritesRowsReadableThroughJpa() {
        repository.batchInsert(records(6, "R", MSISDN), "MSC-01", "B-1");

        List<CallDetailRecord> rows = repository.findAll(Sort.by("recordId"));
        assertThat(rows).hasSize(6);
        assertThat(rows.get(0).getStartTime()).isEqualTo(TestCdrs.BASE);           // UTC round trip
        assertThat(rows.get(0).getChargeAmount()).isEqualByComparingTo("1.5");
        assertThat(rows.get(2).getCalledNumber()).isNull();                         // DATA record
        assertThat(repository.countByBatchId("B-1")).isEqualTo(6);
    }

    @Test
    void findsExistingRecordIds() {
        repository.batchInsert(records(3, "E", MSISDN), "MSC-01", "B-1");

        assertThat(repository.findExistingRecordIds(List.of("E0", "E2", "missing"))).containsExactlyInAnyOrder("E0", "E2");
        assertThat(repository.findExistingRecordIds(List.of())).isEmpty();
    }

    @Test
    void duplicateRecordIdIsTranslatedToDuplicateKeyException() {
        repository.batchInsert(records(1, "D", MSISDN), "MSC-01", "B-1");

        assertThatThrownBy(() -> repository.batchInsert(records(1, "D", MSISDN), "MSC-01", "B-2"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void summarizesUsageInTheDatabase() {
        repository.batchInsert(records(9, "U", MSISDN), "MSC-01", "B-1");
        repository.batchInsert(records(3, "OTHER", "9000000001"), "MSC-01", "B-1");   // another subscriber

        UsageTotals totals = repository.summarizeUsage(MSISDN, TestCdrs.BASE, TestCdrs.BASE.plusSeconds(3600));

        assertThat(totals.getVoiceCalls()).isEqualTo(3);
        assertThat(totals.getVoiceSeconds()).isEqualTo(180);
        assertThat(totals.getSmsCount()).isEqualTo(3);
        assertThat(totals.getDataBytes()).isEqualTo(3000);
        assertThat(totals.getTotalCharge()).isEqualByComparingTo("13.5");
    }

    @Test
    void usageForUnknownSubscriberIsAllNull() {
        UsageTotals totals = repository.summarizeUsage("9999999999", TestCdrs.BASE, TestCdrs.BASE.plusSeconds(60));

        assertThat(totals == null || totals.getVoiceCalls() == null).isTrue();
    }

    @Test
    void pagedFinderRespectsRangeAndOrder() {
        repository.batchInsert(records(10, "P", MSISDN), "MSC-01", "B-1");
        Instant from = TestCdrs.BASE.plusSeconds(120);          // excludes P0, P1
        Instant to = TestCdrs.BASE.plusSeconds(600);            // excludes P10.. (exclusive)

        Page<CallDetailRecord> page = repository.findByMsisdnAndStartTimeGreaterThanEqualAndStartTimeLessThan(
                MSISDN, from, to, PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "startTime")));

        assertThat(page.getTotalElements()).isEqualTo(8);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(CallDetailRecord::getRecordId).containsExactly("P9", "P8", "P7");
    }
}
