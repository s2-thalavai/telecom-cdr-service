package com.telecom.filter;

import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/** Base wrapper that replaces the body stream and keeps getReader() consistent with it. */
abstract class StreamReplacingRequestWrapper extends HttpServletRequestWrapper {

    private ServletInputStream stream;
    private BufferedReader reader;

    StreamReplacingRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    protected abstract ServletInputStream createStream() throws IOException;

    @Override
    public ServletInputStream getInputStream() throws IOException {
        if (stream == null) {
            stream = createStream();
        }
        return stream;
    }

    @Override
    public BufferedReader getReader() throws IOException {
        if (reader == null) {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            reader = new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
        return reader;
    }
}
