#!/usr/bin/env python3
"""Durable relay for supported WeCom application callbacks.

Current WeCom application callbacks do not include Smart Sheet row changes. Smart Sheet inbound
sync therefore uses scheduled incremental reads; this relay only reserves the transport for a
future, officially documented callback event and never stores customer cell values.
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import secrets
import sqlite3
import struct
import threading
import time
import xml.etree.ElementTree as element_tree
from dataclasses import dataclass
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any
from urllib.parse import parse_qs, urlparse

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes


PUBLIC_PATH = "/wecom/smartsheet/callback"
CLAIM_PATH = "/wecom/smartsheet/internal/v1/events/claim"
ACK_PATH = "/wecom/smartsheet/internal/v1/events/ack"
MAX_BODY_BYTES = 262_144
LEASE_SECONDS = 90
AUTH_WINDOW_SECONDS = 120


def text(value: str | None) -> str:
    return "" if value is None else value.strip()


@dataclass(frozen=True)
class Config:
    callback_token: str
    callback_encoding_aes_key: str
    corp_id: str
    client_id: str
    client_secret: str
    database: Path
    host: str = "127.0.0.1"
    port: int = 18082

    @classmethod
    def from_environment(cls) -> "Config":
        config = cls(
            text(os.getenv("WECOM_CALLBACK_TOKEN")),
            text(os.getenv("WECOM_CALLBACK_ENCODING_AES_KEY")),
            text(os.getenv("WECOM_CORP_ID")),
            text(os.getenv("WECOM_INBOUND_RELAY_CLIENT_ID")),
            text(os.getenv("WECOM_INBOUND_RELAY_CLIENT_SECRET")),
            Path(text(os.getenv("WECOM_CALLBACK_RELAY_DB")) or "/var/lib/wecom-callback-relay/events.db"),
            text(os.getenv("WECOM_CALLBACK_RELAY_HOST")) or "127.0.0.1",
            int(text(os.getenv("WECOM_CALLBACK_RELAY_PORT")) or "18082"),
        )
        config.validate()
        return config

    def validate(self) -> None:
        if self.callback_configured() and len(self.callback_encoding_aes_key) != 43:
            raise ValueError("WeCom encoding AES key was invalid")
        if self.callback_configured() and not self.corp_id:
            raise ValueError("WeCom callback configuration is incomplete")
        if not self.client_id or len(self.client_secret) < 32:
            raise ValueError("inbound relay client authentication is incomplete")

    def callback_configured(self) -> bool:
        return bool(self.callback_token and self.callback_encoding_aes_key and self.corp_id)


class WeComCrypto:
    def __init__(self, config: Config):
        self._token = config.callback_token
        self._corp_id = config.corp_id
        self._key = base64.b64decode(config.callback_encoding_aes_key + "=")
        if len(self._key) != 32:
            raise ValueError("WeCom encoding AES key was invalid")

    def decrypt(self, signature: str, timestamp: str, nonce: str, encrypted: str) -> str:
        expected = self.signature(timestamp, nonce, encrypted)
        if not all((signature, timestamp, nonce, encrypted)) or not hmac.compare_digest(signature, expected):
            raise ValueError("callback signature was invalid")
        try:
            cipher = Cipher(algorithms.AES(self._key), modes.CBC(self._key[:16]))
            padded = cipher.decryptor().update(base64.b64decode(encrypted))
            plain = self._unpad(padded)
            if len(plain) < 20:
                raise ValueError("callback payload was invalid")
            length = struct.unpack(">I", plain[16:20])[0]
            end = 20 + length
            if end > len(plain):
                raise ValueError("callback payload was invalid")
            if not hmac.compare_digest(plain[end:].decode("utf-8"), self._corp_id):
                raise ValueError("callback corporate identity was invalid")
            return plain[20:end].decode("utf-8")
        except (ValueError, UnicodeDecodeError, struct.error) as error:
            raise ValueError("callback payload was invalid") from error

    def signature(self, timestamp: str, nonce: str, encrypted: str) -> str:
        source = "".join(sorted((self._token, timestamp, nonce, encrypted)))
        return hashlib.sha1(source.encode("utf-8")).hexdigest()

    @staticmethod
    def _unpad(value: bytes) -> bytes:
        if not value:
            raise ValueError("callback payload was invalid")
        padding = value[-1]
        if padding < 1 or padding > 32 or value[-padding:] != bytes([padding]) * padding:
            raise ValueError("callback payload was invalid")
        return value[:-padding]


class EventStore:
    def __init__(self, database: Path):
        database.parent.mkdir(parents=True, exist_ok=True)
        self._connection = sqlite3.connect(database, check_same_thread=False)
        self._connection.row_factory = sqlite3.Row
        self._lock = threading.Lock()
        with self._connection:
            self._connection.executescript("""
              PRAGMA journal_mode=WAL;
              CREATE TABLE IF NOT EXISTS callback_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                event_key TEXT NOT NULL UNIQUE,
                document_id TEXT NOT NULL,
                sheet_id TEXT NOT NULL,
                change_type TEXT NOT NULL,
                record_ids_json TEXT NOT NULL,
                operator_name TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'PENDING',
                lease_token TEXT,
                lease_until INTEGER,
                attempts INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                resolved_at INTEGER
              );
              CREATE INDEX IF NOT EXISTS callback_events_claim_idx
                ON callback_events(status, lease_until, id);
              CREATE TABLE IF NOT EXISTS used_auth_nonces (
                nonce TEXT PRIMARY KEY,
                expires_at INTEGER NOT NULL
              );
            """)

    def enqueue(self, event: dict[str, Any]) -> None:
        with self._lock, self._connection:
            self._connection.execute("""
                INSERT INTO callback_events
                  (event_key, document_id, sheet_id, change_type, record_ids_json, operator_name, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(event_key) DO NOTHING
                """, (
                    event["event_key"], event["document_id"], event["sheet_id"], event["change_type"],
                    json.dumps(event["record_ids"], separators=(",", ":")), event["operator_name"], int(time.time()),
                ))

    def close(self) -> None:
        with self._lock:
            self._connection.close()

    def claim(self, limit: int) -> list[dict[str, Any]]:
        now = int(time.time())
        lease_until = now + LEASE_SECONDS
        with self._lock, self._connection:
            self._connection.execute("""
                UPDATE callback_events SET status = 'PENDING', lease_token = NULL, lease_until = NULL
                WHERE status = 'LEASED' AND lease_until < ?
                """, (now,))
            rows = self._connection.execute("""
                SELECT id, event_key, document_id, sheet_id, change_type, record_ids_json, operator_name
                FROM callback_events WHERE status = 'PENDING' ORDER BY id ASC LIMIT ?
                """, (limit,)).fetchall()
            events: list[dict[str, Any]] = []
            for row in rows:
                lease_token = secrets.token_urlsafe(24)
                changed = self._connection.execute("""
                    UPDATE callback_events SET status = 'LEASED', lease_token = ?, lease_until = ?, attempts = attempts + 1
                    WHERE id = ? AND status = 'PENDING'
                    """, (lease_token, lease_until, row["id"])).rowcount
                if changed:
                    events.append({
                        "id": row["id"], "event_key": row["event_key"], "document_id": row["document_id"],
                        "sheet_id": row["sheet_id"], "change_type": row["change_type"],
                        "record_ids": json.loads(row["record_ids_json"]), "operator_name": row["operator_name"],
                        "lease_token": lease_token,
                    })
            return events

    def acknowledge(self, event_id: int, lease_token: str) -> bool:
        with self._lock, self._connection:
            return self._connection.execute("""
                UPDATE callback_events SET status = 'RESOLVED', resolved_at = ?, lease_token = NULL, lease_until = NULL
                WHERE id = ? AND status = 'LEASED' AND lease_token = ?
                """, (int(time.time()), event_id, lease_token)).rowcount == 1

    def accept_nonce(self, nonce: str, expires_at: int) -> bool:
        now = int(time.time())
        with self._lock, self._connection:
            self._connection.execute("DELETE FROM used_auth_nonces WHERE expires_at < ?", (now,))
            try:
                self._connection.execute("INSERT INTO used_auth_nonces (nonce, expires_at) VALUES (?, ?)", (nonce, expires_at))
                return True
            except sqlite3.IntegrityError:
                return False


class CallbackRelay:
    def __init__(self, config: Config, store: EventStore):
        self.config = config
        self.crypto = WeComCrypto(config) if config.callback_configured() else None
        self.store = store

    def verify_challenge(self, query: dict[str, list[str]]) -> str:
        if self.crypto is None:
            raise RuntimeError("WeCom callback configuration is incomplete")
        return self.crypto.decrypt(self._one(query, "msg_signature"), self._one(query, "timestamp"),
                                   self._one(query, "nonce"), self._one(query, "echostr"))

    def receive(self, query: dict[str, list[str]], encrypted_xml: str) -> None:
        if self.crypto is None:
            raise RuntimeError("WeCom callback configuration is incomplete")
        encrypted = self._xml_values(encrypted_xml).get("Encrypt", [""])[0]
        payload = self.crypto.decrypt(self._one(query, "msg_signature"), self._one(query, "timestamp"),
                                      self._one(query, "nonce"), encrypted)
        values = self._xml_values(payload)
        if self._first(values, "MsgType") != "event" or self._first(values, "Event") != "smart_sheet_change":
            return
        change_type = self._first(values, "ChangeType")
        if change_type not in {"add_record", "update_record", "delete_record"}:
            return
        record_ids = sorted({value for value in values.get("RecordId", []) if value})
        document_id, sheet_id = self._first(values, "DocId"), self._first(values, "SheetId")
        if not document_id or not sheet_id or not record_ids:
            return
        created = self._first(values, "CreateTime")
        event_key = hashlib.sha256("|".join((document_id, sheet_id, change_type, created, ",".join(record_ids))).encode()).hexdigest()
        self.store.enqueue({
            "event_key": event_key, "document_id": document_id, "sheet_id": sheet_id,
            "change_type": change_type, "record_ids": record_ids,
            "operator_name": self._first(values, "FromUserName"),
        })

    def authenticate_internal(self, method: str, path: str, headers: dict[str, str], body: bytes) -> bool:
        timestamp = headers.get("X-Relay-Timestamp", "")
        nonce = headers.get("X-Relay-Nonce", "")
        key_id = headers.get("X-Relay-Key-Id", "")
        signature = headers.get("X-Relay-Signature", "")
        try:
            parsed_timestamp = int(timestamp)
        except ValueError:
            return False
        if key_id != self.config.client_id or not nonce or abs(int(time.time()) - parsed_timestamp) > AUTH_WINDOW_SECONDS:
            return False
        digest = hashlib.sha256(body).hexdigest()
        signed = "\n".join((timestamp, nonce, method.upper(), path, digest))
        expected = hmac.new(self.config.client_secret.encode(), signed.encode(), hashlib.sha256).hexdigest()
        return hmac.compare_digest(signature, expected) and self.store.accept_nonce(nonce, parsed_timestamp + AUTH_WINDOW_SECONDS)

    @staticmethod
    def _one(query: dict[str, list[str]], name: str) -> str:
        values = query.get(name, [])
        return values[0].strip() if len(values) == 1 else ""

    @staticmethod
    def _first(values: dict[str, list[str]], name: str) -> str:
        return values.get(name, [""])[0]

    @staticmethod
    def _xml_values(xml: str) -> dict[str, list[str]]:
        if len(xml.encode("utf-8")) > MAX_BODY_BYTES or "<!DOCTYPE" in xml.upper():
            raise ValueError("callback XML was invalid")
        try:
            root = element_tree.fromstring(xml)
        except element_tree.ParseError as error:
            raise ValueError("callback XML was invalid") from error
        values: dict[str, list[str]] = {}
        for child in root:
            values.setdefault(child.tag, []).append(text(child.text))
        return values


class RelayHandler(BaseHTTPRequestHandler):
    relay: CallbackRelay

    def do_GET(self) -> None:  # noqa: N802
        if urlparse(self.path).path != PUBLIC_PATH:
            self._respond(HTTPStatus.NOT_FOUND, "not found")
            return
        try:
            self._respond(HTTPStatus.OK, self.relay.verify_challenge(parse_qs(urlparse(self.path).query)))
        except RuntimeError:
            self._respond(HTTPStatus.SERVICE_UNAVAILABLE, "callback not configured")
        except ValueError:
            self._respond(HTTPStatus.FORBIDDEN, "forbidden")

    def do_POST(self) -> None:  # noqa: N802
        parsed = urlparse(self.path)
        body = self._body()
        if body is None:
            return
        if parsed.path == PUBLIC_PATH:
            try:
                self.relay.receive(parse_qs(parsed.query), body.decode("utf-8"))
                self._respond(HTTPStatus.OK, "success")
            except RuntimeError:
                self._respond(HTTPStatus.SERVICE_UNAVAILABLE, "callback not configured")
            except (UnicodeDecodeError, ValueError):
                self._respond(HTTPStatus.FORBIDDEN, "forbidden")
            return
        if parsed.path not in {CLAIM_PATH, ACK_PATH} or not self.relay.authenticate_internal("POST", parsed.path, self.headers, body):
            self._respond(HTTPStatus.FORBIDDEN, "forbidden")
            return
        try:
            request = json.loads(body or b"{}")
            if parsed.path == CLAIM_PATH:
                limit = max(1, min(100, int(request.get("limit", 100))))
                self._json(HTTPStatus.OK, {"events": self.relay.store.claim(limit)})
            else:
                accepted = self.relay.store.acknowledge(int(request["id"]), text(request["lease_token"]))
                self._json(HTTPStatus.OK, {"acknowledged": accepted})
        except (KeyError, TypeError, ValueError, json.JSONDecodeError):
            self._respond(HTTPStatus.BAD_REQUEST, "invalid request")

    def log_message(self, _format: str, *_args: Any) -> None:
        return

    def _body(self) -> bytes | None:
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            self._respond(HTTPStatus.BAD_REQUEST, "invalid request")
            return None
        if length < 0 or length > MAX_BODY_BYTES:
            self._respond(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "request too large")
            return None
        return self.rfile.read(length)

    def _respond(self, status: HTTPStatus, content: str) -> None:
        encoded = content.encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "text/plain; charset=utf-8")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)

    def _json(self, status: HTTPStatus, value: dict[str, Any]) -> None:
        encoded = json.dumps(value, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.end_headers()
        self.wfile.write(encoded)


def main() -> None:
    config = Config.from_environment()
    RelayHandler.relay = CallbackRelay(config, EventStore(config.database))
    server = ThreadingHTTPServer((config.host, config.port), RelayHandler)
    server.serve_forever()


if __name__ == "__main__":
    main()
