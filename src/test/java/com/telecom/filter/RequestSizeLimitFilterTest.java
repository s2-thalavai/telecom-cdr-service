package com.telecom.filter;

import com.telecom.config.PayloadProperties;
import com.telecom.exception.PayloadTooLargeException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestSizeLimitFilterTest {

    private final RequestSizeLimitFilter filter = new RequestSizeLimitFilter(new PayloadProperties(
            DataSize.ofBytes(100), DataSize.ofBytes(1000), DataSize.ofBytes(5000),
            List.of("/api/v1/cdrs/stream"), 10, 100, 10, "unused", 1, 1));

    @Test
    void rejectsDeclaredOversizeWith413AndNeverCallsTheChain() throws Exception {
        MockHttpServletRequest request = post("/api/v1/cdrs/batch", new byte[101]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"status\":413").contains("limit of 100 bytes");
        assertThat(response.getHeader("Connection")).isEqualTo("close");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void streamingPathsGetTheLargerLimit() throws Exception {
        MockHttpServletRequest request = post("/api/v1/cdrs/stream", new byte[500]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(((HttpServletRequest) chain.getRequest()).getInputStream().readAllBytes()).hasSize(500);
    }

    @Test
    void contextPathIsIgnoredWhenMatchingPaths() {
        MockHttpServletRequest request = post("/telecom/api/v1/cdrs/stream", new byte[0]);
        request.setContextPath("/telecom");

        assertThat(filter.limitFor(request)).isEqualTo(1000);
    }

    @Test
    void chunkedBodyWithoutContentLengthIsCountedWhileReading() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/cdrs/batch") {
            @Override
            public long getContentLengthLong() {
                return -1;   // chunked transfer: size unknown up front
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContent(new byte[150]);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        HttpServletRequest wrapped = (HttpServletRequest) chain.getRequest();
        assertThatThrownBy(() -> wrapped.getInputStream().readAllBytes()).isInstanceOf(PayloadTooLargeException.class);
    }

    @Test
    void getReaderIsAlsoBounded() throws Exception {
        MockHttpServletRequest request = post("/api/v1/cdrs/batch", "x".repeat(80).getBytes());
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(((HttpServletRequest) chain.getRequest()).getReader().readLine()).hasSize(80);
    }

    private static MockHttpServletRequest post(String uri, byte[] body) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType("application/json");
        request.setContent(body);
        return request;
    }
}
