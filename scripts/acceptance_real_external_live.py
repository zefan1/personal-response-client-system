#!/usr/bin/env python3
import base64
import json
import os
import subprocess
import sys
import time
from pathlib import Path

from acceptance_real_external_local import (
    Api,
    BASE_URL,
    Check,
    REPORT_DIR,
    ensure_skill_binding,
    start_real_backend,
    stop_process,
    restore_mock_backend,
    shell_quote,
    wsl_path,
)


ROOT = Path(__file__).resolve().parents[1]
REQUIRED_PROVIDER_ENV = [
    "PDA_LIVE_SKILL_BASE_URL",
    "PDA_LIVE_SKILL_API_KEY",
    "PDA_LIVE_IMAGE_BASE_URL",
    "PDA_LIVE_IMAGE_API_KEY",
    "PDA_LIVE_LLM_BASE_URL",
    "PDA_LIVE_LLM_API_KEY",
    "PDA_LIVE_LLM_MODEL",
]
REQUIRED_WECOM_ENV = [
    "WECOM_TRANSPORT_MODE",
    "WECOM_SMARTSHEET_DOC_ID",
    "WECOM_SMARTSHEET_SHEET_ID",
    "WECOM_SMARTSHEET_VIEW_ID",
    "WECOM_SMARTSHEET_SOURCE_TABLE",
    "WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE",
]
ACCEPTANCE_SCREENSHOT_PATH = "PDA_LIVE_ACCEPTANCE_SCREENSHOT_PATH"
ACCEPTANCE_CONFIRMATION = "PDA_LIVE_ACCEPTANCE_CONFIRM"
ISOLATION_CONFIRMATION = "ISOLATED_WECOM_SHEET"
WECOM_ENV_PREFIX = "WECOM_"


def require_live_env() -> dict[str, str]:
    keys = REQUIRED_PROVIDER_ENV + REQUIRED_WECOM_ENV + [
        ACCEPTANCE_SCREENSHOT_PATH,
        ACCEPTANCE_CONFIRMATION,
    ]
    values = {key: os.environ.get(key, "").strip() for key in keys}
    mode = values["WECOM_TRANSPORT_MODE"].upper()
    if mode == "RELAY":
        keys.extend(["WECOM_RELAY_BASE_URL", "WECOM_RELAY_KEY_ID", "WECOM_RELAY_SECRET"])
    elif mode == "DIRECT":
        keys.extend(["WECOM_CORP_ID", "WECOM_APP_SECRET"])
    else:
        values["WECOM_TRANSPORT_MODE"] = ""
    values.update({key: os.environ.get(key, "").strip() for key in keys if key not in values})
    missing = [key for key, value in values.items() if not value]
    screenshot = Path(values.get(ACCEPTANCE_SCREENSHOT_PATH, ""))
    if not missing and not screenshot.is_file():
        missing.append(f"{ACCEPTANCE_SCREENSHOT_PATH} (must be a readable file)")
    if (values.get(ACCEPTANCE_CONFIRMATION)
            and values[ACCEPTANCE_CONFIRMATION] != ISOLATION_CONFIRMATION):
        missing.append(f"{ACCEPTANCE_CONFIRMATION}={ISOLATION_CONFIRMATION}")
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
        "llm.api_base_url": env["PDA_LIVE_LLM_BASE_URL"],
        "llm.api_key": env["PDA_LIVE_LLM_API_KEY"],
        "llm.model": env["PDA_LIVE_LLM_MODEL"],
    }
    for key, value in pairs.items():
        api.request(f"configure live {key}", "PUT", f"/admin/api/v1/configs/{key}", {"value": value})
        api.login()


