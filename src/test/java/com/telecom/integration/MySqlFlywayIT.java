package com.telecom.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.telecom.support.TestCdrs;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.nio.file.Files;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PROD profile on a real MySQL 8.4: Flyway schema matches the JPA mappings (ddl-auto=none),
 * JDBC batch inserts, duplicate-key translation and the aggregate query work on MySQL.
 * Skipped automatically when Docker is unavailable.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("prod")
@Testcontainers(disabledWithoutDocker = true)
class MySqlFlywayIT {

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void spool(DynamicPropertyRegistry registry) throws IOException {
        String dir = Files.createTempDirectory("telecom-cdr-mysql-it-").toString();
        registry.add("telecom.payload.spool-directory", () -> dir);
    }

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private Flyway flyway;

    @Test
    void migrationsAreApplied() {
        assertThat(Arrays.stream(flyway.info().applied()).map(m -> m.getVersion().getVersion())).containsExactly("1");
    }

    @Test
    void streamIngestionDeduplicationAndUsageOnMySql() {
        byte[] body = TestCdrs.gzip(TestCdrs.bytes(TestCdrs.ndjson(3000, "MY", "9811111111")));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_NDJSON);
        headers.set(HttpHeaders.CONTENT_ENCODING, "gzip");

        ResponseEntity<JsonNode> first = rest.postForEntity("/api/v1/cdrs/stream?source=MSC-MY",
                new HttpEntity<>(body, headers), JsonNode.class);
        ResponseEntity<JsonNode> again = rest.postForEntity("/api/v1/cdrs/stream?source=MSC-MY",
                new HttpEntity<>(body, headers), JsonNode.class);

        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(first.getBody().get("accepted").asLong()).isEqualTo(3000);
        assertThat(again.getBody().get("duplicates").asLong()).isEqualTo(3000);

        JsonNode usage = rest.getForObject("/api/v1/subscribers/{m}/usage?from={f}&to={t}", JsonNode.class,
                "9811111111", TestCdrs.BASE, TestCdrs.BASE.plus(3, ChronoUnit.DAYS));
        assertThat(usage.get("voiceCalls").asLong()).isEqualTo(1000);
        assertThat(usage.get("totalCharge").decimalValue()).isEqualByComparingTo("4500");
    }
}
