#!/usr/bin/env bash
set -euo pipefail

if [[ "${EUID}" -ne 0 ]]; then
  echo "run_as_root=true required"
  exit 1
fi

ENV_FILE="/opt/private-domain-assistant/config/production.env"
CREDENTIAL_FILE="/root/private-domain-assistant-initial-admin.txt"
DB_NAME="private_domain_assistant_prod"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "missing_environment_file=$ENV_FILE"
  exit 2
fi

if [[ -e "$CREDENTIAL_FILE" ]]; then
  echo "initial_admin_credentials_already_exist=$CREDENTIAL_FILE"
  exit 3
fi

apt-get update >/dev/null
apt-get install -y apache2-utils >/dev/null

JWT_SECRET="$(sed -n 's/^SYSTEM_JWT_SECRET=//p' "$ENV_FILE")"
if [[ ${#JWT_SECRET} -lt 64 ]]; then
  echo "invalid_jwt_secret=true"
  exit 4
fi

ADMIN_PASSWORD="$(openssl rand -hex 16)"
ADMIN_HASH="$(printf '%s\n' "$ADMIN_PASSWORD" | htpasswd -niBC 12 admin | cut -d: -f2- | tr -d '\n')"

mariadb --protocol=socket "$DB_NAME" <<SQL
UPDATE system_configs
SET config_value = '${JWT_SECRET}'
WHERE config_key = 'system.jwt_secret';

UPDATE accounts
SET password_hash = '${ADMIN_HASH}',
    updated_at = NOW()
WHERE username = 'admin';
SQL

umask 077
cat > "$CREDENTIAL_FILE" <<EOF
username=admin
password=${ADMIN_PASSWORD}
EOF
chmod 0600 "$CREDENTIAL_FILE"

PLAIN_COUNT="$(mariadb --protocol=socket -N "$DB_NAME" -e "SELECT COUNT(*) FROM accounts WHERE password_hash LIKE '{plain}%';")"
DEFAULT_SECRET_COUNT="$(mariadb --protocol=socket -N "$DB_NAME" -e "SELECT COUNT(*) FROM system_configs WHERE config_key='system.jwt_secret' AND config_value='change-me-in-production-private-domain-assistant';")"

if [[ "$PLAIN_COUNT" != "0" || "$DEFAULT_SECRET_COUNT" != "0" ]]; then
  echo "production_security_validation_failed=true"
  exit 5
fi

echo "production_security_configured=true"
echo "plain_password_count=$PLAIN_COUNT"
echo "default_jwt_secret_count=$DEFAULT_SECRET_COUNT"
echo "initial_admin_credentials_file=$CREDENTIAL_FILE"
