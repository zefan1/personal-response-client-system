#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "run_as_root=true required"
  exit 1
fi

ENV_FILE="/opt/private-domain-assistant/config/production.env"
DB_NAME="private_domain_assistant_prod"
DB_USER="pda_prod"

if [[ -e "$ENV_FILE" ]]; then
  echo "production_env_already_exists=$ENV_FILE"
  exit 2
fi

DB_PASSWORD="$(openssl rand -hex 24)"
JWT_SECRET="$(openssl rand -hex 48)"

mariadb --protocol=socket <<SQL
CREATE DATABASE IF NOT EXISTS ${DB_NAME}
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
ALTER USER '${DB_USER}'@'127.0.0.1' IDENTIFIED BY '${DB_PASSWORD}';
GRANT ALL PRIVILEGES ON ${DB_NAME}.* TO '${DB_USER}'@'127.0.0.1';
FLUSH PRIVILEGES;
SQL

install -d -o root -g root -m 0700 "$(dirname "$ENV_FILE")"
umask 077
cat > "$ENV_FILE" <<EOF
APP_ENV=production
MOCK_EXTERNALS=false
SERVER_PORT=8080

SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/${DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false
SPRING_DATASOURCE_USERNAME=${DB_USER}
SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}

REDIS_HOST=127.0.0.1
REDIS_PORT=6379

# Fill these values before starting the application. Production uses the Relay
# so the enterprise WeCom application secret stays on the Relay server.
WECOM_TRANSPORT_MODE=RELAY
WECOM_RELAY_BASE_URL=
WECOM_RELAY_KEY_ID=
WECOM_RELAY_SECRET=
WECOM_SMARTSHEET_DOC_ID=
WECOM_SMARTSHEET_SHEET_ID=
WECOM_SMARTSHEET_VIEW_ID=
WECOM_SMARTSHEET_SOURCE_TABLE=
WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE=

SYSTEM_JWT_SECRET=${JWT_SECRET}

VERSION_STORAGE_ROOT=/data/private-domain-assistant/uploads/desktop-releases
VERSION_PUBLIC_BASE_URL=/downloads/desktop-releases

JAVA_OPTS=-Xms256m -Xmx1024m -XX:+UseG1GC
EOF

chown root:root "$ENV_FILE"
chmod 0600 "$ENV_FILE"

echo "production_runtime_provisioned=true"
echo "database=$DB_NAME"
echo "database_user=$DB_USER"
echo "environment_file=$ENV_FILE"
