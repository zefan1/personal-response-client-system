#!/usr/bin/env python3
"""Authenticated, allowlisted WeCom Smart Table relay for fixed ECS egress."""

from __future__ import annotations

import hashlib
import hmac
import json
import os
import re
import time
import urllib.parse
import urllib.request
from dataclasses import dataclass
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from threading import Lock
from typing import Callable, Mapping


ALLOWED_OPERATIONS = {
    "get_fields": "/cgi-bin/wedoc/smartsheet/get_fields",
    "get_records": "/cgi-bin/wedoc/smartsheet/get_records",
    "add_records": "/cgi-bin/wedoc/smartsheet/add_records",
    "update_records": "/cgi-bin/wedoc/smartsheet/update_records",
    "get_sheet": "/cgi-bin/wedoc/smartsheet/get_sheet",
    "get_views": "/cgi-bin/wedoc/smartsheet/get_views",
    "add_fields": "/cgi-bin/wedoc/smartsheet/add_fields",
    "update_fields": "/cgi-bin/wedoc/smartsheet/update_fields",
}
MAX_BODY_BYTES = 2 * 1024 * 1024
MAX_CLOCK_SKEW_SECONDS = 300
MAX_TRACKED_NONCES = 10_000
NONCE_PATTERN = re.compile(r"[0-9a-fA-F-]{16,80}\Z")
SIGNATURE_PATTERN = re.compile(r"[0-9a-fA-F]{64}\Z")
TIMESTAMP_PATTERN = re.compile(r"\d{10}\Z")


@dataclass(frozen=True)
class RelaySettings:
    key_id: str
    relay_secret: str
    corp_id: str
    app_secret: str
    api_base_url: str

    @classmethod
    def from_environ(cls) -> "RelaySettings":
        values = {
            "key_id": os.environ.get("WECOM_RELAY_KEY_ID", "").strip(),
            "relay_secret": os.environ.get("WECOM_RELAY_SECRET", "").strip(),
            "corp_id": os.environ.get("WECOM_CORP_ID", "").strip(),
            "app_secret": os.environ.get("WECOM_APP_SECRET", "").strip(),
            "api_base_url": os.environ.get("WECOM_API_BASE_URL", "https://qyapi.weixin.qq.com").strip().rstrip("/"),
        }
        environment_names = {
            "key_id": "WECOM_RELAY_KEY_ID",
            "relay_secret": "WECOM_RELAY_SECRET",
            "corp_id": "WECOM_CORP_ID",
            "app_secret": "WECOM_APP_SECRET",
            "api_base_url": "WECOM_API_BASE_URL",
        }
        missing = [environment_names[key] for key, value in values.items() if not value]
        if missing:
            raise ValueError("Missing required environment variables: " + ", ".join(missing))
        return cls(**values)


@dataclass(frozen=True)
class RelayResponse:
    status: int
    data: dict


