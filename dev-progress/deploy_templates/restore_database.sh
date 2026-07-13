#!/usr/bin/env bash
set -euo pipefail

# Example restore script. Stop the application and make a fresh backup before use.

BACKUP_FILE="${1:-}"
DB_NAME="${DB_NAME:-private_domain_assistant_prod}"
DB_USER="${DB_USER:-pda_prod}"

if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "usage: DB_PASSWORD=... $0 /path/to/backup.sql.gz"
  exit 2
fi

echo "Restoring $BACKUP_FILE into $DB_NAME"
read -r -p "Type RESTORE to continue: " CONFIRM
if [[ "$CONFIRM" != "RESTORE" ]]; then
  echo "restore_cancelled"
  exit 1
fi

gunzip -c "$BACKUP_FILE" | mysql \
  --default-character-set=utf8mb4 \
  -u"$DB_USER" \
  -p"${DB_PASSWORD:?DB_PASSWORD is required}" \
  "$DB_NAME"

echo "restore_completed"
