#!/usr/bin/env bash
# End-to-end large-payload checks against a running instance. Requires curl, gzip, python3.
# Usage: BASE_URL=http://localhost:8080 N=20000 ./scripts/smoke-test.sh
set -uo pipefail
cd "$(dirname "$0")/.."

BASE_URL="${BASE_URL:-http://localhost:8080}"
API="$BASE_URL/api/v1"
N="${N:-20000}"
TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
PASS=0; FAIL=0
RUN="SMOKE$(date +%s)"

green() { printf '\033[32m%s\033[0m\n' "$*"; }
red()   { printf '\033[31m%s\033[0m\n' "$*"; }
check() { if [[ "$3" == "$2" ]]; then green "PASS  $1 ($3)"; PASS=$((PASS+1)); else red "FAIL  $1 (got '$3', want '$2')"; FAIL=$((FAIL+1)); fi; }
json()  { python3 -c "import json,sys; d=json.load(sys.stdin); print(eval('d'+sys.argv[1]))" "$1"; }

for tool in curl gzip python3; do command -v $tool >/dev/null || { red "$tool is required"; exit 1; }; done
echo "Waiting for $BASE_URL/actuator/health ..."
for _ in $(seq 1 60); do curl -fs "$BASE_URL/actuator/health" >/dev/null 2>&1 && break; sleep 2; done
curl -fs "$BASE_URL/actuator/health" >/dev/null || { red "Service not healthy at $BASE_URL"; exit 1; }

echo; echo "== Generating $N records =="
python3 scripts/gen_cdrs.py "$N" --prefix "$RUN" --subscribers 50 --days 5 -o "$TMP/feed.ndjson"
gzip -k "$TMP/feed.ndjson"
echo "plain: $(wc -c < "$TMP/feed.ndjson") bytes, gzip: $(wc -c < "$TMP/feed.ndjson.gz") bytes"

echo; echo "== Streaming (gzip) =="
START=$(date +%s)
R=$(curl -s -X POST --data-binary @"$TMP/feed.ndjson.gz" -H "Content-Type: application/x-ndjson" \
     -H "Content-Encoding: gzip" "$API/cdrs/stream?source=SMOKE&batchId=$RUN")
echo "  took $(( $(date +%s) - START ))s"
check "stream accepted all" "$N" "$(echo "$R" | json "['accepted']")"

R=$(curl -s -X POST --data-binary @"$TMP/feed.ndjson.gz" -H "Content-Type: application/x-ndjson" \
     -H "Content-Encoding: gzip" "$API/cdrs/stream?source=SMOKE")
check "resend is idempotent (duplicates)" "$N" "$(echo "$R" | json "['duplicates']")"

echo; echo "== Async (202 + polling) =="
python3 scripts/gen_cdrs.py 5000 --prefix "${RUN}A" -o "$TMP/async.ndjson"
LOC=$(curl -s -D - -o /dev/null -X POST --data-binary @"$TMP/async.ndjson" -H "Content-Type: application/x-ndjson" \
      "$API/cdrs/async?source=SMOKE-ASYNC" | tr -d '\r' | sed -n 's/^[Ll]ocation: //p')
[[ -n "$LOC" ]] && green "PASS  async accepted, job $LOC" && PASS=$((PASS+1)) || { red "FAIL  no Location header"; FAIL=$((FAIL+1)); }
STATUS=""
for _ in $(seq 1 60); do
  STATUS=$(curl -s "$BASE_URL$LOC" | json "['status']")
  [[ "$STATUS" == "COMPLETED" || "$STATUS" == "FAILED" ]] && break; sleep 1
done
check "async job completed" "COMPLETED" "$STATUS"

echo; echo "== Limits & errors =="
head -c 11000000 /dev/zero | tr '\0' ' ' > "$TMP/big.json"
check "batch body > 10MB -> 413" 413 "$(curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary @"$TMP/big.json" \
      -H 'Content-Type: application/json' "$API/cdrs/batch")"
check "Content-Encoding br -> 415" 415 "$(curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary @"$TMP/feed.ndjson" \
      -H 'Content-Type: application/x-ndjson' -H 'Content-Encoding: br' "$API/cdrs/stream?source=X")"
check "fake gzip -> 400" 400 "$(curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary @sample-data/cdrs-small.ndjson \
      -H 'Content-Type: application/x-ndjson' -H 'Content-Encoding: gzip' "$API/cdrs/stream?source=X")"
printf '%s\n{broken' "$(head -1 sample-data/cdrs-small.ndjson | sed "s/SAMPLE/${RUN}M/")" > "$TMP/broken.ndjson"
check "malformed stream -> 400" 400 "$(curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary @"$TMP/broken.ndjson" \
      -H 'Content-Type: application/x-ndjson' "$API/cdrs/stream?source=X")"
check "missing source -> 400" 400 "$(curl -s -o /dev/null -w '%{http_code}' -X POST --data-binary @sample-data/cdrs-small.ndjson \
      -H 'Content-Type: application/x-ndjson' "$API/cdrs/stream")"

echo; echo "== Queries =="
MSISDN=$(head -1 "$TMP/feed.ndjson" | json "['msisdn']")
FROM=$(python3 -c "from datetime import datetime,timedelta,timezone as t; print((datetime.now(t.utc)-timedelta(days=6)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
TO=$(python3 -c "from datetime import datetime,timedelta,timezone as t; print((datetime.now(t.utc)+timedelta(minutes=1)).strftime('%Y-%m-%dT%H:%M:%SZ'))")
U=$(curl -s "$API/subscribers/$MSISDN/usage?from=$FROM&to=$TO")
CALLS=$(echo "$U" | json "['voiceCalls']+d['smsCount']")
[[ "$CALLS" -gt 0 ]] && green "PASS  usage for $MSISDN: $(echo "$U" | tr -d '\n' | cut -c1-120)" && PASS=$((PASS+1)) \
  || { red "FAIL  usage: $U"; FAIL=$((FAIL+1)); }
check "page size capped at 500" 500 "$(curl -s "$API/cdrs?msisdn=$MSISDN&from=$FROM&to=$TO&size=100000" | json "['size']")"

echo; echo "Passed: $PASS  Failed: $FAIL"
[[ $FAIL -eq 0 ]]
