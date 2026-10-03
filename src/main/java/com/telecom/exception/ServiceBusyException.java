package com.telecom.exception;

/** HTTP 503 - async ingestion queue is full. */
public class ServiceBusyException extends RuntimeException {
    public ServiceBusyException(String message) {
        super(message);
    }
}
