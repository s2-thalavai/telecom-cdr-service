package com.telecom.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.repository.CdrRepository;
import com.telecom.repository.IngestionJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/**
 * Full stack over real HTTP: embedded Tomcat, the real filter chain (size limit + gzip), H2.
 * The "test" profile uses small limits: 64KB default body, 2MB streaming body, 4MB decompressed,
 * chunk size 50, at most 5,000 records per request, 1 async worker with a queue of 2.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final Path SPOOL = createTempDir();
    protected static final MediaType NDJSON = MediaType.APPLICATION_NDJSON;

    @DynamicPropertySource
    static void spoolDirectory(DynamicPropertyRegistry registry) {
        registry.add("telecom.payload.spool-directory", SPOOL::toString);
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected CdrRepository cdrs;

    @Autowired
    protected IngestionJobRepository jobs;

    @BeforeEach
    void resetDatabase() {
        cdrs.deleteAllInBatch();
        jobs.deleteAllInBatch();
    }

    protected ResponseEntity<JsonNode> post(String path, Object body, MediaType contentType, String contentEncoding) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(contentType);
        if (contentEncoding != null) {
            headers.set(HttpHeaders.CONTENT_ENCODING, contentEncoding);
        }
        return rest.exchange(path, HttpMethod.POST, new HttpEntity<>(body, headers), JsonNode.class);
    }

    protected static long spoolFileCount() {
        try (Stream<Path> files = Files.list(SPOOL)) {
            return files.count();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Path createTempDir() {
        try {
            return Files.createTempDirectory("telecom-cdr-it-spool-");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
