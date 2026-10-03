package com.telecom.filter;

import com.telecom.exception.PayloadTooLargeException;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedServletInputStreamTest {

    @Test
    void readsEverythingUnderTheLimit() throws IOException {
        BoundedServletInputStream in = new BoundedServletInputStream(new ByteArrayInputStream(new byte[100]), 100, "body");

        assertThat(in.readAllBytes()).hasSize(100);
        assertThat(in.bytesRead()).isEqualTo(100);
        assertThat(in.isFinished()).isTrue();
    }

    @Test
    void bulkReadFailsOnceLimitIsCrossed() {
        BoundedServletInputStream in = new BoundedServletInputStream(new ByteArrayInputStream(new byte[101]), 100, "body");

        assertThatThrownBy(in::readAllBytes)
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("body exceeds the limit of 100 bytes");
    }

    @Test
    void singleByteReadsAreCountedToo() throws IOException {
        BoundedServletInputStream in = new BoundedServletInputStream(new ByteArrayInputStream(new byte[3]), 2, "body");
        in.read();
        in.read();

        assertThatThrownBy(in::read).isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void nonBlockingReadsAreNotSupported() {
        BoundedServletInputStream in = new BoundedServletInputStream(new ByteArrayInputStream(new byte[0]), 1, "body");

        assertThat(in.isReady()).isTrue();
        assertThatThrownBy(() -> in.setReadListener(null)).isInstanceOf(UnsupportedOperationException.class);
    }
}
