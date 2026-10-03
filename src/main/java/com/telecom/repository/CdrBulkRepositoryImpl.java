package com.telecom.repository;

import com.telecom.dto.CdrRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * JDBC batch insert: one round trip per chunk (multi-row INSERT on MySQL with rewriteBatchedStatements=true).
 * JPA persist() of 1,000 entities would mean 1,000 managed objects plus IDENTITY ids that disable batching.
 */
public class CdrBulkRepositoryImpl implements CdrBulkRepository {

    static final String INSERT_SQL = """
            INSERT INTO call_detail_records
              (record_id, msisdn, called_number, call_type, start_time, duration_seconds,
               data_volume_bytes, cell_id, charge_amount, source, batch_id, ingested_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""";

    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public CdrBulkRepositoryImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
        this.namedJdbcTemplate = new NamedParameterJdbcTemplate(jdbcTemplate);
    }

    @Override
    public int batchInsert(List<CdrRequest> records, String source, String batchId) {
        if (records.isEmpty()) {
            return 0;
        }
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.batchUpdate(INSERT_SQL, records, records.size(), (ps, cdr) -> {
            ps.setString(1, cdr.recordId());
            ps.setString(2, cdr.msisdn());
            if (cdr.calledNumber() == null) ps.setNull(3, Types.VARCHAR); else ps.setString(3, cdr.calledNumber());
            ps.setString(4, cdr.callType().name());
            ps.setTimestamp(5, Timestamp.from(cdr.startTime()));
            ps.setInt(6, cdr.durationSeconds());
            ps.setLong(7, cdr.dataVolumeBytes());
            if (cdr.cellId() == null) ps.setNull(8, Types.VARCHAR); else ps.setString(8, cdr.cellId());
            ps.setBigDecimal(9, cdr.chargeAmount());
            ps.setString(10, source);
            ps.setString(11, batchId);
            ps.setTimestamp(12, now);
        });
        return records.size();
    }

    @Override
    public Set<String> findExistingRecordIds(Collection<String> recordIds) {
        if (recordIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(namedJdbcTemplate.queryForList(
                "SELECT record_id FROM call_detail_records WHERE record_id IN (:ids)",
                Map.of("ids", recordIds), String.class));
    }
}
