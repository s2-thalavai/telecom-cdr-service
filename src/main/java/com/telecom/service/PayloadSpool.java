package com.telecom.service;

import com.telecom.config.PayloadProperties;
import com.telecom.dto.SpooledPayload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Writes request bodies to disk quickly so the HTTP request can finish before processing starts. */
@Component
public class PayloadSpool {

    private static final Logger log = LoggerFactory.getLogger(PayloadSpool.class);
    private final Path directory;

    public PayloadSpool(PayloadProperties properties) {
        this.directory = Path.of(properties.spoolDirectory()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create spool directory " + directory, e);
        }
    }

    public Path directory() {
        return directory;
    }

    public SpooledPayload spool(InputStream body) throws IOException {
        Path file = Files.createTempFile(directory, "cdr-", ".json");
        try {
            long bytes = Files.copy(body, file, StandardCopyOption.REPLACE_EXISTING);   // limits enforced by filters
            return new SpooledPayload(file, bytes);
        } catch (IOException | RuntimeException e) {
            delete(file);
            throw e;
        }
    }

    public void delete(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Could not delete spooled payload {}", file, e);
        }
    }
}
