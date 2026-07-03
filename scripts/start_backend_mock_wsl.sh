#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOG_DIR="$ROOT_DIR/.tools/runtime"
LOG_FILE="$LOG_DIR/backend.log"
PID_FILE="$LOG_DIR/backend.pid"
DB_NAME="${SMOKE_DB_NAME:-private_domain_assistant_smoke}"
DB_USER="${SMOKE_DB_USER:-pda_smoke}"
DB_PASSWORD="${SMOKE_DB_PASSWORD:-pda_smoke_pwd}"
PORT="${SMOKE_PORT:-8080}"

mkdir -p "$LOG_DIR"

if [[ -f "$PID_FILE" ]]; then
  old_pid="$(cat "$PID_FILE" || true)"
  if [[ -n "$old_pid" ]] && kill -0 "$old_pid" 2>/dev/null; then
    echo "backend_already_running pid=${old_pid} url=http://127.0.0.1:${PORT}"
    exit 0
  fi
fi

sudo service mariadb start >/dev/null 2>&1 || sudo /etc/init.d/mariadb start >/dev/null 2>&1
sudo service redis-server start >/dev/null 2>&1 || sudo /etc/init.d/redis-server start >/dev/null 2>&1

mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'localhost' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL

cd "$ROOT_DIR"
rm -f "$LOG_FILE"

SPRING_DATASOURCE_URL="jdbc:mysql://localhost:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false" \
SPRING_DATASOURCE_USERNAME="$DB_USER" \
SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
MOCK_EXTERNALS=true \
SERVER_PORT="$PORT" \
MAVEN_OPTS="-Dstyle.color=never" \
nohup mvn -Dstyle.color=never org.springframework.boot:spring-boot-maven-plugin:3.3.7:run >"$LOG_FILE" 2>&1 &
echo "$!" > "$PID_FILE"

for _ in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${PORT}/api/v1/auth/config" >/tmp/pda_auth_config.json 2>/tmp/pda_curl_err; then
    echo "backend_ready pid=$(cat "$PID_FILE") url=http://127.0.0.1:${PORT}"
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
