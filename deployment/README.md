# Private Domain Assistant production deployment

This bundle is for a clean server. It does not include local database data, upload files, logs, build output, or credentials.

## Install

1. Install Docker Engine and Docker Compose v2 on Ubuntu 22.04+.
2. Extract the bundle and enter its root directory.
3. Copy `deployment/.env.example` to `deployment/.env` and replace all `CHANGE_ME` values. Keep `SYSTEM_JWT_SECRET` at least 64 characters.
   `APP_CORS_ALLOWED_ORIGINS` is optional; the configured `PDA_DOMAIN` is added automatically. If users also open the service by a server IP or another port, add each exact origin as a comma-separated value.
4. Start the complete stack:

```sh
docker compose --env-file deployment/.env -f deployment/docker-compose.yml up -d --build
```

5. Open `http://SERVER_IP/` (or the configured domain). The first account is `admin` / `admin123`.

The first login must be followed by changing the initial password in the account management page. Configure WeCom, AI, Smart Sheets, and the HTTPS certificate after the browser login. Database, Redis, and uploads are persistent named volumes and are not exposed to the public network.

## Verify

```sh
docker compose --env-file deployment/.env -f deployment/docker-compose.yml ps
curl -fsS http://127.0.0.1/ | head
curl -fsS http://127.0.0.1/api/v1/auth/config
# Verify the browser origin used by the deployment (replace the origin as needed).
curl -i -X OPTIONS http://127.0.0.1/admin/api/v1/auth/login \
  -H 'Origin: http://SERVER_IP' \
  -H 'Access-Control-Request-Method: POST'
```

Back up the `pda_db`, `pda_redis`, and `pda_uploads` volumes before upgrades.
