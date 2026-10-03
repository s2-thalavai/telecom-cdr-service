package com.telecom.dto;

import java.nio.file.Path;

public record SpooledPayload(Path file, long sizeBytes) {
}
