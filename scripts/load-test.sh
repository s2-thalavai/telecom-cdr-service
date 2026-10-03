#!/usr/bin/env bash
# Push one big gzip feed and report throughput. Watch memory with `docker stats` meanwhile:
# heap stays flat because nothing buffers the whole payload.
# Usage: N=1000000 BASE_URL=http://localhost:8080 ./scripts/load-test.sh
set -euo pipefail
cd "$(dirname "$0")/.."
BASE_URL="${BASE_URL:-http://localhost:8080}"
N="${N:-1000000}"
FILE="sample-data/cdrs-${N}-generated.ndjson.gz"
[[ -f "$FILE" ]] || python3 scripts/gen_cdrs.py "$N" --gzip --prefix "LOAD$(date +%s)" -o "$FILE"
echo "Feed: $FILE ($(du -h "$FILE" | cut -f1) compressed)"
START=$(date +%s.%N)
curl -s -X POST --data-binary @"$FILE" -H "Content-Type: application/x-ndjson" -H "Content-Encoding: gzip" \
     "$BASE_URL/api/v1/cdrs/stream?source=LOAD" | python3 -m json.tool
END=$(date +%s.%N)
python3 -c "e=$END-$START; print(f'{$N} records in {e:.1f}s -> {$N/e:,.0f} records/s')"
