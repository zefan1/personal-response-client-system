#!/usr/bin/env python3
import json
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
OUTPUT = REPORT_DIR / "admin_batch_b.json"
BASE_URL = "http://localhost:8080"
USERNAME = "admin"
PASSWORD = "admin123"
RUN_ID = datetime.now().strftime("%H%M%S")


def request_json(method: str, path: str, body: dict | None = None, token: str | None = None, timeout: int = 10):
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
        try:
            payload = json.loads(raw)
        except Exception:
            payload = None
        return error.code, payload, sanitize(raw)
    except Exception as error:
        return 0, None, str(error)


def sanitize(raw: str) -> str:
    try:
        payload = json.loads(raw)
        redact(payload)
        return json.dumps(payload, ensure_ascii=False)[:1200]
    except Exception:
        return raw[:1200]


def redact(value):
    if isinstance(value, dict):
        for key in list(value.keys()):
            if key in {"accessToken", "refreshToken", "apiKey", "api_key", "password", "newPassword"}:
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
        for key in ("items", "list", "records", "datasources", "categories", "versions", "logs"):
            maybe = value.get(key)
            if isinstance(maybe, list):
                return maybe
    return []


def item_id(value):
    if isinstance(value, dict):
        for key in ("id", "datasourceId", "categoryId"):
            if value.get(key) is not None:
                return value.get(key)
    return None


def add(checks, name: str, ok: bool, detail: str, evidence: dict | None = None):
    item = {"name": name, "ok": bool(ok), "detail": detail}
    if evidence:
        item["evidence"] = evidence
    checks.append(item)


def cleanup(cleanups, checks):
    for label, func in reversed(cleanups):
        try:
            ok, detail = func()
            add(checks, f"cleanup {label}", ok, detail)
        except Exception as error:
            add(checks, f"cleanup {label}", False, str(error))


