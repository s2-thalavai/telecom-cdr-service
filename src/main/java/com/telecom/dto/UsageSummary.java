package com.telecom.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record UsageSummary(String msisdn, Instant from, Instant to, long voiceCalls, long voiceSeconds,
                           long smsCount, long dataBytes, BigDecimal totalCharge) {
}
