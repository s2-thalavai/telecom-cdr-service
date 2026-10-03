package com.telecom.repository;

import com.telecom.entity.CallDetailRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

public interface CdrRepository extends JpaRepository<CallDetailRecord, Long>, CdrBulkRepository {

    Page<CallDetailRecord> findByMsisdnAndStartTimeGreaterThanEqualAndStartTimeLessThan(
            String msisdn, Instant from, Instant to, Pageable pageable);

    long countByBatchId(String batchId);

    /** Aggregation in the database: never load millions of rows to sum them in Java. */
    @Query("""
            select
              sum(case when c.callType = com.telecom.entity.CallType.VOICE then 1 else 0 end) as voiceCalls,
              sum(case when c.callType = com.telecom.entity.CallType.VOICE then c.durationSeconds else 0 end) as voiceSeconds,
              sum(case when c.callType = com.telecom.entity.CallType.SMS then 1 else 0 end) as smsCount,
              sum(c.dataVolumeBytes) as dataBytes,
              sum(c.chargeAmount) as totalCharge
            from CallDetailRecord c
            where c.msisdn = :msisdn and c.startTime >= :from and c.startTime < :to
            """)
    UsageTotals summarizeUsage(@Param("msisdn") String msisdn, @Param("from") Instant from, @Param("to") Instant to);
}
