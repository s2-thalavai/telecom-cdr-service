package com.telecom.exception;

import java.io.IOException;

/**
 * Thrown by BoundedServletInputStream while reading. An IOException on purpose: parsers and
 * converters propagate it (or wrap it) instead of treating it as bad JSON.
 */
public class PayloadTooLargeException extends IOException {
    public PayloadTooLargeException(String message) {
        super(message);
    }
}
