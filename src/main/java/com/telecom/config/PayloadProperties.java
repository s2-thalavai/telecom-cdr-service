package com.telecom.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

import java.util.List;

@ConfigurationProperties(prefix = "telecom.payload")
public record PayloadProperties(
        @DefaultValue("10MB") DataSize defaultMaxRequestSize,
        @DefaultValue("500MB") DataSize streamingMaxRequestSize,
        @DefaultValue("2GB") DataSize maxDecompressedSize,
        @DefaultValue({"/api/v1/cdrs/stream", "/api/v1/cdrs/async"}) List<String> streamingPaths,
        @DefaultValue("1000") int chunkSize,
        @DefaultValue("5000000") long maxRecordsPerRequest,
        @DefaultValue("100") int maxReportedErrors,
        @DefaultValue("./data/spool") String spoolDirectory,
        @DefaultValue("2") int asyncWorkers,
        @DefaultValue("20") int asyncQueueCapacity) {
}
