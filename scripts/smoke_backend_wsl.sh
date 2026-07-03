#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.tools/smoke"
LOG_FILE="$LOG_DIR/backend.log"
PID_FILE="$LOG_DIR/backend.pid"
DB_NAME="${SMOKE_DB_NAME:-private_domain_assistant_smoke}"
DB_USER="${SMOKE_DB_USER:-pda_smoke}"
DB_PASSWORD="${SMOKE_DB_PASSWORD:-pda_smoke_pwd}"
PORT="${SMOKE_PORT:-8080}"

mkdir -p "$LOG_DIR"

cleanup() {
  if [[ -f "$PID_FILE" ]]; then
    local pid
    pid="$(cat "$PID_FILE" || true)"
    if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
      kill "$pid" 2>/dev/null || true
      wait "$pid" 2>/dev/null || true
    fi
  fi
}
trap cleanup EXIT

sudo service mariadb start >/dev/null 2>&1 || sudo /etc/init.d/mariadb start >/dev/null 2>&1
sudo service redis-server start >/dev/null 2>&1 || sudo /etc/init.d/redis-server start >/dev/null 2>&1

mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -e "DROP DATABASE ${DB_NAME}; CREATE DATABASE ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" >/dev/null

rm -f "$LOG_FILE" "$PID_FILE"
cd "$ROOT_DIR"

SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
SPRING_DATASOURCE_USERNAME="$DB_USER" \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
MOCK_EXTERNALS=true \
SERVER_PORT="$PORT" \
MAVEN_OPTS="-Dstyle.color=never" \
mvn -Dstyle.color=never org.springframework.boot:spring-boot-maven-plugin:3.3.7:run >"$LOG_FILE" 2>&1 &
echo "$!" > "$PID_FILE"

for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${PORT}/api/v1/auth/config" >/tmp/pda_auth_config.json 2>/tmp/pda_curl_err; then
    echo "auth_config=$(cat /tmp/pda_auth_config.json)"
    break
  fi
  if grep -q "APPLICATION FAILED TO START\|Application run failed\|BUILD FAILURE" "$LOG_FILE"; then
    echo "backend_start_failed"
    tail -n 160 "$LOG_FILE"
    exit 1
  fi
  sleep 2
done

if ! curl -fsS "http://127.0.0.1:${PORT}/api/v1/auth/config" >/tmp/pda_auth_config.json; then
  echo "backend_start_timeout"
  tail -n 200 "$LOG_FILE"
  exit 124
fi

login_payload='{"username":"admin","password":"admin123"}'
login_response="$(curl -fsS -H 'Content-Type: application/json' -d "$login_payload" "http://127.0.0.1:${PORT}/admin/api/v1/auth/login")"
token="$(printf '%s' "$login_response" | jq -r '.data.accessToken // empty')"
if [[ -z "$token" ]]; then
  echo "login_failed=$login_response"
  exit 1
fi
echo "login_success=true"

health_response="$(curl -fsS -H "Authorization: Bearer ${token}" "http://127.0.0.1:${PORT}/admin/api/v1/health")"
echo "health=$(printf '%s' "$health_response" | jq -c '{success, status: .data.status, components: (.data.components | keys)}')"

flyway_count="$(mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -N -e 'SELECT COUNT(*) FROM flyway_schema_history')"
table_count="$(mysql -u"$DB_USER" -p"$DB_PASSWORD" "$DB_NAME" -N -e 'SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()')"
echo "flyway_migrations=${flyway_count}"
echo "table_count=${table_count}"
