#!/usr/bin/env bash
# Delete everything including the MySQL PVC (data is lost).
set -euo pipefail
cd "$(dirname "$0")/.."
kubectl delete -k k8s/ --ignore-not-found
kubectl -n telecom-cdr delete pvc --all --ignore-not-found 2>/dev/null || true
kubectl delete namespace telecom-cdr --ignore-not-found
