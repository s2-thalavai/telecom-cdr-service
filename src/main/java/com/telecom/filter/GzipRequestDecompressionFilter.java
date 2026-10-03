package com.telecom.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;
import java.util.zip.GZIPInputStream;

/**
 * Accepts Content-Encoding: gzip request bodies (neither Spring nor Tomcat decompresses them).
 * The decompressed size is bounded separately: 1MB of gzip can expand to gigabytes (zip bomb).
 */
public class GzipRequestDecompressionFilter extends OncePerRequestFilter {

    private final long maxDecompressedBytes;

    public GzipRequestDecompressionFilter(long maxDecompressedBytes) {
        this.maxDecompressedBytes = maxDecompressedBytes;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String encoding = request.getHeader(HttpHeaders.CONTENT_ENCODING);
        if (encoding == null || encoding.isBlank() || "identity".equalsIgnoreCase(encoding.trim())) {
            chain.doFilter(request, response);
            return;
        }
        if (!"gzip".equalsIgnoreCase(encoding.trim())) {
            ProblemResponses.write(response, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported Content-Encoding",
                    "Only gzip request bodies are supported");
            return;
        }
        chain.doFilter(new GzipRequest(request, maxDecompressedBytes), response);
    }

    static final class GzipRequest extends StreamReplacingRequestWrapper {

        private static final Set<String> HIDDEN_HEADERS = Set.of("content-encoding", "content-length");
        private final long limit;

        GzipRequest(HttpServletRequest request, long limit) {
            super(request);
            this.limit = limit;
        }

        @Override
        protected ServletInputStream createStream() throws IOException {
            return new BoundedServletInputStream(
                    new GZIPInputStream(getRequest().getInputStream(), 64 * 1024), limit, "Decompressed request body");
        }

        // Downstream code must see a plain, length-unknown body
        @Override
        public String getHeader(String name) {
            return hidden(name) ? null : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            return hidden(name) ? Collections.emptyEnumeration() : super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            return Collections.enumeration(Collections.list(super.getHeaderNames()).stream()
                    .filter(n -> !hidden(n)).toList());
        }

        @Override
        public int getContentLength() {
            return -1;
        }

        @Override
        public long getContentLengthLong() {
            return -1;
        }

        private static boolean hidden(String name) {
            return name != null && HIDDEN_HEADERS.contains(name.toLowerCase(Locale.ROOT));
        }
    }
}
