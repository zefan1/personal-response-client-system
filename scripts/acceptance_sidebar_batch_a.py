#!/usr/bin/env python3
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import date
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
OUTPUT = REPORT_DIR / "sidebar_batch_a.json"
BASE_URL = "http://localhost:8080"
USERNAME = "admin"
PASSWORD = "admin123"


def request_json(method: str, path: str, body: dict | None = None, token: str | None = None, timeout: int = 8):
    data = None
    headers: dict[str, str] = {}
    if body is not None:
        data = json.dumps(body, ensure_ascii=False).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
            return response.status, json.loads(raw), sanitize(raw)
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        return error.code, None, sanitize(raw)


def sanitize(raw: str) -> str:
    try:
        payload = json.loads(raw)
        redact(payload)
        return json.dumps(payload, ensure_ascii=False)[:1000]
    except Exception:
        return raw[:1000]


def redact(value):
    if isinstance(value, dict):
        for key in list(value.keys()):
            if key in {"accessToken", "refreshToken", "apiKey", "api_key", "password"}:
                value[key] = "***"
            else:
                redact(value[key])
    elif isinstance(value, list):
        for item in value:
            redact(item)


def data_of(payload):
    if isinstance(payload, dict) and payload.get("success") is True:
        return payload.get("data")
    return None


def list_from(value):
    if isinstance(value, list):
        return value
    if isinstance(value, dict):
        for key in ("items", "list", "records", "customers", "followups"):
            maybe = value.get(key)
            if isinstance(maybe, list):
                return maybe
    return []


def pick_phone(item):
    if not isinstance(item, dict):
        return ""
    return str(item.get("phoneFull") or item.get("phone") or item.get("customerPhone") or "")


def add(checks, name: str, ok: bool, detail: str, evidence: dict | None = None):
    item = {"name": name, "ok": bool(ok), "detail": detail}
    if evidence:
        item["evidence"] = evidence
    checks.append(item)


