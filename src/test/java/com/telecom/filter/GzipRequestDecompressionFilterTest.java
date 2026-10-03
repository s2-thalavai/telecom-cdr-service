package com.telecom.filter;

import com.telecom.exception.PayloadTooLargeException;
import com.telecom.support.TestCdrs;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GzipRequestDecompressionFilterTest {

    private final GzipRequestDecompressionFilter filter = new GzipRequestDecompressionFilter(10_000);

    @Test
    void decompressesGzipBodyAndHidesEncodingHeaders() throws Exception {
        byte[] plain = TestCdrs.bytes(TestCdrs.ndjson(20, "G", "9876543210"));
        MockHttpServletRequest request = request(TestCdrs.gzip(plain), "gzip");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
        assertThat(wrapped.getInputStream().readAllBytes()).isEqualTo(plain);
        assertThat(wrapped.getHeader("Content-Encoding")).isNull();
        assertThat(wrapped.getHeader("Content-Length")).isNull();
        assertThat(wrapped.getContentLengthLong()).isEqualTo(-1);
        assertThat(Collections.list(wrapped.getHeaderNames())).doesNotContain("Content-Encoding", "Content-Length")
                .contains("Content-Type");
    }

    @Test
    void plainBodiesPassThroughUntouched() throws Exception {
        MockHttpServletRequest request = request(new byte[]{1, 2, 3}, null);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void identityEncodingPassesThrough() throws Exception {
        MockHttpServletRequest request = request(new byte[]{1}, "identity");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void unsupportedEncodingIs415() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(new byte[]{1}, "br"), response, chain);

        assertThat(response.getStatus()).isEqualTo(415);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void zipBombIsStoppedAtTheDecompressedLimit() throws Exception {
        byte[] bomb = TestCdrs.gzip(new byte[1_000_000]);   // ~1KB compressed, 1MB expanded
        assertThat(bomb.length).isLessThan(5_000);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request(bomb, "gzip"), new MockHttpServletResponse(), chain);

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
        assertThatThrownBy(() -> wrapped.getInputStream().readAllBytes())
                .isInstanceOf(PayloadTooLargeException.class)
                .hasMessageContaining("Decompressed request body");
    }

    private static MockHttpServletRequest request(byte[] body, String encoding) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cdrs/stream");
        request.setContentType("application/x-ndjson");
        request.addHeader("Content-Length", String.valueOf(body.length));
        if (encoding != null) {
            request.addHeader("Content-Encoding", encoding);
        }
        request.setContent(body);
        return request;
    }
}
