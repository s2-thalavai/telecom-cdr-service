package com.telecom.support;

import com.telecom.entity.CallType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.zip.GZIPOutputStream;

/**
 * Deterministic CDR payloads. Record i has type VOICE/SMS/DATA (i % 3), so for a multiple of 3 records:
 * voice calls = n/3 (60s each), sms = n/3, data = n/3 (1000 bytes each); every record charges 1.5.
 */
public final class TestCdrs {

    /** Midnight UTC, ten days ago: always in the past (passes @PastOrPresent), stable within a test run. */
    public static final Instant BASE = Instant.now().truncatedTo(ChronoUnit.DAYS).minus(10, ChronoUnit.DAYS);

    private TestCdrs() {
    }

    public static String record(String recordId, String msisdn, CallType type, Instant start) {
        String called = type == CallType.DATA ? "null" : "\"9123400000\"";
        int duration = type == CallType.VOICE ? 60 : 0;
        long data = type == CallType.DATA ? 1000 : 0;
        return ("{\"recordId\":\"%s\",\"msisdn\":\"%s\",\"calledNumber\":%s,\"callType\":\"%s\",\"startTime\":\"%s\","
                + "\"durationSeconds\":%d,\"dataVolumeBytes\":%d,\"cellId\":\"CELL-1\",\"chargeAmount\":1.5}")
                .formatted(recordId, msisdn, called, type, start, duration, data);
    }

    public static String record(int i, String idPrefix, String msisdn) {
        return record(idPrefix + "-" + i, msisdn, CallType.values()[i % 3], BASE.plusSeconds(i * 60L));
    }

    /** Newline-delimited JSON. */
    public static String ndjson(int count, String idPrefix, String msisdn) {
        StringBuilder sb = new StringBuilder(count * 230);
        for (int i = 0; i < count; i++) {
            sb.append(record(i, idPrefix, msisdn)).append('\n');
        }
        return sb.toString();
    }

    /** Same records as a JSON array. */
    public static String jsonArray(int count, String idPrefix, String msisdn) {
        StringBuilder sb = new StringBuilder(count * 230).append('[');
        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(record(i, idPrefix, msisdn));
        }
        return sb.append(']').toString();
    }

    /** Body for POST /cdrs/batch. */
    public static String batch(String source, int count, String idPrefix, String msisdn) {
        return "{\"source\":\"" + source + "\",\"records\":" + jsonArray(count, idPrefix, msisdn) + "}";
    }

    public static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }

    public static byte[] gzip(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(out)) {
            gz.write(data);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }
}
