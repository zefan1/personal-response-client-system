#!/usr/bin/env bash
set -euo pipefail

# First-run preparation for a clean Ubuntu 24.04 server.
# Run from Alibaba Cloud Workbench as root. This script does not create
# production database credentials or write provider/API keys.

if [[ "${EUID}" -ne 0 ]]; then
  echo "run_as_root=true required"
  exit 1
fi

SSH_PORT="${SSH_PORT:-22}"
APP_ROOT="/opt/private-domain-assistant"
DATA_ROOT="/data/private-domain-assistant"

export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get full-upgrade -y
apt-get install -y \
  ca-certificates curl rsync unzip \
  openjdk-17-jre-headless \
  mariadb-server redis-server nginx certbot python3-certbot-nginx \
  fail2ban ufw logrotate

if ! id pda >/dev/null 2>&1; then
  useradd --system --home-dir "$APP_ROOT" --shell /usr/sbin/nologin pda
fi

install -d -o pda -g pda -m 0750 \
  "$APP_ROOT/app" \
  "$APP_ROOT/config" \
  "$DATA_ROOT/uploads/desktop-releases" \
  "$DATA_ROOT/uploads/quick-search" \
  "$DATA_ROOT/logs" \
  "$DATA_ROOT/backups/mysql"

if ! swapon --show | grep -q .; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  if ! grep -q '^/swapfile ' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' >> /etc/fstab
  fi
fi

# Keep database and Redis private. The application connects locally.
systemctl enable --now mariadb redis-server nginx fail2ban

ufw allow "${SSH_PORT}/tcp"
ufw allow 80/tcp
ufw allow 443/tcp
ufw --force enable

cat <<'SUMMARY'
bootstrap_completed=true
next_steps=
1. Confirm the cloud security group allows SSH on the selected port and HTTP/HTTPS.
2. Create private_domain_assistant_prod and pda_prod in MariaDB.
3. Copy production.env.example, fill secrets on the server, and install the systemd unit.
4. Point the domain A record to this server, then run certbot for HTTPS.
SUMMARY

java -version 2>&1 | head -n 1
systemctl --no-pager --full status mariadb redis-server nginx fail2ban | sed -n '1,80p'
ufw status verbose
