#!/usr/bin/env python3
import json
import os
import sys
from pathlib import Path

from acceptance_real_external_local import (
    Api,
    BASE_URL,
    REPORT_DIR,
    ensure_datasource,
    ensure_skill_binding,
    start_real_backend,
    stop_process,
    restore_mock_backend,
)


ROOT = Path(__file__).resolve().parents[1]
REQUIRED_ENV = [
    "PDA_LIVE_SKILL_BASE_URL",
    "PDA_LIVE_SKILL_API_KEY",
    "PDA_LIVE_IMAGE_BASE_URL",
    "PDA_LIVE_IMAGE_API_KEY",
    "PDA_LIVE_TABLE_BASE_URL",
    "PDA_LIVE_TABLE_API_KEY",
]


def require_live_env() -> dict[str, str]:
    values = {key: os.environ.get(key, "").strip() for key in REQUIRED_ENV}
    missing = [key for key, value in values.items() if not value]
    if missing:
        write_report([], False, missing, "missing live external environment variables")
        raise SystemExit("missing live external environment variables: " + ", ".join(missing))
    return values


def configure_live_external(api: Api, env: dict[str, str]):
    pairs = {
        "skill.api_base_url": env["PDA_LIVE_SKILL_BASE_URL"],
        "skill.api_key": env["PDA_LIVE_SKILL_API_KEY"],
        "image.api_base_url": env["PDA_LIVE_IMAGE_BASE_URL"],
        "image.api_key": env["PDA_LIVE_IMAGE_API_KEY"],
        "table.api_base_url": env["PDA_LIVE_TABLE_BASE_URL"],
        "table.api_key": env["PDA_LIVE_TABLE_API_KEY"],
    }
    for key, value in pairs.items():
        api.request(f"configure live {key}", "PUT", f"/admin/api/v1/configs/{key}", {"value": value})
        api.login()


def ensure_live_environment(api: Api, kind: str, base_url: str, api_key: str):
    path = f"/admin/api/v1/{kind}-environments"
    created = api.request(
        f"create live {kind} environment",
        "POST",
        path,
        {
            "envName": f"live-acceptance-{kind}",
            "baseUrl": base_url,
            "apiKey": api_key,
            "remark": "live external acceptance",
        },
    )
    env_id = (created.get("data") or {})["id"]
    api.request(f"activate live {kind} environment", "PUT", f"{path}/{env_id}/activate", {})
    api.login()
    return env_id


def run_live_acceptance(api: Api, env: dict[str, str]):
    api.login()
    configure_live_external(api, env)
    ensure_live_environment(api, "skill", env["PDA_LIVE_SKILL_BASE_URL"], env["PDA_LIVE_SKILL_API_KEY"])
    skill_id = ensure_skill_binding(api)
    skill_test = api.request(
        "live skill provider test",
        "POST",
        f"/admin/api/v1/skills/{skill_id}/test",
        {"testMessage": "客户想了解产后修复，请生成跟进话术"},
    )
    suggestions = ((skill_test.get("data") or {}).get("suggestions") or [])
    if len(suggestions) < 1:
        raise AssertionError("live skill provider did not return suggestions")

    image_id = ensure_live_environment(api, "image", env["PDA_LIVE_IMAGE_BASE_URL"], env["PDA_LIVE_IMAGE_API_KEY"])
    image_test = api.request("live image provider test", "POST", f"/admin/api/v1/image-environments/{image_id}/test", {})
    image_data = image_test.get("data") or {}
    if not image_data.get("success"):
        raise AssertionError(f"live image environment test not successful: {image_data}")

    datasource_id = ensure_datasource(api)
    columns = api.request("live wecom sheet columns", "GET", f"/admin/api/v1/datasources/{datasource_id}/columns")
    columns_data = columns.get("data") or {}
    if columns_data.get("fetchStatus") != "OK" or columns_data.get("source") != "SHEET_SAMPLE":
        raise AssertionError(f"live datasource columns did not use SheetClient sample rows: {columns_data}")

    api.request(
        "live wecom table update",
        "POST",
        "/api/v1/customers/13900000001/save-to-table",
        {
            "sourceTable": "acceptance_customers",
            "sourceRowId": "acceptance_customers-row-001",
            "fields": {"nickname": "验收客户", "lead_type": "XIAN_SUO"},
        },
    )


def write_report(checks, passed: bool, missing_env=None, fatal=None):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "baseUrl": BASE_URL,
        "mockExternals": False,
        "providerMode": "live",
        "passed": passed,
        "missingEnv": missing_env or [],
        "fatal": str(fatal) if fatal else None,
        "checks": [check.__dict__ for check in checks],
    }
    path = REPORT_DIR / "real_external_live.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"real_external_live_report={path}")
    print(f"passed={str(report['passed']).lower()} checks={len(checks)} missingEnv={len(report['missingEnv'])}")


def main():
    env = require_live_env()
    backend_proc = None
    api = Api(BASE_URL)
    failed = None
    try:
        backend_proc = start_real_backend()
        run_live_acceptance(api, env)
    except Exception as ex:
        failed = ex
    finally:
        write_report(api.checks, failed is None and all(check.ok for check in api.checks), fatal=failed)
        stop_process(backend_proc)
        restore_mock_backend()
    if failed:
        print(f"fatal={failed}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