class RelayApplication:
    def __init__(
        self,
        settings: RelaySettings,
        now: Callable[[], float] = time.time,
        opener: Callable[..., object] = urllib.request.urlopen,
    ) -> None:
        self._settings = settings
        self._now = now
        self._opener = opener
        self._token_lock = Lock()
        self._token = ""
        self._token_expires_at = 0.0
        self._nonce_lock = Lock()
        self._used_nonces: dict[str, int] = {}

    def handle(self, headers: Mapping[str, str], raw_body: bytes) -> RelayResponse:
        if not self._signature_valid(headers, raw_body):
            return self._failure(401, "relay authentication failed")
        if len(raw_body) > MAX_BODY_BYTES:
            return self._failure(413, "request body is too large")
        try:
            request = json.loads(raw_body.decode("utf-8"))
        except (UnicodeDecodeError, json.JSONDecodeError):
            return self._failure(400, "request body must be a JSON object")
        if not isinstance(request, dict):
            return self._failure(400, "request body must be a JSON object")
        operation = request.get("operation")
        payload = request.get("payload")
        if operation not in ALLOWED_OPERATIONS or not isinstance(payload, dict):
            return self._failure(400, "unsupported relay operation")
        if not self._request_id_matches(headers, request.get("requestId")):
            return self._failure(401, "relay authentication failed")
        return self._forward(operation, payload)

    def _signature_valid(self, headers: Mapping[str, str], raw_body: bytes) -> bool:
        normalized = {str(key).lower(): str(value).strip() for key, value in headers.items()}
        key_id = normalized.get("x-relay-key-id", "")
        timestamp = normalized.get("x-relay-timestamp", "")
        nonce = normalized.get("x-relay-nonce", "")
        signature = normalized.get("x-relay-signature", "")
        if key_id != self._settings.key_id:
            return False
        if not TIMESTAMP_PATTERN.fullmatch(timestamp) or not NONCE_PATTERN.fullmatch(nonce):
            return False
        if not SIGNATURE_PATTERN.fullmatch(signature):
            return False
        if abs(int(self._now()) - int(timestamp)) > MAX_CLOCK_SKEW_SECONDS:
            return False
        signed = timestamp.encode("ascii") + b"." + nonce.encode("ascii") + b"." + raw_body
        expected = hmac.new(self._settings.relay_secret.encode("utf-8"), signed, hashlib.sha256).hexdigest()
        if not hmac.compare_digest(expected, signature.lower()):
            return False
        return self._register_nonce(nonce, int(timestamp) + MAX_CLOCK_SKEW_SECONDS)

    def _register_nonce(self, nonce: str, expires_at: int) -> bool:
        now = int(self._now())
        with self._nonce_lock:
            self._used_nonces = {
                value: expiry for value, expiry in self._used_nonces.items() if expiry >= now
            }
            if nonce in self._used_nonces or len(self._used_nonces) >= MAX_TRACKED_NONCES:
                return False
            self._used_nonces[nonce] = expires_at
            return True

    def _request_id_matches(self, headers: Mapping[str, str], request_id: object) -> bool:
        normalized = {str(key).lower(): str(value).strip() for key, value in headers.items()}
        return isinstance(request_id, str) and request_id and normalized.get("x-relay-request-id") == request_id

    def _forward(self, operation: str, payload: dict) -> RelayResponse:
        try:
            token = self._access_token()
            url = self._settings.api_base_url + ALLOWED_OPERATIONS[operation]
            url += "?access_token=" + urllib.parse.quote(token, safe="")
            body = json.dumps(payload, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
            request = urllib.request.Request(url, data=body, method="POST", headers={"Content-Type": "application/json"})
            with self._opener(request, timeout=30) as response:
                result = json.loads(response.read().decode("utf-8"))
            if not isinstance(result, dict):
                return self._failure(502, "relay upstream response was invalid")
            return RelayResponse(200, result)
        except Exception:
            return self._failure(502, "relay upstream request failed")

    def _access_token(self) -> str:
        with self._token_lock:
            if self._token and self._token_expires_at - self._now() > 300:
                return self._token
            query = urllib.parse.urlencode({"corpid": self._settings.corp_id, "corpsecret": self._settings.app_secret})
            request = urllib.request.Request(self._settings.api_base_url + "/cgi-bin/gettoken?" + query, method="GET")
            with self._opener(request, timeout=10) as response:
                result = json.loads(response.read().decode("utf-8"))
            token = result.get("access_token") if isinstance(result, dict) else None
            expiry = result.get("expires_in") if isinstance(result, dict) else None
            if not isinstance(token, str) or not token or not isinstance(expiry, int) or expiry <= 0:
                raise ValueError("token response was invalid")
            self._token = token
            self._token_expires_at = self._now() + expiry
            return token

    @staticmethod
    def _failure(status: int, message: str) -> RelayResponse:
        return RelayResponse(status, {"errcode": -1, "errmsg": message})


class RelayRequestHandler(BaseHTTPRequestHandler):
    application: RelayApplication
    server_version = "WecomRelay"
    sys_version = ""

    def do_POST(self) -> None:
        if self.path != "/v1/wecom/api":
            self._send(RelayResponse(404, {"errcode": -1, "errmsg": "not found"}))
            return
        try:
            length = int(self.headers.get("Content-Length", "-1"))
        except ValueError:
            length = -1
        if length < 0 or length > MAX_BODY_BYTES:
            self._send(RelayResponse(413, {"errcode": -1, "errmsg": "request body is too large"}))
            return
        self._send(self.application.handle(dict(self.headers.items()), self.rfile.read(length)))

    def do_GET(self) -> None:
        if self.path == "/health":
            self._send(RelayResponse(200, {"ok": True}))
            return
        self._send(RelayResponse(404, {"errcode": -1, "errmsg": "not found"}))

    def _send(self, response: RelayResponse) -> None:
        raw = json.dumps(response.data, ensure_ascii=False, separators=(",", ":")).encode("utf-8")
        self.send_response(response.status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(raw)))
        self.end_headers()
        self.wfile.write(raw)

    def log_message(self, format: str, *args: object) -> None:
        return


def main() -> None:
    settings = RelaySettings.from_environ()
    RelayRequestHandler.application = RelayApplication(settings)
    port = int(os.environ.get("WECOM_RELAY_PORT", "18081"))
    server = ThreadingHTTPServer(("127.0.0.1", port), RelayRequestHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
