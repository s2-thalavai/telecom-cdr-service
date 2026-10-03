-- Operational queries. Run: make db-reports

-- Records per source and batch (latest first)
SELECT source, batch_id, COUNT(*) AS records, MIN(ingested_at) AS first_ingested
FROM call_detail_records
GROUP BY source, batch_id
ORDER BY first_ingested DESC
LIMIT 20;

-- Traffic mix per day
SELECT DATE(start_time) AS day, call_type, COUNT(*) AS records,
       SUM(duration_seconds) AS voice_seconds, ROUND(SUM(data_volume_bytes) / 1024 / 1024) AS data_mb
FROM call_detail_records
GROUP BY DATE(start_time), call_type
ORDER BY day DESC, call_type;

-- Top subscribers by charge (last 30 days)
SELECT msisdn, COUNT(*) AS records, SUM(charge_amount) AS total_charge
FROM call_detail_records
WHERE start_time >= UTC_TIMESTAMP() - INTERVAL 30 DAY
GROUP BY msisdn
ORDER BY total_charge DESC
LIMIT 10;

-- Ingestion jobs that never finished (e.g. pod restarted mid-job)
SELECT id, status, source, created_at, started_at
FROM ingestion_jobs
WHERE status IN ('PENDING', 'RUNNING') AND created_at < UTC_TIMESTAMP() - INTERVAL 1 HOUR;

-- Job outcomes
SELECT status, COUNT(*) AS jobs, SUM(accepted) AS accepted, SUM(rejected) AS rejected, SUM(duplicates) AS duplicates
FROM ingestion_jobs GROUP BY status;
