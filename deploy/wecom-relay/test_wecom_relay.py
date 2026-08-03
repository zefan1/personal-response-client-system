import hashlib
import hmac
import json
import pathlib
import sys
import time
import unittest
import uuid

sys.path.insert(0, str(pathlib.Path(__file__).parent))
import wecom_relay


class RelayApplicationTest(unittest.TestCase):
    def setUp(self):
        self.app = wecom_relay.RelayApplication(
            wecom_relay.RelaySettings(
                key_id="local-test",
                relay_secret="relay-secret",
                corp_id="corp-id",
                app_secret="app-secret",
                api_base_url="https://qyapi.weixin.qq.com",
            ),
            now=lambda: 1_725_000_000,
        )

    def test_unsigned_request_is_rejected(self):
        response = self.app.handle(headers={}, raw_body=b"{}")

        self.assertEqual(response.status, 401)

    def test_operation_outside_allowlist_is_rejected(self):
        response = self.app.handle(*self.signed_request({"operation": "delete_everything", "payload": {}}))

        self.assertEqual(response.status, 400)

    def test_reused_signed_request_is_rejected(self):
        headers, raw_body = self.signed_request({"operation": "get_fields", "payload": {}})

        self.assertTrue(self.app._signature_valid(headers, raw_body))
        self.assertFalse(self.app._signature_valid(headers, raw_body))

    def signed_request(self, body):
        body = {**body, "requestId": "request-1"}
        raw_body = json.dumps(body, separators=(",", ":")).encode("utf-8")
        timestamp = "1725000000"
        nonce = str(uuid.uuid4())
        signature = hmac.new(
            b"relay-secret",
            f"{timestamp}.{nonce}.".encode("utf-8") + raw_body,
            hashlib.sha256,
        ).hexdigest()
        return {
            "X-Relay-Key-Id": "local-test",
            "X-Relay-Timestamp": timestamp,
            "X-Relay-Nonce": nonce,
            "X-Relay-Request-Id": "request-1",
            "X-Relay-Signature": signature,
        }, raw_body


if __name__ == "__main__":
    unittest.main()
