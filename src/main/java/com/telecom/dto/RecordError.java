package com.telecom.dto;

/** recordIndex is the 1-based position in the payload; recordId is null if the record was unreadable. */
public record RecordError(long recordIndex, String recordId, String message) {
}
