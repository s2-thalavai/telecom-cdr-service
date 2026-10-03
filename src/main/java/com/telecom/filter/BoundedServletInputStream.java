package com.telecom.filter;

import com.telecom.exception.PayloadTooLargeException;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;

import java.io.IOException;
import java.io.InputStream;

/** Counts bytes as they are read and aborts as soon as the limit is crossed (works without Content-Length). */
public class BoundedServletInputStream extends ServletInputStream {

    private final InputStream delegate;
    private final long limit;
    private final String description;
    private long bytesRead;
    private boolean finished;

    public BoundedServletInputStream(InputStream delegate, long limit, String description) {
        this.delegate = delegate;
        this.limit = limit;
        this.description = description;
    }

    @Override
    public int read() throws IOException {
        int b = delegate.read();
        if (b == -1) {
            finished = true;
            return -1;
        }
        count(1);
        return b;
    }

    @Override
    public int read(byte[] buffer, int offset, int length) throws IOException {
        int n = delegate.read(buffer, offset, length);
        if (n == -1) {
            finished = true;
            return -1;
        }
        count(n);
        return n;
    }

    private void count(int n) throws PayloadTooLargeException {
        bytesRead += n;
        if (bytesRead > limit) {
            throw new PayloadTooLargeException(description + " exceeds the limit of " + limit + " bytes");
        }
    }

    public long bytesRead() {
        return bytesRead;
    }

    @Override
    public boolean isFinished() {
        return finished;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public void setReadListener(ReadListener readListener) {
        throw new UnsupportedOperationException("Non-blocking reads are not supported");
    }

    @Override
    public void close() throws IOException {
        delegate.close();
    }
}
