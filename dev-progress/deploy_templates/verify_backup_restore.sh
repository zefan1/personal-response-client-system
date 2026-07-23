#!/usr/bin/env bash
set -euo pipefail

BACKUP_FILE="${1:-}"
RESTORE_DB="private_domain_assistant_restore_check_20260720"

if [[ -z "$BACKUP_FILE" || ! -f "$BACKUP_FILE" ]]; then
  echo "usage: $0 /path/to/backup.sql.gz"
  exit 2
fi

mariadb --protocol=socket <<SQL
DROP DATABASE IF EXISTS ${RESTORE_DB};
CREATE DATABASE ${RESTORE_DB} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
SQL

cleanup() {
  mariadb --protocol=socket -e "DROP DATABASE IF EXISTS ${RESTORE_DB};"
}
trap cleanup EXIT

gunzip -c "$BACKUP_FILE" | mariadb --protocol=socket "$RESTORE_DB"

TABLE_COUNT="$(mariadb --protocol=socket -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${RESTORE_DB}';")"
MIGRATION_VERSION="$(mariadb --protocol=socket -N -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM ${RESTORE_DB}.flyway_schema_history WHERE success=1;")"

if [[ "$TABLE_COUNT" -lt 1 || -z "$MIGRATION_VERSION" ]]; then
  echo "restore_drill_failed=true"
  exit 3
fi

echo "restore_drill_passed=true"
echo "restored_table_count=$TABLE_COUNT"
echo "restored_migration_version=$MIGRATION_VERSION"
