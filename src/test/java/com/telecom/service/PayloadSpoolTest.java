package com.telecom.service;

import com.telecom.config.PayloadProperties;
import com.telecom.dto.SpooledPayload;
import com.telecom.exception.PayloadTooLargeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PayloadSpoolTest {

    @TempDir
    Path dir;

    private PayloadSpool spool() {
        return new PayloadSpool(new PayloadProperties(DataSize.ofKilobytes(1), DataSize.ofKilobytes(1),
                DataSize.ofKilobytes(1), List.of(), 1, 1, 1, dir.resolve("spool").toString(), 1, 1));
    }

    @Test
    void writesBodyToDiskAndDeletes() throws IOException {
        PayloadSpool spool = spool();

        SpooledPayload payload = spool.spool(new ByteArrayInputStream(new byte[300]));

        assertThat(payload.sizeBytes()).isEqualTo(300);
        assertThat(Files.size(payload.file())).isEqualTo(300);
        spool.delete(payload.file());
        assertThat(payload.file()).doesNotExist();
    }

    @Test
    void failedWriteLeavesNoFileBehind() throws IOException {
        PayloadSpool spool = spool();
        InputStream failing = new InputStream() {
            private int n;

            @Override
            public int read() throws IOException {
                if (n++ > 100) {
                    throw new PayloadTooLargeException("too big");
                }
                return 'x';
            }
        };

        assertThatThrownBy(() -> spool.spool(failing)).isInstanceOf(PayloadTooLargeException.class);
        try (Stream<Path> files = Files.list(spool.directory())) {
            assertThat(files).isEmpty();
        }
    }
}
