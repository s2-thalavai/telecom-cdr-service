package com.telecom.service;

import com.telecom.dto.IngestionJobResponse;
import com.telecom.dto.SpooledPayload;
import com.telecom.entity.JobStatus;
import com.telecom.exception.PayloadTooLargeException;
import com.telecom.exception.ServiceBusyException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.TaskRejectedException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AsyncIngestionServiceTest {

    private static final Path FILE = Path.of("spool/cdr-1.json");

    @Mock
    private PayloadSpool spool;
    @Mock
    private IngestionJobService jobs;
    @Mock
    private AsyncIngestionWorker worker;

    @InjectMocks
    private AsyncIngestionService service;

    private final InputStream body = new ByteArrayInputStream(new byte[]{'{', '}'});

    @Test
    void spoolsCreatesJobAndHandsOverToWorker() throws Exception {
        when(spool.spool(body)).thenReturn(new SpooledPayload(FILE, 2));
        when(jobs.create("MSC-01", "B-1", 2)).thenReturn(job("j1"));

        IngestionJobResponse job = service.submit(body, "MSC-01", "B-1");

        assertThat(job.id()).isEqualTo("j1");
        verify(worker).process("j1", FILE, "MSC-01", "B-1");
    }

    @Test
    void fullQueueMarksJobFailedDeletesSpoolAndSignalsBusy() throws Exception {
        when(spool.spool(body)).thenReturn(new SpooledPayload(FILE, 2));
        when(jobs.create("MSC-01", "B-1", 2)).thenReturn(job("j2"));
        doThrow(new TaskRejectedException("queue full")).when(worker).process("j2", FILE, "MSC-01", "B-1");

        assertThatThrownBy(() -> service.submit(body, "MSC-01", "B-1")).isInstanceOf(ServiceBusyException.class);

        verify(jobs).markFailed(any(), any(), isNull());
        verify(spool).delete(FILE);
    }

    @Test
    void spoolFailureCreatesNoJob() throws Exception {
        when(spool.spool(body)).thenThrow(new PayloadTooLargeException("too big"));

        assertThatThrownBy(() -> service.submit(body, "MSC-01", "B-1")).isInstanceOf(PayloadTooLargeException.class);
        verifyNoInteractions(jobs, worker);
    }

    @Test
    void jobCreationFailureDeletesSpooledFile() throws Exception {
        when(spool.spool(body)).thenReturn(new SpooledPayload(FILE, 2));
        when(jobs.create("MSC-01", "B-1", 2)).thenThrow(new IllegalStateException("db down"));

        assertThatThrownBy(() -> service.submit(body, "MSC-01", "B-1")).isInstanceOf(IllegalStateException.class);
        verify(spool).delete(FILE);
        verifyNoInteractions(worker);
    }

    private static IngestionJobResponse job(String id) {
        return new IngestionJobResponse(id, JobStatus.PENDING, "MSC-01", "B-1", 2, 0, 0, 0, 0, null,
                Instant.now(), null, null, "/api/v1/ingestion-jobs/" + id);
    }
}
