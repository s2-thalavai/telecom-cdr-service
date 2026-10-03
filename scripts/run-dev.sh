#!/usr/bin/env bash
# Run locally on the dev profile (H2 in-memory).
set -euo pipefail
cd "$(dirname "$0")/.."
MVN="${MVN:-mvn}"
command -v "$MVN" >/dev/null 2>&1 || { echo "Maven not found. Install Maven 3.9+ or use 'make up' (Docker)."; exit 1; }
echo "Starting telecom-cdr-service (dev) on http://localhost:${SERVER_PORT:-8080}"
exec "$MVN" -B spring-boot:run -Dspring-boot.run.profiles=dev
