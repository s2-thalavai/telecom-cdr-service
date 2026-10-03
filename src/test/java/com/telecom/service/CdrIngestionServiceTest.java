package com.telecom.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.telecom.config.JacksonConfig;
import com.telecom.config.PayloadProperties;
import com.telecom.dto.CdrBatchRequest;
import com.telecom.dto.CdrRequest;
import com.telecom.dto.IngestionSummary;
import com.telecom.dto.RecordError;
import com.telecom.entity.CallType;
import com.telecom.exception.MalformedPayloadException;
import com.telecom.exception.PayloadTooLargeException;
import com.telecom.exception.TooManyRecordsException;
import com.telecom.repository.CdrRepository;
import com.telecom.support.TestCdrs;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Real Jackson streaming + real Bean Validation; repository and transactions mocked.
 * Chunk size 3, max 20 records, max 2 reported errors.
 */
@ExtendWith(MockitoExtension.class)
class CdrIngestionServiceTest {

    private static final String MSISDN = "9876543210";
    private static final ValidatorFactory VALIDATORS = Validation.buildDefaultValidatorFactory();

    @Mock
    private CdrRepository repository;

    private CdrIngestionService service;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = JsonMapper.builder().addModule(new JavaTimeModule()).build();
        mapper.getFactory().setStreamReadConstraints(JacksonConfig.CONSTRAINTS);
        PayloadProperties properties = new PayloadProperties(DataSize.ofMegabytes(1), DataSize.ofMegabytes(10),
                DataSize.ofMegabytes(20), List.of("/stream"), 3, 20, 2, "unused", 1, 1);
        service = new CdrIngestionService(repository, VALIDATORS.getValidator(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)), mapper, properties);
    }

    @AfterAll
    static void closeValidators() {
        VALIDATORS.close();
    }

    // ------------------------------------------------------------ streaming

    @Test
    void ndjsonIsInsertedInChunks() throws IOException {
        IngestionSummary summary = stream(TestCdrs.ndjson(7, "N", MSISDN));

        assertThat(summary.received()).isEqualTo(7);
        assertThat(summary.accepted()).isEqualTo(7);
        assertThat(summary.rejected()).isZero();
        verify(repository, times(3)).batchInsert(anyList(), eq("MSC-01"), eq("B-1"));   // 3 + 3 + 1
    }

    @Test
    @SuppressWarnings("unchecked")
    void jsonArrayIsAcceptedWithTheSameCode() throws IOException {
        IngestionSummary summary = stream(TestCdrs.jsonArray(2, "A", MSISDN));

        ArgumentCaptor<List<CdrRequest>> inserted = ArgumentCaptor.forClass(List.class);
        verify(repository).batchInsert(inserted.capture(), eq("MSC-01"), eq("B-1"));
        assertThat(inserted.getValue()).extracting(CdrRequest::recordId).containsExactly("A-0", "A-1");
        assertThat(inserted.getValue().get(0).chargeAmount()).isEqualByComparingTo("1.5");
        assertThat(summary.accepted()).isEqualTo(2);
    }

    @Test
    void invalidRecordsAreRejectedWithIndexAndTheRestIngested() throws IOException {
        String body = TestCdrs.record(0, "V", MSISDN) + "\n"
                + TestCdrs.record(1, "V", "12345") + "\n"                                        // bad msisdn
                + TestCdrs.record(2, "V", MSISDN).replace("\"callType\":\"DATA\"", "\"callType\":\"FAX\"") + "\n"
                + TestCdrs.record(3, "V", MSISDN) + "\n";

        IngestionSummary summary = stream(body);

        assertThat(summary.received()).isEqualTo(4);
        assertThat(summary.accepted()).isEqualTo(2);
        assertThat(summary.rejected()).isEqualTo(2);
        assertThat(summary.errors()).extracting(RecordError::recordIndex).containsExactly(2L, 3L);
        assertThat(summary.errors().get(0).recordId()).isEqualTo("V-1");
        assertThat(summary.errors().get(0).message()).contains("msisdn");
        assertThat(summary.errors().get(1).recordId()).isNull();                // unreadable: no id
        assertThat(summary.errors().get(1).message()).startsWith("Unreadable record");
    }

    @Test
    void reportedErrorsAreCappedButAllRejectionsCounted() throws IOException {
        StringBuilder body = new StringBuilder();
        IntStream.range(0, 5).forEach(i -> body.append(TestCdrs.record(i, "E", "123")).append('\n'));

        IngestionSummary summary = stream(body.toString());

        assertThat(summary.rejected()).isEqualTo(5);
        assertThat(summary.errors()).hasSize(2);
        verify(repository, never()).batchInsert(anyList(), anyString(), anyString());
    }

    @Test
    void duplicatesWithinChunkAndInDatabaseAreSkipped() throws IOException {
        when(repository.findExistingRecordIds(anyCollection()))
                .thenAnswer(inv -> ((Collection<String>) inv.getArgument(0)).contains("D-1") ? Set.of("D-1") : Set.of());
        String body = TestCdrs.record(0, "D", MSISDN) + "\n"
                + TestCdrs.record(0, "D", MSISDN) + "\n"     // same id again in the same chunk
                + TestCdrs.record(1, "D", MSISDN) + "\n";    // already stored

        IngestionSummary summary = stream(body);

        assertThat(summary.accepted()).isEqualTo(1);
        assertThat(summary.duplicates()).isEqualTo(2);
    }

    @Test
    void concurrentDuplicateKeyIsRetriedOnceWithoutTheConflictingIds() throws IOException {
        when(repository.findExistingRecordIds(anyCollection())).thenReturn(Set.of(), Set.of("R-1"));
        when(repository.batchInsert(anyList(), anyString(), anyString()))
                .thenThrow(new DuplicateKeyException("uk_cdr_record_id"))
                .thenReturn(2);

        IngestionSummary summary = stream(TestCdrs.ndjson(3, "R", MSISDN));

        assertThat(summary.accepted()).isEqualTo(2);
        assertThat(summary.duplicates()).isEqualTo(1);
        verify(repository, times(2)).batchInsert(anyList(), anyString(), anyString());
    }

    @Test
    void malformedJsonKeepsEarlierRecordsAndReportsPartialResult() {
        String body = TestCdrs.record(0, "M", MSISDN) + "\n" + TestCdrs.record(1, "M", MSISDN) + "\n{broken";

        assertThatThrownBy(() -> stream(body))
                .isInstanceOfSatisfying(MalformedPayloadException.class, e -> {
                    assertThat(e.getPartialResult().accepted()).isEqualTo(2);
                    assertThat(e.getMessage()).contains("after record");
                });
        verify(repository).batchInsert(anyList(), eq("MSC-01"), eq("B-1"));   // the 2 good records were flushed
    }

    @Test
    void tooManyRecordsStopsTheFeed() {
        assertThatThrownBy(() -> stream(TestCdrs.ndjson(21, "T", MSISDN)))
                .isInstanceOf(TooManyRecordsException.class)
                .hasMessageContaining("More than 20 records");
        verify(repository, times(7)).batchInsert(anyList(), anyString(), anyString());   // 20 records / chunk 3
    }

    @Test
    void parserConstraintViolationIsMalformed() {
        String deep = "[".repeat(100) + "]".repeat(100);   // nesting > 64

        assertThatThrownBy(() -> stream(deep)).isInstanceOf(MalformedPayloadException.class);
    }

    @Test
    void sizeLimitFromTheFilterPropagatesUnchanged() {
        InputStream failing = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new PayloadTooLargeException("Request body exceeds the limit");
            }
        };

        assertThatThrownBy(() -> service.ingestStream(failing, "MSC-01", "B-1"))
                .isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void emptyBodyIngestsNothing() throws IOException {
        IngestionSummary summary = stream("");

        assertThat(summary.received()).isZero();
        verify(repository, never()).batchInsert(anyList(), anyString(), anyString());
    }

    // ------------------------------------------------------------ buffered batch

    @Test
    void batchIsPersistedInChunks() {
        List<CdrRequest> records = IntStream.range(0, 7)
                .mapToObj(i -> new CdrRequest("BB-" + i, MSISDN, null, CallType.SMS, TestCdrs.BASE, 0, 0, null,
                        BigDecimal.ONE))
                .toList();

        IngestionSummary summary = service.ingestBatch(new CdrBatchRequest("MSC-02", null, records), "B-9");

        assertThat(summary.accepted()).isEqualTo(7);
        assertThat(summary.source()).isEqualTo("MSC-02");
        verify(repository, times(3)).batchInsert(anyList(), eq("MSC-02"), eq("B-9"));
    }

    private IngestionSummary stream(String body) throws IOException {
        return service.ingestStream(new ByteArrayInputStream(TestCdrs.bytes(body)), "MSC-01", "B-1");
    }
}
