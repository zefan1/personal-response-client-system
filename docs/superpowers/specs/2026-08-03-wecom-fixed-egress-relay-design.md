# WeCom Fixed-Egress Relay Design

## Goal

Allow the local private-domain assistant to call WeCom Smart Table APIs through the existing Alibaba Cloud ECS. WeCom therefore sees the ECS public egress IP instead of the changing local IP. A deployed server instance can call WeCom directly without reverting code.

## Scope

- Add an explicit WeCom transport mode: `DIRECT` or `RELAY`.
- In `RELAY` mode, send only approved Smart Table operations to `POST /v1/wecom/api` on the ECS domain.
- Authenticate every local-to-relay request with HMAC-SHA256 over the exact UTF-8 JSON request body.
- Run a small relay process on the ECS behind the existing Nginx configuration.
- Keep WeCom application credentials only on the ECS for relay mode.
- Preserve the existing immediate retry, durable `pending_table_writes` queue, and read-back behavior.

Out of scope:

- Changing the WeCom trusted IP list other than retaining the ECS egress IP already configured there.
- Converting a phone-less customer into a formal Smart Table row. The current `TABLE_WRITE_BLOCKED` path remains local and makes no relay request.
- Building a general HTTP proxy, changing unrelated document APIs, or moving the system database to the ECS.

## Architecture

```text
Local backend (RELAY mode)
  -> HMAC-authenticated HTTPS request
ECS relay at /v1/wecom/api
  -> fixed ECS public egress IP
WeCom Smart Table API

Deployed backend (DIRECT mode)
  -> WeCom Smart Table API
```

The deployed mode is configuration only. No source rollback is required:

- Local testing: `WECOM_TRANSPORT_MODE=RELAY`.
- Server deployment: `WECOM_TRANSPORT_MODE=DIRECT`.

## Local Backend Design

`WecomTableClient` remains the boundary used by write, retry, read, and field-discovery code. A relay implementation will be selected only when `WECOM_TRANSPORT_MODE=RELAY`; the existing official direct client remains the default.

New relay settings are environment variables and are never saved to the database or committed:

- `WECOM_RELAY_BASE_URL`
- `WECOM_RELAY_KEY_ID`
- `WECOM_RELAY_SECRET`

The local request has this shape:

```json
{
  "operation": "add_records",
  "payload": {},
  "requestId": "uuid"
}
```

Headers are `X-Relay-Key-Id`, `X-Relay-Timestamp`, `X-Relay-Nonce`, `X-Relay-Request-Id`, and `X-Relay-Signature`. The signing text is exactly `timestamp + "." + nonce + "." + rawBody`.

No `corpId`, app secret, access token, phone number, or full Smart Table URL is emitted to application logs. A relay error is normalized into the existing WeCom exception path so normal retry semantics continue to apply.

## ECS Relay Design

The relay is a small process bound to loopback. Existing Nginx exposes only `/v1/wecom/api` over HTTPS and forwards to that process. The existing project reverse-proxy route remains unchanged.

The relay:

1. Rejects requests with missing, expired, malformed, or invalid HMAC signatures using `401`.
2. Rejects paths and operations outside the project allowlist using `400`.
3. Obtains and caches the WeCom access token from ECS-held environment variables.
4. Calls the approved WeCom endpoint from the ECS public IP.
5. Returns a sanitized status, WeCom `errcode`, and response data required by the client.

Initial allowed operations match the current project client:

- `get_fields`
- `get_records`
- `add_records`
- `update_records`
- `get_sheet`
- `get_views`
- `add_fields`
- `update_fields`

The token endpoint is internal to the relay and is not remotely callable. Configuration deployment merges the new relay variables with existing ECS variables; it must not overwrite existing services.

## Phone-Less Behavior

A relay only changes network egress. It cannot make an empty value valid for the configured Smart Table unique field. `NewCustomerRowCreator` continues to stop an empty-phone new-row request locally with `TABLE_WRITE_BLOCKED`; this request must not enter the retry queue or reach the relay.

The UI and API error path will identify this as "missing formal customer identity" rather than a relay or trusted-IP failure. Introducing a verified WeChat ID as a future unique identity is a separate product decision.

## Testing And Acceptance

Unit tests are written first for:

- Direct mode preserving the existing client behavior.
- Relay mode signing the exact serialized request and forwarding the selected operation.
- Invalid local relay configuration failing before a network call.
- Phone-less writes remaining locally blocked with zero relay calls.
- Relay rejection and WeCom failures reaching the existing retry queue only when retryable.

ECS acceptance is ordered:

1. Health endpoint returns successfully.
2. Unsigned relay request returns `401`.
3. Unsupported operation returns `400`.
4. A correctly signed low-risk read succeeds through ECS.
5. A controlled Smart Table write and subsequent read-back succeed.
6. The server's actual egress IP matches the trusted IP retained in WeCom.
7. Existing project API routes and any pre-existing ECS service remain available.
