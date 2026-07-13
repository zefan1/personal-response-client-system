#!/usr/bin/env python3
import json
import subprocess
import time

from acceptance_real_external_local import (
    Api,
    BASE_URL,
    Check,
    DB_NAME,
    DB_PASSWORD,
    DB_USER,
    FAKE_URL,
    REPORT_DIR,
    ROOT,
    start_fake_provider,
    start_real_backend,
    stop_process,
    restore_mock_backend,
    shell_quote,
)


PRIMARY_BAD_URL = "http://127.0.0.1:18081"
CUSTOMER_PHONE = "13900009991"


def configure(api: Api, key: str, value: str):
    api.request(f"configure {key}", "PUT", f"/admin/api/v1/configs/{key}", {"value": value})
    api.login()


def create_llm_environment(api: Api, name: str, base_url: str, api_key: str, model: str):
    payload = api.request(
        f"create LLM environment {name}",
        "POST",
        "/admin/api/v1/llm-environments",
        {
            "envName": name,
            "baseUrl": base_url,
            "apiKey": api_key,
            "model": model,
            "protocol": "OPENAI_COMPATIBLE",
            "timeoutMs": 1000,
            "temperature": 0.2,
            "maxTokens": 1024,
        },
    )
    return (payload.get("data") or {})["id"]


def create_route(api: Api, environment_id: int, priority: int):
    payload = api.request(
        f"create LLM route priority {priority}",
        "POST",
        "/admin/api/v1/llm-routes",
        {
            "scene": "REPLY_GENERATION",
            "leadType": "PENDING",
            "environmentId": environment_id,
            "priority": priority,
            "enabled": True,
        },
    )
    return (payload.get("data") or {})["id"]


def import_customer(api: Api):
    sql = (
        "INSERT INTO customers (phone, nickname, lead_type, followup_notes, source_table, synced_at) "
        f"VALUES ('{CUSTOMER_PHONE}', 'LLM主备验收客户', 'PENDING', '想了解产后恢复', 'llm_failover_acceptance', NOW()) "
        "ON DUPLICATE KEY UPDATE nickname = VALUES(nickname), lead_type = VALUES(lead_type), "
        "followup_notes = VALUES(followup_notes), source_table = VALUES(source_table), synced_at = NOW();"
    )
    result = subprocess.run(
        [
            "wsl",
            "-d",
            "Ubuntu",
            "--",
            "bash",
            "-lc",
            f"mysql -u{shell_quote(DB_USER)} -p{shell_quote(DB_PASSWORD)} {shell_quote(DB_NAME)} -e {shell_quote(sql)}",
        ],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
    )
    ok = result.returncode == 0
    api.checks.append(
        Check(
            "insert LLM failover customer fixture",
            ok,
            0 if ok else result.returncode,
            (result.stderr or result.stdout).decode("utf-8", errors="replace")[-240:],
        )
    )
    if not ok:
        raise AssertionError("insert LLM failover customer fixture failed")


def enable_llm_reply_generation(api: Api):
    configure(api, "llm.reply_generation.enabled", "true")
    configure(api, "llm.reply_generation.fallback_to_skill", "false")


def generate_reply(api: Api):
    payload = api.request(
        "chat generate through LLM failover",
        "POST",
        "/api/v1/chat/generate",
        {
            "phone": CUSTOMER_PHONE,
            "scene": "ACTIVE_REPLY",
            "clientMessage": "客户想了解产后恢复，想预约到店评估",
        },
    )
    data = payload.get("data") or {}
    source = data.get("replySource") or {}
    suggestions = ((data.get("skill") or {}).get("suggestions") or [])
    if source.get("source") != "LLM":
        raise AssertionError(f"expected LLM reply source, got {source}")
    if not suggestions:
        raise AssertionError("expected LLM suggestions from backup model")
    return data


def llm_analytics(api: Api):
    # MySQL timestamps can round to seconds; a tiny delay keeps logs visible in analytics windows.
    time.sleep(1)
    payload = api.request(
        "read LLM analytics after failover",
        "GET",
        "/admin/api/v1/analytics/llm-calls?days=1&scene=REPLY_GENERATION&leadType=PENDING",
    )
    data = payload.get("data") or {}
    details = data.get("details") or []
    total = sum(int(item.get("totalCalls") or 0) for item in details)
    success = sum(int(item.get("successCount") or 0) for item in details)
    failed = sum(int(item.get("failCount") or 0) for item in details)
    if total < 2 or success < 1 or failed < 1:
        raise AssertionError(f"expected one failed primary call and one successful backup call, got {data}")
    return data


def run_acceptance(api: Api):
    api.login()
    primary_id = create_llm_environment(api, "LLM failover primary bad", PRIMARY_BAD_URL, "bad-primary-key", "bad-primary")
    backup_id = create_llm_environment(api, "LLM failover backup fake", FAKE_URL, "fake-acceptance-key", "fake-backup")
    api.request("activate backup LLM environment", "PUT", f"/admin/api/v1/llm-environments/{backup_id}/activate", {})
    api.login()
    create_route(api, primary_id, 1)
    create_route(api, backup_id, 2)
    configure(api, "llm.api_base_url", FAKE_URL)
    configure(api, "llm.api_key", "fake-acceptance-key")
    configure(api, "llm.model", "fake-backup")
    enable_llm_reply_generation(api)
    import_customer(api)
    response = generate_reply(api)
    analytics = llm_analytics(api)
    return {"replySource": response.get("replySource"), "analytics": analytics}


def write_report(api: Api, passed: bool, result=None, fatal=None):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "baseUrl": BASE_URL,
        "fakeProviderUrl": FAKE_URL,
        "passed": passed,
        "fatal": str(fatal) if fatal else None,
        "result": result or {},
        "checks": [check.__dict__ for check in api.checks],
    }
    path = REPORT_DIR / "llm_failover_local.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"llm_failover_local_report={path}")
    print(f"passed={str(report['passed']).lower()} checks={len(api.checks)}")


def main():
    fake_proc = None
    backend_proc = None
    api = Api(BASE_URL)
    result = None
    failed = None
    try:
        fake_proc = start_fake_provider()
        backend_proc = start_real_backend()
        result = run_acceptance(api)
    except Exception as ex:
        failed = ex
    finally:
        write_report(api, failed is None and all(check.ok for check in api.checks), result=result, fatal=failed)
        stop_process(backend_proc)
        stop_process(fake_proc)
        restore_mock_backend()
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
