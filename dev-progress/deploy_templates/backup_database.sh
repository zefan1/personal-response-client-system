#!/usr/bin/env bash
set -euo pipefail

# Example database backup script.
# Put on the server, chmod +x, and run from cron.

BACKUP_DIR="${BACKUP_DIR:-/data/private-domain-assistant/backups/mysql}"
DB_NAME="${DB_NAME:-private_domain_assistant_prod}"
DB_USER="${DB_USER:-pda_prod}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"
TIMESTAMP="$(date +%Y%m%d-%H%M%S)"
OUTPUT="${BACKUP_DIR}/${DB_NAME}-${TIMESTAMP}.sql.gz"

mkdir -p "$BACKUP_DIR"

mysqldump \
  --single-transaction \
  --routines \
  --triggers \
  --default-character-set=utf8mb4 \
  -u"$DB_USER" \
  -p"${DB_PASSWORD:?DB_PASSWORD is required}" \
  "$DB_NAME" | gzip -9 > "$OUTPUT"

find "$BACKUP_DIR" -type f -name "${DB_NAME}-*.sql.gz" -mtime +"$RETENTION_DAYS" -delete

echo "backup_created=$OUTPUT"
