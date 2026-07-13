#!/usr/bin/env python3
import argparse
import json
import sys
import time
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"


def request_json(method: str, url: str, body: dict | None = None, timeout: int = 5) -> tuple[int, dict | None, str]:
    data = None
    headers = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    with urllib.request.urlopen(req, timeout=timeout) as response:
        raw = response.read().decode("utf-8", errors="replace")
        try:
            payload = json.loads(raw)
        except json.JSONDecodeError:
            payload = None
        return response.status, payload, sanitize(raw)


def request_text(url: str, timeout: int = 5) -> tuple[int, str]:
    with urllib.request.urlopen(url, timeout=timeout) as response:
        return response.status, response.read().decode("utf-8", errors="replace")


def sanitize(text: str) -> str:
    try:
        payload = json.loads(text)
        redact_json(payload)
        return json.dumps(payload, ensure_ascii=False)
    except Exception:
        return text


def redact_json(value: object) -> None:
    if isinstance(value, dict):
        for key in list(value.keys()):
            if key in {"accessToken", "refreshToken", "apiKey", "api_key", "password"}:
                value[key] = "***"
            else:
                redact_json(value[key])
    elif isinstance(value, list):
        for item in value:
            redact_json(item)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--frontend-url", default="http://127.0.0.1:5173/")
    parser.add_argument("--backend-url", default="http://localhost:8080")
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    args = parser.parse_args()

    checks: list[dict[str, object]] = []

    def add_check(name: str, ok: bool, detail: str):
        checks.append({"name": name, "ok": ok, "detail": detail})

    try:
        status, text = request_text(args.frontend_url)
        add_check(
            "frontend vite page",
            status == 200 and '<div id="app">' in text and "/src/renderer/main.ts" in text,
            f"status={status} bytes={len(text)}",
        )
    except Exception as error:
        add_check("frontend vite page", False, str(error))

    backend = args.backend_url.rstrip("/")
    try:
        status, payload, raw = request_json("GET", f"{backend}/api/v1/auth/config")
        add_check(
            "backend auth config",
            status == 200 and isinstance(payload, dict) and payload.get("success") is True and isinstance(payload.get("data"), dict),
            f"status={status} body={raw[:240]}",
        )
    except Exception as error:
        add_check("backend auth config", False, str(error))

    try:
        status, payload, raw = request_json("POST", f"{backend}/admin/api/v1/auth/login", {
            "username": args.username,
            "password": args.password,
        })
        data = payload.get("data") if isinstance(payload, dict) else None
        token = data.get("accessToken") if isinstance(data, dict) else None
        add_check(
            "admin login token",
            status == 200 and isinstance(payload, dict) and payload.get("success") is True and bool(token),
            f"status={status} tokenPresent={bool(token)} body={raw[:240]}",
        )
    except Exception as error:
        add_check("admin login token", False, str(error))

    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "frontendUrl": args.frontend_url,
        "backendUrl": backend,
        "username": args.username,
        "passed": all(check["ok"] for check in checks),
        "checks": checks,
    }
    path = REPORT_DIR / "manual_test_readiness.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"manual_test_readiness_report={path}")
    print(f"passed={str(report['passed']).lower()} checks={sum(1 for check in checks if check['ok'])}/{len(checks)}")
    if not report["passed"]:
        for check in checks:
            if not check["ok"]:
                print(f"manual readiness failure: {check['name']} {check['detail']}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
