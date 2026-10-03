#!/usr/bin/env python3
"""Generate realistic CDR feeds for load and functional testing (stdlib only).

Examples:
  python3 scripts/gen_cdrs.py 1000000 -o sample-data/cdrs-1m-generated.ndjson.gz --gzip
  python3 scripts/gen_cdrs.py 5000 --format batch --source MSC-03 -o sample-data/batch-generated.json
  python3 scripts/gen_cdrs.py 20000 --error-rate 0.02 --duplicate-rate 0.01 -o sample-data/dirty-generated.ndjson
"""
import argparse
import gzip
import json
import random
import sys
from datetime import datetime, timedelta, timezone


def make_record(i, args, rng, now):
    kind = rng.choices(["VOICE", "SMS", "DATA"], weights=[45, 25, 30])[0]
    record = {
        "recordId": f"{args.prefix}-{i:010d}",
        "msisdn": f"9{rng.randint(0, args.subscribers - 1):09d}",
        "calledNumber": None if kind == "DATA" else f"8{rng.randint(0, 999_999_999):09d}",
        "callType": kind,
        "startTime": (now - timedelta(seconds=rng.randint(60, args.days * 86400))).isoformat().replace("+00:00", "Z"),
        "durationSeconds": rng.randint(1, 1800) if kind == "VOICE" else 0,
        "dataVolumeBytes": rng.randint(1_000, 500_000_000) if kind == "DATA" else 0,
        "cellId": f"CELL-{rng.randint(1, 5000)}",
        "chargeAmount": round(rng.uniform(0, 25), 4),
    }
    if rng.random() < args.error_rate:          # inject a validation error
        broken = rng.choice(["msisdn", "callType", "chargeAmount"])
        record[broken] = {"msisdn": "12345", "callType": "FAX", "chargeAmount": -1}[broken]
    return record


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("count", type=int, help="number of records")
    p.add_argument("-o", "--output", default="-", help="output file (default stdout)")
    p.add_argument("--format", choices=["ndjson", "array", "batch"], default="ndjson")
    p.add_argument("--gzip", action="store_true", help="gzip the output")
    p.add_argument("--prefix", default="GEN", help="recordId prefix (change it to avoid duplicates)")
    p.add_argument("--source", default="MSC-01", help="source for --format batch")
    p.add_argument("--subscribers", type=int, default=10_000)
    p.add_argument("--days", type=int, default=30, help="spread start times over the last N days")
    p.add_argument("--error-rate", type=float, default=0.0)
    p.add_argument("--duplicate-rate", type=float, default=0.0)
    p.add_argument("--seed", type=int, default=42)
    args = p.parse_args()

    rng = random.Random(args.seed)
    now = datetime.now(timezone.utc).replace(microsecond=0)

    if args.output == "-":
        out = sys.stdout.buffer
    elif args.gzip:
        out = gzip.open(args.output, "wb", compresslevel=6)
    else:
        out = open(args.output, "wb")

    def write(text):
        out.write(text.encode("utf-8"))

    if args.format == "batch":
        write('{"source":%s,"records":[' % json.dumps(args.source))
    elif args.format == "array":
        write("[")

    last = None
    for i in range(args.count):
        rec = last if (last is not None and rng.random() < args.duplicate_rate) else make_record(i, args, rng, now)
        last = rec
        line = json.dumps(rec, separators=(",", ":"))
        if args.format == "ndjson":
            write(line + "\n")
        else:
            write(("," if i else "") + line)

    if args.format == "batch":
        write("]}")
    elif args.format == "array":
        write("]")
    if out is not sys.stdout.buffer:
        out.close()


if __name__ == "__main__":
    main()
