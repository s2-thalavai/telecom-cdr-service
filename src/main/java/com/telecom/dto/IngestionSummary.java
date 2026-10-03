package com.telecom.dto;

import java.util.List;

public record IngestionSummary(String batchId, String source, long received, long accepted, long duplicates,
                               long rejected, long durationMs, List<RecordError> errors) {
}
