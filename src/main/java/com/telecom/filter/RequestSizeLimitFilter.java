package com.telecom.filter;

import com.telecom.config.PayloadProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Per-path body size limit. Rejects early on Content-Length and also counts bytes for
 * chunked requests (no Content-Length) so the limit cannot be bypassed.
 */
public class RequestSizeLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private final long defaultLimit;
    private final long streamingLimit;
    private final List<String> streamingPaths;

    public RequestSizeLimitFilter(PayloadProperties properties) {
        this.defaultLimit = properties.defaultMaxRequestSize().toBytes();
        this.streamingLimit = properties.streamingMaxRequestSize().toBytes();
        this.streamingPaths = List.copyOf(properties.streamingPaths());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long limit = limitFor(request);
        long declared = request.getContentLengthLong();
        if (declared > limit) {
            log.warn("Rejected {} {}: Content-Length {} exceeds {}", request.getMethod(),
                    request.getRequestURI(), declared, limit);
            ProblemResponses.write(response, HttpStatus.PAYLOAD_TOO_LARGE, "Payload Too Large",
                    "Request body of %d bytes exceeds the limit of %d bytes".formatted(declared, limit));
            return;
        }
        chain.doFilter(new StreamReplacingRequestWrapper(request) {
            @Override
            protected ServletInputStream createStream() throws IOException {
                return new BoundedServletInputStream(getRequest().getInputStream(), limit, "Request body");
            }
        }, response);
    }

    long limitFor(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return streamingPaths.stream().anyMatch(path::startsWith) ? streamingLimit : defaultLimit;
    }
}
