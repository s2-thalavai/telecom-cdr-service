package com.telecom.dto;

import com.telecom.entity.CallType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;

/** One CDR as sent by a network element. */
public record CdrRequest(
        @NotBlank @Size(max = 64) String recordId,
        @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$", message = "must be a valid 10-digit mobile number") String msisdn,
        @Size(max = 20) String calledNumber,
        @NotNull CallType callType,
        @NotNull @PastOrPresent Instant startTime,
        @PositiveOrZero int durationSeconds,
        @PositiveOrZero long dataVolumeBytes,
        @Size(max = 20) String cellId,
        @NotNull @DecimalMin("0.0000") @Digits(integer = 8, fraction = 4) BigDecimal chargeAmount) {
}
