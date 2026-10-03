package com.telecom.exception;

import com.telecom.dto.IngestionSummary;

/** HTTP 400 - the stream became unparsable; records before that point were already committed (partialResult). */
public class MalformedPayloadException extends RuntimeException {

    private final transient IngestionSummary partialResult;

    public MalformedPayloadException(String message, IngestionSummary partialResult) {
        super(message);
        this.partialResult = partialResult;
    }

    public IngestionSummary getPartialResult() {
        return partialResult;
    }
}
