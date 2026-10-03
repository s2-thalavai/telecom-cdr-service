package com.telecom.repository;

import java.math.BigDecimal;

/** Interface projection for the aggregate query (all values null when there are no rows). */
public interface UsageTotals {
    Long getVoiceCalls();
    Long getVoiceSeconds();
    Long getSmsCount();
    Long getDataBytes();
    BigDecimal getTotalCharge();
}