def main() -> int:
    checks: list[dict] = []
    cleanups: list[tuple[str, object]] = []

    status, payload, raw = request_json("POST", "/admin/api/v1/auth/login", {"username": USERNAME, "password": PASSWORD})
    login_data = data_of(payload)
    token = login_data.get("accessToken") if isinstance(login_data, dict) else ""
    add(checks, "B0 admin login", status == 200 and bool(token), f"status={status} tokenPresent={bool(token)}")
    if not token:
      write_report(checks)
      return 1

    # B1: AI/Skill and LLM configuration.
    status, payload, raw = request_json("GET", "/admin/api/v1/llm-environments", token=token)
    llm_envs = list_from(data_of(payload))
    initial_llm_env_count = len(llm_envs)
    add(checks, "B1 llm environment list", status == 200, f"status={status} count={len(llm_envs)}")

    env_payload = {
        "envName": f"codex-b-llm-{RUN_ID}",
        "baseUrl": "http://127.0.0.1:18080",
        "apiKey": "codex-test-key",
        "model": "codex-b-model",
        "protocol": "OPENAI_COMPATIBLE",
        "timeoutMs": 3000,
        "temperature": 0.2,
        "maxTokens": 256,
    }
    status, payload, raw = request_json("POST", "/admin/api/v1/llm-environments", env_payload, token=token)
    llm_env = data_of(payload)
    llm_env_id = item_id(llm_env)
    add(checks, "B1 create llm environment", status == 200 and llm_env_id is not None, f"status={status} body={raw[:300]}")
    if llm_env_id:
        if initial_llm_env_count > 0:
            cleanups.append((f"llm environment {llm_env_id}", lambda eid=llm_env_id: delete_ok(f"/admin/api/v1/llm-environments/{eid}", token)))
        else:
            cleanups.append((f"llm environment {llm_env_id} retained", lambda: (True, "retained because backend requires at least one LLM environment")))
        route_payload = {
            "scene": "SUMMARY",
            "leadType": "PENDING",
            "environmentId": llm_env_id,
            "priority": 99,
            "enabled": True,
        }
        status, payload, raw = request_json("POST", "/admin/api/v1/llm-routes", route_payload, token=token)
        route = data_of(payload)
        route_id = item_id(route)
        add(checks, "B1 create llm route", status == 200 and route_id is not None, f"status={status} body={raw[:300]}")
        if route_id:
            cleanups.append((f"llm route {route_id}", lambda rid=route_id: delete_ok(f"/admin/api/v1/llm-routes/{rid}", token)))
            status, payload, raw = request_json("PUT", f"/admin/api/v1/llm-routes/{route_id}/toggle", {"enabled": False}, token=token)
            add(checks, "B1 toggle llm route", status == 200 and data_of(payload) is not None, f"status={status}")

    status, payload, raw = request_json("GET", "/admin/api/v1/analytics/llm-calls", token=token)
    add(checks, "B1 llm analytics loads", status == 200 and data_of(payload) is not None, f"status={status}")

    # B2: datasource.
    ds_payload = {
        "name": f"codex-b-datasource-{RUN_ID}",
        "sheetId": f"codex-sheet-{RUN_ID}",
        "sourceTable": f"codex_b_source_{RUN_ID}",
        "description": "codex batch b temporary datasource",
    }
    status, payload, raw = request_json("POST", "/admin/api/v1/datasources", ds_payload, token=token)
    datasource = data_of(payload)
    datasource_id = item_id(datasource)
    add(checks, "B2 create datasource", status == 200 and datasource_id is not None, f"status={status} body={raw[:300]}")
    if datasource_id:
        cleanups.append((f"datasource {datasource_id}", lambda did=datasource_id: delete_ok(f"/admin/api/v1/datasources/{did}", token)))
        status, payload, raw = request_json("PUT", f"/admin/api/v1/datasources/{datasource_id}/mappings", {
            "mappings": [{"sourceField": "phone", "targetField": "phone", "enabled": True}]
        }, token=token)
        add(checks, "B2 save datasource mapping", status == 200 and data_of(payload) is not None, f"status={status}")
        status, payload, raw = request_json("GET", f"/admin/api/v1/datasources/{datasource_id}/mappings/versions", token=token)
        add(checks, "B2 mapping versions load", status == 200 and data_of(payload) is not None, f"status={status}")
        status, payload, raw = request_json("PUT", f"/admin/api/v1/datasources/{datasource_id}/toggle", {"enabled": False}, token=token)
        add(checks, "B2 toggle datasource", status == 200 and data_of(payload) is not None, f"status={status}")

    # B3: quick search.
    quick_payload = {
        "contentType": "TEMPLATE",
        "leadType": "GENERAL",
        "title": f"codex-b-template-{RUN_ID}",
        "shortcutCode": f"cb{RUN_ID[-6:]}",
        "content": "codex batch b temporary template",
        "sortOrder": 999,
        "enabled": True,
    }
    status, payload, raw = request_json("POST", "/admin/api/v1/quick-search/items", quick_payload, token=token)
    quick = data_of(payload)
    quick_id = item_id(quick)
    add(checks, "B3 create quick search item", status == 200 and quick_id is not None, f"status={status} body={raw[:300]}")
    if quick_id:
        cleanups.append((f"quick search {quick_id}", lambda qid=quick_id: delete_ok(f"/admin/api/v1/quick-search/items/{qid}", token)))
        updated = dict(quick_payload)
        updated["title"] = f"codex-b-template-updated-{RUN_ID}"
        status, payload, raw = request_json("PUT", f"/admin/api/v1/quick-search/items/{quick_id}", updated, token=token)
        add(checks, "B3 update quick search item", status == 200 and data_of(payload) is not None, f"status={status}")
        status, payload, raw = request_json("PUT", f"/admin/api/v1/quick-search/items/{quick_id}/toggle", token=token)
        add(checks, "B3 toggle quick search item", status == 200 and data_of(payload) is not None, f"status={status}")

    # B4: accounts. Create leader and keeper, then delete keeper before leader.
    leader_phone = "199" + RUN_ID[-8:].rjust(8, "0")
    keeper_phone = "198" + RUN_ID[-8:].rjust(8, "0")
    status, payload, raw = request_json("POST", "/admin/api/v1/accounts", {
        "phone": leader_phone,
        "password": "codex123",
        "displayName": "测组长" + RUN_ID[-4:],
        "role": "LEADER",
        "leaderId": None,
    }, token=token)
    leader = data_of(payload)
    leader_id = item_id(leader)
    add(checks, "B4 create leader account", status == 200 and leader_id is not None, f"status={status} body={raw[:300]}")
    if leader_id:
        cleanups.append((f"leader account {leader_id}", lambda aid=leader_id: delete_ok(f"/admin/api/v1/accounts/{aid}", token)))
        status, payload, raw = request_json("POST", "/admin/api/v1/accounts", {
            "phone": keeper_phone,
            "password": "codex123",
            "displayName": "测管家" + RUN_ID[-4:],
            "role": "KEEPER",
            "leaderId": leader_id,
        }, token=token)
        keeper = data_of(payload)
        keeper_id = item_id(keeper)
        add(checks, "B4 create keeper account", status == 200 and keeper_id is not None, f"status={status} body={raw[:300]}")
        if keeper_id:
            cleanups.append((f"keeper account {keeper_id}", lambda aid=keeper_id: delete_ok(f"/admin/api/v1/accounts/{aid}", token)))
            status, payload, raw = request_json("PUT", f"/admin/api/v1/accounts/{keeper_id}/toggle", {"isEnabled": False}, token=token)
            add(checks, "B4 toggle keeper account", status == 200 and data_of(payload) is not None, f"status={status}")
            status, payload, raw = request_json("PUT", f"/admin/api/v1/accounts/{keeper_id}/reset-password", {"newPassword": "codex456"}, token=token)
            add(checks, "B4 reset keeper password", status == 200 and data_of(payload) is not None, f"status={status}")

    # B5: rules.
    rule_payload = {
        "name": f"codex-b-rule-{RUN_ID}",
        "conditionJson": json.dumps({"operator": "AND", "conditions": [{"field": "leadType", "op": "EQ", "value": "PENDING"}]}),
        "actionType": "ALERT",
        "actionConfig": json.dumps({"message": "codex batch b"}),
        "priority": 1,
        "enabled": True,
    }
    status, payload, raw = request_json("POST", "/admin/api/v1/rules", rule_payload, token=token)
    rule = data_of(payload)
    rule_id = item_id(rule)
    add(checks, "B5 create rule", status == 200 and rule_id is not None, f"status={status} body={raw[:300]}")
    if rule_id:
        cleanups.append((f"rule {rule_id}", lambda rid=rule_id: delete_ok(f"/admin/api/v1/rules/{rid}", token)))
        status, payload, raw = request_json("PUT", f"/admin/api/v1/rules/{rule_id}/toggle", {"enabled": False}, token=token)
        add(checks, "B5 toggle rule", status == 200 and data_of(payload) is not None, f"status={status}")

    # B5: tags. Use a field unlikely to be already bound.
    status, payload, raw = request_json("POST", "/admin/api/v1/tags/categories", {
        "categoryName": f"codex-b-tag-{RUN_ID}",
        "boundField": "purchasedProject",
        "isEnabled": True,
        "sortOrder": 999,
    }, token=token)
    category = data_of(payload)
    category_id = item_id(category)
    add(checks, "B5 create tag category", status == 200 and category_id is not None, f"status={status} body={raw[:300]}")
    if category_id:
        cleanups.append((f"tag category {category_id}", lambda cid=category_id: delete_ok(f"/admin/api/v1/tags/categories/{cid}", token)))
        status, payload, raw = request_json("POST", "/admin/api/v1/tags/values", {
            "categoryId": category_id,
            "tagValue": f"CODEX_B_{RUN_ID}",
            "displayName": "批次B标签",
            "isEnabled": True,
            "sortOrder": 1,
        }, token=token)
        tag_value = data_of(payload)
        tag_value_id = item_id(tag_value)
        add(checks, "B5 create tag value", status == 200 and tag_value_id is not None, f"status={status} body={raw[:300]}")
        if tag_value_id:
            cleanups.append((f"tag value {tag_value_id}", lambda vid=tag_value_id: delete_ok(f"/admin/api/v1/tags/values/{vid}", token)))
            status, payload, raw = request_json("PUT", f"/admin/api/v1/tags/values/{tag_value_id}/toggle", {"isEnabled": False}, token=token)
            add(checks, "B5 toggle tag value", status == 200 and data_of(payload) is not None, f"status={status}")

    # B5: notices.
    publish_at = (datetime.now() + timedelta(minutes=5)).replace(microsecond=0).isoformat()
    status, payload, raw = request_json("POST", "/admin/api/v1/notices", {
        "title": f"codex-b-notice-{RUN_ID}",
        "content": "codex batch b temporary notice",
        "level": "INFO",
        "publishType": "SCHEDULED",
        "publishAt": publish_at,
        "expireDays": 1,
    }, token=token)
    notice = data_of(payload)
    notice_id = item_id(notice)
    add(checks, "B5 create scheduled notice", status == 200 and notice_id is not None, f"status={status} body={raw[:300]}")
    if notice_id:
        cleanups.append((f"notice {notice_id}", lambda nid=notice_id: delete_ok(f"/admin/api/v1/notices/{nid}", token)))
        status, payload, raw = request_json("PUT", f"/admin/api/v1/notices/{notice_id}/stop", {}, token=token)
        add(checks, "B5 stop notice", status == 200 and data_of(payload) is not None, f"status={status}")

    # B5: audit export and health.
    status, payload, raw = request_json("GET", "/admin/api/v1/health", token=token)
    add(checks, "B5 health loads", status == 200 and data_of(payload) is not None, f"status={status}")
    status, payload, raw = request_json("POST", "/admin/api/v1/audit-logs/export", {"action": "", "operator": ""}, token=token)
    export_data = data_of(payload)
    add(checks, "B5 audit export starts", status == 200 and isinstance(export_data, dict), f"status={status}")

    # Versions: draft create/update/delete without publish.
    version = f"9.9.{int(RUN_ID[-4:]) % 10000}"
    status, payload, raw = request_json("POST", "/admin/api/v1/versions", {
        "version": version,
        "platform": "WINDOWS",
        "downloadUrl": "/downloads/desktop-releases/codex-b.exe",
        "changelog": "codex batch b temporary version",
        "updateStrategy": "OPTIONAL",
        "gradualPercent": None,
        "fileSize": 1,
    }, token=token)
    version_data = data_of(payload)
    version_id = item_id(version_data)
    add(checks, "B5 create draft version", status == 200 and version_id is not None, f"status={status} body={raw[:300]}")
    if version_id:
        cleanups.append((f"version {version_id}", lambda vid=version_id: delete_ok(f"/admin/api/v1/versions/{vid}", token)))
        status, payload, raw = request_json("PUT", f"/admin/api/v1/versions/{version_id}", {
            "version": version,
            "downloadUrl": "/downloads/desktop-releases/codex-b-updated.exe",
            "changelog": "codex batch b temporary version updated",
            "updateStrategy": "OPTIONAL",
            "gradualPercent": None,
            "fileSize": 2,
        }, token=token)
        add(checks, "B5 update draft version", status == 200 and data_of(payload) is not None, f"status={status}")

    cleanup(cleanups, checks)
    return 0 if write_report(checks) else 1


def delete_ok(path: str, token: str):
    status, payload, raw = request_json("DELETE", path, token=token)
    return status in (200, 204), f"status={status} body={raw[:240]}"


def write_report(checks: list[dict]) -> bool:
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    required_checks = [item for item in checks if not item["name"].startswith("cleanup ")]
    cleanup_checks = [item for item in checks if item["name"].startswith("cleanup ")]
    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "backendUrl": BASE_URL,
        "batch": "B",
        "passed": all(item["ok"] for item in checks),
        "requiredPassed": all(item["ok"] for item in required_checks),
        "cleanupPassed": all(item["ok"] for item in cleanup_checks),
        "checks": checks,
    }
    OUTPUT.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"admin_batch_b_report={OUTPUT}")
    print(f"passed={str(report['passed']).lower()} checks={sum(1 for item in checks if item['ok'])}/{len(checks)}")
    for item in checks:
        if not item["ok"]:
            print(f"failure={item['name']} detail={item['detail']}", file=sys.stderr)
    return bool(report["passed"])


if __name__ == "__main__":
    raise SystemExit(main())