def ensure_live_environment(api: Api, kind: str, base_url: str, api_key: str):
    path = f"/admin/api/v1/{kind}-environments"
    body = {
        "envName": f"live-acceptance-{kind}",
        "baseUrl": base_url,
        "apiKey": api_key,
        "remark": "live external acceptance",
    }
    if kind == "llm":
      body.update({
          "model": os.environ["PDA_LIVE_LLM_MODEL"],
          "protocol": os.environ.get("PDA_LIVE_LLM_PROTOCOL", "OPENAI_COMPATIBLE"),
          "timeoutMs": int(os.environ.get("PDA_LIVE_LLM_TIMEOUT_MS", "15000")),
          "temperature": float(os.environ.get("PDA_LIVE_LLM_TEMPERATURE", "0.2")),
          "maxTokens": int(os.environ.get("PDA_LIVE_LLM_MAX_TOKENS", "1024")),
      })
    created = api.request(
        f"create live {kind} environment",
        "POST",
        path,
        body,
    )
    env_id = (created.get("data") or {})["id"]
    api.request(f"activate live {kind} environment", "PUT", f"{path}/{env_id}/activate", {})
    api.login()
    return env_id


def poll_live_recognition(api: Api, screenshot_path: Path):
    image_base64 = base64.b64encode(screenshot_path.read_bytes()).decode("ascii")
    submitted = api.request(
        "live screenshot recognition submission",
        "POST",
        "/api/v1/chat/recognition-jobs",
        {
            "imageBase64": image_base64,
            "leadType": "GENERAL",
            "replySessionId": f"live-acceptance-{int(time.time())}",
        },
        sensitive_body=True,
    )
    job_id = ((submitted.get("data") or {}).get("jobId") or "").strip()
    if not job_id:
        raise AssertionError("live screenshot recognition did not return a job id")
    deadline = time.monotonic() + 90
    while time.monotonic() < deadline:
        job = api.request(
            "live screenshot recognition result",
            "GET",
            f"/api/v1/chat/recognition-jobs/{job_id}",
        )
        data = job.get("data") or {}
        status = data.get("status")
        if status == "READY":
            response = data.get("response") or {}
            suggestions = ((response.get("skill") or {}).get("suggestions") or [])
            if not suggestions:
                raise AssertionError("live screenshot recognition completed without a reply suggestion")
            return
        if status in {"FAILED", "CANCELLED", "EXPIRED"}:
            raise AssertionError(f"live screenshot recognition ended as {status}: {data.get('errorCode')}")
        time.sleep(1)
    raise TimeoutError("live screenshot recognition did not finish within 90 seconds")


def run_live_wecom_acceptance(api: Api):
    exported = " ".join(
        f"export {key}={shell_quote(value)};"
        for key, value in os.environ.items()
        if key.startswith(WECOM_ENV_PREFIX)
    )
    command = (
        f"{exported} cd {shell_quote(wsl_path(ROOT))} && "
        "mvn -q -DskipTests "
        "-Dexec.mainClass=com.privateflow.modules.tablewrite.client.WecomSmartSheetLiveAcceptanceMain "
        "org.codehaus.mojo:exec-maven-plugin:3.5.0:java"
    )
    result = subprocess.run(
        ["wsl", "-d", "Ubuntu", "--", "bash", "-lc", command],
        cwd=ROOT,
        stdout=subprocess.PIPE,
        stderr=subprocess.STDOUT,
        timeout=180,
    )
    output = (result.stdout or b"").decode("utf-8", errors="replace")
    api.checks.append(Check(
        "live WeCom Smart Sheet create/update/reread",
        result.returncode == 0,
        result.returncode,
        output[-240:],
    ))
    if result.returncode != 0:
        raise AssertionError("live WeCom Smart Sheet acceptance failed: " + output[-1000:])


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

    llm_id = ensure_live_environment(api, "llm", env["PDA_LIVE_LLM_BASE_URL"], env["PDA_LIVE_LLM_API_KEY"])
    llm_test = api.request("live LLM provider test", "POST", f"/admin/api/v1/llm-environments/{llm_id}/test", {})
    llm_data = llm_test.get("data") or {}
    if not llm_data.get("success"):
        raise AssertionError(f"live LLM environment test not successful: {llm_data}")

    poll_live_recognition(api, Path(env[ACCEPTANCE_SCREENSHOT_PATH]))
    run_live_wecom_acceptance(api)


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
        backend_proc = start_real_backend({
            key: value for key, value in env.items() if key.startswith(WECOM_ENV_PREFIX)
        })
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
