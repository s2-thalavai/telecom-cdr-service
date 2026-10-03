package com.telecom.dto;

import com.telecom.entity.CallDetailRecord;
import com.telecom.entity.CallType;

import java.math.BigDecimal;
import java.time.Instant;

public record CdrResponse(Long id, String recordId, String msisdn, String calledNumber, CallType callType,
                          Instant startTime, int durationSeconds, long dataVolumeBytes, String cellId,
                          BigDecimal chargeAmount, String source, String batchId) {

    public static CdrResponse from(CallDetailRecord c) {
        return new CdrResponse(c.getId(), c.getRecordId(), c.getMsisdn(), c.getCalledNumber(), c.getCallType(),
                c.getStartTime(), c.getDurationSeconds(), c.getDataVolumeBytes(), c.getCellId(),
                c.getChargeAmount(), c.getSource(), c.getBatchId());
    }
}
