#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.tools/runtime"
LOG_FILE="$LOG_DIR/backend-real.log"
PID_FILE="$LOG_DIR/backend.pid"
DB_NAME="${SMOKE_DB_NAME:-private_domain_assistant_smoke}"
DB_USER="${SMOKE_DB_USER:-pda_smoke}"
DB_PASSWORD="${SMOKE_DB_PASSWORD:-pda_smoke_pwd}"
PORT="${SMOKE_PORT:-8080}"
WECOM_TRANSPORT_MODE="${WECOM_TRANSPORT_MODE:-DIRECT}"

required_wecom_variables=(
  WECOM_SMARTSHEET_DOC_ID
  WECOM_SMARTSHEET_SHEET_ID
  WECOM_SMARTSHEET_VIEW_ID
  WECOM_SMARTSHEET_SOURCE_TABLE
  WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE
)
case "$WECOM_TRANSPORT_MODE" in
  DIRECT)
    required_wecom_variables+=(WECOM_CORP_ID WECOM_APP_SECRET)
    ;;
  RELAY)
    required_wecom_variables+=(WECOM_RELAY_BASE_URL WECOM_RELAY_KEY_ID WECOM_RELAY_SECRET)
    ;;
  *)
    echo "backend_start_invalid_wecom_transport_mode"
    exit 2
    ;;
esac
missing_wecom_variables=()
for variable_name in "${required_wecom_variables[@]}"; do
  variable_value="${!variable_name:-}"
  if [[ -z "${variable_value//[[:space:]]/}" ]]; then
    missing_wecom_variables+=("$variable_name")
  fi
done
if (( ${#missing_wecom_variables[@]} > 0 )); then
  echo "backend_start_missing_wecom_configuration variables=${missing_wecom_variables[*]}"
  exit 2
fi

mkdir -p "$LOG_DIR"

if [[ -f "$PID_FILE" ]]; then
  old_pid="$(cat "$PID_FILE" || true)"
  if [[ -n "$old_pid" ]] && kill -0 "$old_pid" 2>/dev/null; then
    echo "backend_already_running pid=${old_pid} url=http://127.0.0.1:${PORT}"
    exit 0
  fi
fi

if curl -fsS "http://127.0.0.1:${PORT}/api/v1/auth/config" >/tmp/pda_auth_config.json 2>/tmp/pda_curl_err; then
  echo "backend_port_in_use_unverified pid=unknown url=http://127.0.0.1:${PORT}"
  echo "auth_config=$(cat /tmp/pda_auth_config.json)"
  exit 1
fi

sudo service mariadb start >/dev/null 2>&1 || sudo /etc/init.d/mariadb start >/dev/null 2>&1
sudo service redis-server start >/dev/null 2>&1 || sudo /etc/init.d/redis-server start >/dev/null 2>&1

if ! MYSQL_PWD="$DB_PASSWORD" mysql -u"$DB_USER" -Nse "USE \`${DB_NAME}\`; SELECT 1" >/dev/null 2>&1; then
  mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
fi

cd "$ROOT_DIR"
rm -f "$LOG_FILE"

SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
SPRING_DATASOURCE_USERNAME="$DB_USER" \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
MOCK_EXTERNALS=false \
SERVER_PORT="$PORT" \
WECOM_TRANSPORT_MODE="$WECOM_TRANSPORT_MODE" \
WECOM_RELAY_BASE_URL="${WECOM_RELAY_BASE_URL:-}" \
WECOM_RELAY_KEY_ID="${WECOM_RELAY_KEY_ID:-}" \
WECOM_RELAY_SECRET="${WECOM_RELAY_SECRET:-}" \
MAVEN_OPTS="-Dstyle.color=never" \
nohup mvn -Dstyle.color=never org.springframework.boot:spring-boot-maven-plugin:3.3.7:run >"$LOG_FILE" 2>&1 &
echo "$!" > "$PID_FILE"

for _ in $(seq 1 "${SMOKE_STARTUP_ATTEMPTS:-180}"); do
  if curl -fsS "http://127.0.0.1:${PORT}/api/v1/auth/config" >/tmp/pda_auth_config.json 2>/tmp/pda_curl_err; then
    echo "backend_ready pid=$(cat "$PID_FILE") url=http://127.0.0.1:${PORT} mock_external=false"
    echo "auth_config=$(cat /tmp/pda_auth_config.json)"
    exit 0
  fi
  if grep -q "APPLICATION FAILED TO START\|Application run failed\|BUILD FAILURE" "$LOG_FILE"; then
    echo "backend_start_failed"
    tail -n 160 "$LOG_FILE"
    exit 1
  fi
  sleep 2
done

echo "backend_start_timeout"
tail -n 200 "$LOG_FILE"
exit 124
