package com.telecom.exception;

/** HTTP 413 - more records than telecom.payload.max-records-per-request. */
public class TooManyRecordsException extends RuntimeException {
    public TooManyRecordsException(String message) {
        super(message);
    }
}
