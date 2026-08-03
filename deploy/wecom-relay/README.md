# WeCom Fixed-Egress Relay

This service accepts only signed Smart Table operations on `POST /v1/wecom/api`. It binds to `127.0.0.1:18081`; Nginx supplies the public HTTPS endpoint. It is not a general HTTP proxy.

## Files and Secrets

Copy `wecom_relay.py` and `private-domain-assistant-wecom-relay.service` to the ECS. Create the service user and directories once:

```bash
sudo useradd --system --no-create-home --shell /usr/sbin/nologin pda-relay
sudo install -d -o pda-relay -g pda-relay -m 0750 /opt/private-domain-assistant/wecom-relay
sudo install -d -o root -g pda-relay -m 0750 /etc/private-domain-assistant
sudo install -o pda-relay -g pda-relay -m 0750 wecom_relay.py /opt/private-domain-assistant/wecom-relay/wecom_relay.py
sudo install -o root -g root -m 0644 private-domain-assistant-wecom-relay.service /etc/systemd/system/private-domain-assistant-wecom-relay.service
```

Create `/etc/private-domain-assistant/wecom-relay.env` on the ECS with owner `root:pda-relay` and mode `0640`. Never commit this file or paste its values into logs:

```ini
WECOM_RELAY_KEY_ID=your-local-system-key-id
WECOM_RELAY_SECRET=generate-a-long-random-secret
WECOM_CORP_ID=your-wecom-corp-id
WECOM_APP_SECRET=your-wecom-app-secret
WECOM_API_BASE_URL=https://qyapi.weixin.qq.com
WECOM_RELAY_PORT=18081
```

The local backend must use the same key ID and secret, plus the public HTTPS base URL:

```text
WECOM_TRANSPORT_MODE=RELAY
WECOM_RELAY_BASE_URL=https://your-existing-domain
WECOM_RELAY_KEY_ID=your-local-system-key-id
WECOM_RELAY_SECRET=generate-a-long-random-secret
```

For the backend already deployed to the ECS, set `WECOM_TRANSPORT_MODE=DIRECT`. No source rollback is needed.

## Nginx and Service Activation

Copy the contents of `nginx-wecom-relay.conf` into the existing HTTPS `server` block for the project domain. Do not replace the existing `/` or `/ws/` locations. Then validate and activate:

```bash
sudo nginx -t
sudo systemctl daemon-reload
sudo systemctl enable --now private-domain-assistant-wecom-relay
sudo systemctl status private-domain-assistant-wecom-relay --no-pager
```

The relay health endpoint is loopback-only:

```bash
curl --fail http://127.0.0.1:18081/health
```

Before a production Smart Table write, first send a signed read-only `get_fields` request through the public Nginx route and confirm the returned `errcode` is `0`.

## Rollback

Remove only the exact Nginx location above, validate with `sudo nginx -t`, and reload Nginx. Then stop and disable only this relay service:

```bash
sudo systemctl disable --now private-domain-assistant-wecom-relay
sudo rm /etc/systemd/system/private-domain-assistant-wecom-relay.service
sudo systemctl daemon-reload
```

Leave the existing backend service, Nginx application routes, and its environment file unchanged.
