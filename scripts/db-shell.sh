#!/usr/bin/env bash
# MySQL shell inside the Docker Compose database container.
set -euo pipefail
cd "$(dirname "$0")/.."
[[ -f .env ]] && set -a && source .env && set +a
exec docker compose exec mysql mysql \
  -u"${MYSQL_USER:-telecom_user}" -p"${MYSQL_PASSWORD:-telecom_pass_change_me}" "${MYSQL_DATABASE:-telecomdb}"
