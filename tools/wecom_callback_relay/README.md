# WeCom Callback Relay

This is a small server-side bridge for the development phase. It receives the public WeCom
application callback, verifies and decrypts it, and stores only supported event metadata in SQLite.

It is not the current Smart Sheet synchronization mechanism. The configured application callback
event list does not include Smart Sheet row changes, so Smart Sheet additions and edits are detected
by the backend's scheduled incremental reads instead. The relay remains available for future,
officially documented callback events.

Required server environment variables:

```text
WECOM_CALLBACK_TOKEN=
WECOM_CALLBACK_ENCODING_AES_KEY=
WECOM_CORP_ID=
WECOM_INBOUND_RELAY_CLIENT_ID=
WECOM_INBOUND_RELAY_CLIENT_SECRET=
WECOM_CALLBACK_RELAY_DB=/var/lib/wecom-callback-relay/events.db
WECOM_CALLBACK_RELAY_HOST=127.0.0.1
WECOM_CALLBACK_RELAY_PORT=18082
```

The relay can be started before the WeCom callback values are created. Until `WECOM_CALLBACK_TOKEN`,
`WECOM_CALLBACK_ENCODING_AES_KEY`, and `WECOM_CORP_ID` are all set, its public callback endpoint returns
HTTP 503 and accepts no callback events. The authenticated internal queue remains available for a safe
installation check.

The public callback URL is `https://sy.xn--15tq51d.top/wecom/smartsheet/callback`.
The authenticated pull endpoints are `POST /wecom/smartsheet/internal/v1/events/claim` and
`POST /wecom/smartsheet/internal/v1/events/ack`; they are forwarded only to this relay and require
the signed internal client headers.
When the main project moves to the same server, it continues to claim the queue over `127.0.0.1`;
only its relay base URL changes from the public domain to the local loopback address.
