#!/usr/bin/env python3
import argparse
import json
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import parse_qs, unquote, urlparse


IMAGE_RECOGNITION_TEXT = json.dumps(
    {
        "nickname": "验收客户",
        "phone": "13900000001",
        "timestamp": "2026-07-03T12:00:00",
        "messages": [
            {"role": "client", "text": "想了解产后修复"},
            {"role": "staff", "text": "可以先了解您的恢复情况"},
        ],
    },
    ensure_ascii=False,
)

SKILL_RESPONSE = {
    "suggestions": [
        {"text": "可以先了解您的恢复情况。", "direction": "OPENING", "reason": "acceptance"},
        {"text": "建议预约到店评估。", "direction": "INVITE", "reason": "acceptance"},
        {"text": "我帮您安排顾问继续跟进。", "direction": "FOLLOWUP", "reason": "acceptance"},
    ],
    "customer_analysis": {"leadType": "XIAN_SUO", "confidence": 0.93},
    "followup_suggest": {"nextAction": "CALL", "daysLater": 1},
    "profile_updates": [{"field": "需求", "value": "产后修复"}],
}


class FakeExternalProvider(BaseHTTPRequestHandler):
    server_version = "PDAFakeExternal/1.0"

    def log_message(self, fmt, *args):
        print("%s %s" % (self.address_string(), fmt % args), flush=True)

    def do_GET(self):
        parsed = urlparse(self.path)
        if parsed.path == "/health":
            self.send_json({"success": True, "service": "fake-external-provider"})
            return
        parts = [unquote(part) for part in parsed.path.strip("/").split("/")]
        if len(parts) == 3 and parts[0] == "tables" and parts[2] == "rows":
            query = parse_qs(parsed.query)
            limit = int(query.get("limit", ["3"])[0] or "3")
            table = parts[1]
            rows = [
                {
                    "rowId": f"{table}-row-001",
                    "fields": {
                        "phone": "13900000001",
                        "nickname": "验收客户",
                        "lead_type": "XIAN_SUO",
                        "latest_message": "想了解产后修复",
                    },
                },
                {
                    "rowId": f"{table}-row-002",
                    "fields": {
                        "phone": "13900000002",
                        "nickname": "复诊客户",
                        "lead_type": "TUAN_GOU",
                        "latest_message": "想预约到店",
                    },
                },
            ][:limit]
            self.send_json({"rows": rows})
            return
        self.send_error(404, "not found")

    def do_POST(self):
        parsed = urlparse(self.path)
        body = self.read_body()
        content_type = self.headers.get("Content-Type", "")
        if parsed.path == "/v1/chat/completions":
            if "multipart/form-data" in content_type:
                self.send_json({"choices": [{"message": {"content": IMAGE_RECOGNITION_TEXT}}]})
            else:
                self.send_json({"choices": [{"message": {"content": json.dumps(SKILL_RESPONSE, ensure_ascii=False)}}]})
            return
        parts = [unquote(part) for part in parsed.path.strip("/").split("/")]
        if len(parts) == 3 and parts[0] == "tables" and parts[2] == "rows":
            row_id = f"{parts[1]}-fake-row-{abs(hash(body)) % 100000}"
            self.send_json({"rowId": row_id, "received": self.parse_json(body)})
            return
        self.send_error(404, "not found")

    def do_PUT(self):
        parsed = urlparse(self.path)
        body = self.read_body()
        parts = [unquote(part) for part in parsed.path.strip("/").split("/")]
        if len(parts) == 4 and parts[0] == "tables" and parts[2] == "rows":
            self.send_json({"updated": True, "rowId": parts[3], "received": self.parse_json(body)})
            return
        self.send_error(404, "not found")

    def read_body(self):
        length = int(self.headers.get("Content-Length", "0") or "0")
        return self.rfile.read(length) if length else b""

    @staticmethod
    def parse_json(body):
        try:
            return json.loads(body.decode("utf-8")) if body else None
        except json.JSONDecodeError:
            return None

    def send_json(self, payload, status=200):
        data = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--host", default="127.0.0.1")
    parser.add_argument("--port", type=int, default=18080)
    args = parser.parse_args()
    server = ThreadingHTTPServer((args.host, args.port), FakeExternalProvider)
    print(f"fake_external_provider_ready url=http://{args.host}:{args.port}", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
