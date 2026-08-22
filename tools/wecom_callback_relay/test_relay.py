import base64
import hashlib
import hmac
import os
import struct
import sys
import tempfile
import unittest
from pathlib import Path

from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

sys.path.insert(0, str(Path(__file__).parent))
from relay import CallbackRelay, Config, EventStore


class CallbackRelayTest(unittest.TestCase):
    def setUp(self):
        self.key = base64.b64encode(os.urandom(32)).decode().rstrip("=")
        self.config = Config("token", self.key, "corp-1", "local", "x" * 32, Path(tempfile.mktemp()))
        self.store = EventStore(self.config.database)
        self.relay = CallbackRelay(self.config, self.store)

    def tearDown(self):
        self.store.close()
        for suffix in ("", "-wal", "-shm"):
            Path(str(self.config.database) + suffix).unlink(missing_ok=True)

    def test_callback_is_durable_and_deduplicated(self):
        event = "<xml><MsgType>event</MsgType><Event>smart_sheet_change</Event><ChangeType>add_record</ChangeType><DocId>doc</DocId><SheetId>sheet</SheetId><CreateTime>123</CreateTime><RecordId>row-b</RecordId><RecordId>row-a</RecordId></xml>"
        encrypted = self._encrypt(event)
        query = self._query(encrypted)

        self.relay.receive(query, self._encrypted_xml(encrypted))
        self.relay.receive(query, self._encrypted_xml(encrypted))

        events = self.relay.store.claim(10)
        self.assertEqual(1, len(events))
        self.assertEqual(["row-a", "row-b"], events[0]["record_ids"])
        self.assertTrue(self.relay.store.acknowledge(events[0]["id"], events[0]["lease_token"]))
        self.assertEqual([], self.relay.store.claim(10))

    def test_rejects_invalid_signature(self):
        with self.assertRaises(ValueError):
            self.relay.verify_challenge({"msg_signature": ["bad"], "timestamp": ["1"], "nonce": ["2"], "echostr": ["bad"]})

    def test_internal_queue_starts_before_wecom_callback_is_configured(self):
        config = Config("", "", "", "local", "x" * 32, Path(tempfile.mktemp()))
        store = EventStore(config.database)
        try:
            relay = CallbackRelay(config, store)
            self.assertIsNone(relay.crypto)
            with self.assertRaises(RuntimeError):
                relay.verify_challenge({})
        finally:
            store.close()
            for suffix in ("", "-wal", "-shm"):
                Path(str(config.database) + suffix).unlink(missing_ok=True)

    def test_internal_request_signature_is_single_use(self):
        body = b'{"limit":1}'
        timestamp = str(int(__import__("time").time()))
        nonce = "single-use-nonce"
        signed = "\n".join((timestamp, nonce, "POST", "/wecom/smartsheet/internal/v1/events/claim", hashlib.sha256(body).hexdigest()))
        signature = hmac.new(self.config.client_secret.encode(), signed.encode(), hashlib.sha256).hexdigest()
        headers = {
            "X-Relay-Key-Id": self.config.client_id,
            "X-Relay-Timestamp": timestamp,
            "X-Relay-Nonce": nonce,
            "X-Relay-Signature": signature,
        }

        self.assertTrue(self.relay.authenticate_internal("POST", "/wecom/smartsheet/internal/v1/events/claim", headers, body))
        self.assertFalse(self.relay.authenticate_internal("POST", "/wecom/smartsheet/internal/v1/events/claim", headers, body))

    def _query(self, encrypted):
        return {"msg_signature": [self.relay.crypto.signature("123", "nonce", encrypted)], "timestamp": ["123"], "nonce": ["nonce"]}

    @staticmethod
    def _encrypted_xml(encrypted):
        return "<xml><Encrypt><![CDATA[" + encrypted + "]]></Encrypt></xml>"

    def _encrypt(self, payload):
        plain = os.urandom(16) + struct.pack(">I", len(payload.encode())) + payload.encode() + self.config.corp_id.encode()
        pad = 32 - len(plain) % 32
        cipher = Cipher(algorithms.AES(base64.b64decode(self.key + "=")), modes.CBC(base64.b64decode(self.key + "=")[:16]))
        return base64.b64encode(cipher.encryptor().update(plain + bytes([pad]) * pad)).decode()


if __name__ == "__main__":
    unittest.main()