def main() -> int:
    checks: list[dict] = []

    status, payload, raw = request_json("GET", "/api/v1/auth/config")
    add(checks, "A1 backend reachable", status == 200 and data_of(payload) is not None, f"status={status} body={raw[:240]}")

    status, payload, raw = request_json("POST", "/admin/api/v1/auth/login", {"username": USERNAME, "password": PASSWORD})
    login_data = data_of(payload)
    token = login_data.get("accessToken") if isinstance(login_data, dict) else ""
    account = login_data.get("account") if isinstance(login_data, dict) else None
    add(checks, "A1 admin login", status == 200 and bool(token), f"status={status} tokenPresent={bool(token)}", {
        "accountName": account.get("displayName") if isinstance(account, dict) else None,
    })

    if not token:
        write_report(checks)
        return 1

    appointment_phone = "18800003333"
    status, payload, raw = request_json("GET", f"/api/v1/customers/{appointment_phone}", token=token)
    profile = data_of(payload)
    customer = profile.get("customer") if isinstance(profile, dict) else None
    version = customer.get("version") if isinstance(customer, dict) else None
    today = date.today().isoformat()
    if isinstance(version, int):
        status, payload, raw = request_json("PUT", f"/api/v1/customers/{appointment_phone}", {
            "version": version,
            "operator": "codex-local-acceptance",
            "fields": {
                "appointmentDate": today,
                "arrived": "NO",
            },
        }, token=token)
        add(checks, "A0 refresh appointment fixture", status == 200 and data_of(payload) is not None, f"status={status} appointmentDate={today}")
    else:
        add(checks, "A0 refresh appointment fixture", False, f"status={status} unable to read profile version body={raw[:300]}")

    status, payload, raw = request_json("GET", "/api/v1/desktop/status", token=token)
    status_data = data_of(payload)
    add(checks, "A1 desktop status", status == 200 and isinstance(status_data, dict), f"status={status}", {
        "keys": sorted(status_data.keys())[:20] if isinstance(status_data, dict) else [],
    })

    status, payload, raw = request_json("GET", "/api/v1/followups/today", token=token)
    follow_data = data_of(payload)
    followups = list_from(follow_data)
    phones = {pick_phone(item) for item in followups}
    add(checks, "A1/A4 followup data loads", status == 200 and len(followups) >= 2, f"status={status} count={len(followups)}")
    add(checks, "A2/A4 overdue customer exists", any(phone.endswith("1111") for phone in phones), f"phones={sorted(phones)}")
    add(checks, "A4 today customer exists", any(phone.endswith("2222") for phone in phones), f"phones={sorted(phones)}")
    add(checks, "A4 appointment customer exists", any(phone.endswith("3333") for phone in phones), f"phones={sorted(phones)}")

    query = urllib.parse.quote("1111")
    status, payload, raw = request_json("GET", f"/api/v1/customers/search?q={query}&limit=10", token=token)
    search_data = data_of(payload)
    customers = list_from(search_data)
    add(checks, "A3 search 1111 returns customer", status == 200 and any(pick_phone(item).endswith("1111") for item in customers), f"status={status} count={len(customers)}")

    status, payload, raw = request_json("GET", "/api/v1/customers/18800001111", token=token)
    profile = data_of(payload)
    profile_text = json.dumps(profile, ensure_ascii=False) if profile is not None else ""
    expected_words = ["逾期跟进客户", "上海", "产后修复", "腹直肌", "跟进"]
    add(checks, "A2/A3 customer profile loads with Chinese sample", status == 200 and all(word in profile_text for word in expected_words), f"status={status}", {
        "expectedWords": expected_words,
    })
    if isinstance(profile, dict):
        add(checks, "A3 profile exposes full phone for operations", str(profile.get("phoneFull") or "").endswith("1111"), f"phone={profile.get('phone')} phoneFull={profile.get('phoneFull')}")

    status, payload, raw = request_json("GET", "/api/v1/quick-search/items", token=token)
    quick_data = data_of(payload)
    quick_items = list_from(quick_data)
    quick_text = json.dumps(quick_items, ensure_ascii=False)
    add(checks, "A5 quick search local templates", status == 200 and "本地测试 团购开场白" in quick_text and "本地测试 到店提醒" in quick_text, f"status={status} count={len(quick_items)}")
    add(checks, "A5 quick search shortcuts", "local_tuan_open" in quick_text and "local_arrival_reminder" in quick_text, "shortcut check")

    chat_body = {
        "phone": "18800001111",
        "scene": "ACTIVE_REPLY",
        "clientMessage": "客户说：我产后三个月，腹直肌分离，想先了解价格和到店评估。",
    }
    status, payload, raw = request_json("POST", "/api/v1/chat/generate", chat_body, token=token, timeout=15)
    chat_data = data_of(payload)
    chat_text = json.dumps(chat_data, ensure_ascii=False) if chat_data is not None else ""
    add(checks, "A6 reply generation returns usable response", status == 200 and isinstance(chat_data, dict) and ("suggest" in chat_text or "reply" in chat_text or "content" in chat_text), f"status={status} body={raw[:500]}")
    add(checks, "A6 reply source is explicit", "replySource" in chat_text or "source" in chat_text, "source metadata present")

    return 0 if write_report(checks) else 1


def write_report(checks: list[dict]) -> bool:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "backendUrl": BASE_URL,
        "batch": "A",
        "passed": all(item["ok"] for item in checks),
        "checks": checks,
    }
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"sidebar_batch_a_report={OUTPUT}")
    print(f"passed={str(report['passed']).lower()} checks={sum(1 for item in checks if item['ok'])}/{len(checks)}")
    for item in checks:
        if not item["ok"]:
            print(f"failure={item['name']} detail={item['detail']}", file=sys.stderr)
    return bool(report["passed"])


if __name__ == "__main__":
    raise SystemExit(main())
