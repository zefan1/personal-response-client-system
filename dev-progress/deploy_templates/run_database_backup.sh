#!/usr/bin/env bash
set -euo pipefail

ENV_FILE="/opt/private-domain-assistant/config/production.env"
BACKUP_SCRIPT="/usr/local/sbin/private-domain-assistant-backup-database"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing_environment_file=$ENV_FILE"
  exit 1
fi

DB_PASSWORD="$(sed -n 's/^SPRING_DATASOURCE_PASSWORD=//p' "$ENV_FILE")"
if [[ -z "$DB_PASSWORD" ]]; then
  echo "missing_database_password=true"
  exit 2
fi

export DB_PASSWORD
export DB_NAME="private_domain_assistant_prod"
export DB_USER="pda_prod"
export DB_HOST="127.0.0.1"
export BACKUP_DIR="/data/private-domain-assistant/backups/mysql"
export RETENTION_DAYS="14"

exec "$BACKUP_SCRIPT"
