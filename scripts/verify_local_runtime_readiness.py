#!/usr/bin/env python3
import argparse
import json
import os
import shutil
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
DEFAULT_BACKEND_URL = os.environ.get("PDA_BASE_URL", "http://localhost:8080")
DEFAULT_DB_NAME = os.environ.get("SMOKE_DB_NAME", "private_domain_assistant_smoke")
DEFAULT_DB_USER = os.environ.get("SMOKE_DB_USER", "pda_smoke")
DEFAULT_DB_PASSWORD = os.environ.get("SMOKE_DB_PASSWORD", "pda_smoke_pwd")
REAL_EXTERNAL_CONFIG_KEYS = [
    "skill.api_base_url",
    "skill.api_key",
    "image.api_base_url",
    "image.api_key",
    "llm.api_base_url",
    "llm.api_key",
    "llm.model",
    "table.api_base_url",
    "table.api_key",
]
LLM_SCENE_CONFIG_KEYS = [
    "llm.reply_generation.enabled",
    "llm.reply_generation.fallback_to_skill",
    "llm.reply_generation.temperature",
    "llm.reply_generation.max_tokens",
    "llm.reply_generation.system_prompt",
    "llm.profile_extraction.enabled",
    "llm.profile_extraction.fallback_to_skill",
    "llm.profile_extraction.temperature",
    "llm.profile_extraction.max_tokens",
    "llm.profile_extraction.system_prompt",
    "llm.followup_suggestion.enabled",
    "llm.followup_suggestion.temperature",
    "llm.followup_suggestion.max_tokens",
    "llm.followup_suggestion.system_prompt",
    "llm.abnormal_detection.enabled",
    "llm.abnormal_detection.temperature",
    "llm.abnormal_detection.max_tokens",
    "llm.abnormal_detection.system_prompt",
    "llm.summary.enabled",
    "llm.summary.temperature",
    "llm.summary.max_tokens",
    "llm.summary.system_prompt",
]
LLM_SCENES = [
    "REPLY_GENERATION",
    "PROFILE_EXTRACTION",
    "FOLLOWUP_SUGGESTION",
    "ABNORMAL_DETECTION",
    "SUMMARY",
]


def run(command: list[str], timeout: int = 20) -> dict[str, object]:
    started = time.time()
    try:
        completed = subprocess.run(
            command,
            cwd=ROOT,
            text=True,
            encoding="utf-8",
            errors="replace",
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            timeout=timeout,
        )
        return {
            "ok": completed.returncode == 0,
            "exitCode": completed.returncode,
            "durationMs": int((time.time() - started) * 1000),
            "output": completed.stdout[-3000:],
        }
    except Exception as error:
        return {
            "ok": False,
            "exitCode": -1,
            "durationMs": int((time.time() - started) * 1000),
            "output": str(error),
        }


def request_json(method: str, url: str, body: dict | None = None, token: str | None = None, timeout: int = 5) -> tuple[bool, dict[str, object] | None, str]:
    data = None
    headers: dict[str, str] = {}
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=timeout) as response:
            raw = response.read().decode("utf-8", errors="replace")
            try:
                payload = json.loads(raw)
            except json.JSONDecodeError:
                payload = None
            return 200 <= response.status < 300, payload, sanitize(raw)[:600]
    except urllib.error.HTTPError as error:
        raw = error.read().decode("utf-8", errors="replace")
        return False, None, f"HTTP {error.code}: {sanitize(raw)[:600]}"
    except Exception as error:
        return False, None, str(error)


def sanitize(text: str) -> str:
    replacements = [
        ("accessToken", "***"),
        ("refreshToken", "***"),
    ]
    sanitized = text
    try:
        payload = json.loads(text)
        redact_json(payload)
        sanitized = json.dumps(payload, ensure_ascii=False)
    except Exception:
        for token, replacement in replacements:
            sanitized = sanitized.replace(token, replacement)
    return sanitized


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


def wsl_quote(value: str) -> str:
    return "'" + value.replace("'", "'\"'\"'") + "'"


def sql_in(values: list[str]) -> str:
    return ",".join("'" + value.replace("'", "''") + "'" for value in values)


def wsl_available() -> bool:
    return shutil.which("wsl") is not None


def wsl_run(script: str, timeout: int = 30) -> dict[str, object]:
    if not wsl_available():
        return {"ok": False, "exitCode": -1, "durationMs": 0, "output": "wsl command not found"}
    return run(["wsl", "bash", "-lc", script], timeout=timeout)


def add_check(checks: list[dict[str, object]], name: str, ok: bool, detail: str, required: bool = True, data: dict[str, object] | None = None) -> None:
    item: dict[str, object] = {
        "name": name,
        "ok": ok,
        "required": required,
        "detail": detail,
    }
    if data:
        item["data"] = data
    checks.append(item)


def check_windows_tools(checks: list[dict[str, object]]) -> None:
    required = ["java", "javac", "node", "npm", "wsl"]
    optional = ["mvn", "mysql", "docker"]
    for tool in required:
        path = shutil.which(tool)
        add_check(checks, f"windows tool:{tool}", bool(path), path or "not found on Windows PATH")
    for tool in optional:
        path = shutil.which(tool)
        add_check(checks, f"windows optional tool:{tool}", bool(path), path or "not found on Windows PATH", required=False)


def check_wsl_tools(checks: list[dict[str, object]]) -> None:
    result = wsl_run("for tool in java mvn mysql redis-server; do command -v $tool >/dev/null || missing=\"$missing $tool\"; done; echo missing=${missing:-}; java -version 2>&1 | head -n 1; mvn -version 2>&1 | head -n 1", timeout=20)
    output = str(result["output"])
    missing = ""
    for line in output.splitlines():
        if line.startswith("missing="):
            missing = line.removeprefix("missing=").strip()
            break
    add_check(checks, "wsl backend toolchain", result["ok"] and not missing, output.strip() or "no output")


def check_backend(checks: list[dict[str, object]], backend_url: str, username: str, password: str) -> str:
    base = backend_url.rstrip("/")
    ok, payload, raw = request_json("GET", f"{base}/api/v1/auth/config")
    add_check(checks, "backend auth config", ok and isinstance(payload, dict) and payload.get("success") is True, raw)
    token = ""
    ok, payload, raw = request_json("POST", f"{base}/admin/api/v1/auth/login", {"username": username, "password": password})
    data = payload.get("data") if isinstance(payload, dict) else None
    if isinstance(data, dict):
        token = str(data.get("accessToken") or "")
    add_check(checks, "admin login", ok and bool(token), raw)
    if token:
        ok, payload, raw = request_json("GET", f"{base}/admin/api/v1/health", token=token)
        health_data = payload.get("data") if isinstance(payload, dict) else None
        db_status = ""
        redis_status = ""
        runtime_mode = ""
        if isinstance(health_data, dict):
            components = health_data.get("components")
            if isinstance(components, dict):
                db = components.get("db")
                redis = components.get("redis")
                if isinstance(db, dict):
                    db_status = str(db.get("status") or "")
                if isinstance(redis, dict):
                    redis_status = str(redis.get("status") or "")
            runtime = health_data.get("runtimeMode")
            if isinstance(runtime, dict):
                runtime_mode = str(runtime.get("label") or "")
        add_check(
            checks,
            "backend health db/redis",
            ok and db_status == "UP" and redis_status == "UP",
            raw,
            data={"dbStatus": db_status, "redisStatus": redis_status, "runtimeMode": runtime_mode},
        )
    return token


def check_wsl_database(checks: list[dict[str, object]], db_name: str, db_user: str, db_password: str) -> None:
    all_config_keys = REAL_EXTERNAL_CONFIG_KEYS + LLM_SCENE_CONFIG_KEYS
    sql = (
        "SELECT CONCAT('flyway=', COUNT(*)) FROM flyway_schema_history; "
        "SELECT CONCAT('flyway_latest=', COALESCE(MAX(CAST(version AS UNSIGNED)), 0)) FROM flyway_schema_history WHERE success = 1; "
        "SELECT CONCAT('tables=', COUNT(*)) FROM information_schema.tables WHERE table_schema = DATABASE(); "
        "SELECT CONCAT('config:', config_key, '=', IF(config_value IS NULL OR config_value = '', '<empty>', '<set>')) "
        "FROM system_configs "
        f"WHERE config_key IN ({sql_in(all_config_keys)}) "
        "ORDER BY config_key; "
        "SELECT CONCAT('llm_route_scenes=', GROUP_CONCAT(DISTINCT scene ORDER BY scene SEPARATOR ',')) FROM llm_scene_routes; "
    )
    command = (
        f"mysql -u{wsl_quote(db_user)} -p{wsl_quote(db_password)} {wsl_quote(db_name)} "
        f"-N -e {wsl_quote(sql)}"
    )
    result = wsl_run(command, timeout=30)
    output = str(result["output"])
    flyway = 0
    flyway_latest = 0
    tables = 0
    configs: dict[str, str] = {}
    route_scenes: list[str] = []
    for line in output.splitlines():
        line = line.strip()
        if line.startswith("flyway="):
            flyway = int(line.split("=", 1)[1] or "0")
        elif line.startswith("flyway_latest="):
            flyway_latest = int(line.split("=", 1)[1] or "0")
        elif line.startswith("tables="):
            tables = int(line.split("=", 1)[1] or "0")
        elif line.startswith("config:"):
            key, value = line.removeprefix("config:").split("=", 1)
            configs[key] = value
        elif line.startswith("llm_route_scenes="):
            route_scenes = [scene for scene in line.split("=", 1)[1].split(",") if scene]
    add_check(
        checks,
        "wsl database schema",
        bool(result["ok"] and flyway >= 1 and tables >= 1),
        output.strip() or "no database output",
        data={"database": db_name, "flywayMigrations": flyway, "latestMigration": flyway_latest, "tableCount": tables},
    )
    missing_config_rows = [key for key in all_config_keys if key not in configs]
    add_check(
        checks,
        "llm scene config rows",
        not missing_config_rows,
        "missing config rows: " + ", ".join(missing_config_rows) if missing_config_rows else "all LLM scene config rows present",
        data={
            "sceneConfigs": {key: configs.get(key, "<missing>") for key in LLM_SCENE_CONFIG_KEYS},
            "missing": missing_config_rows,
        },
    )
    missing_enabled_defaults = [
        key for key in [
            "llm.reply_generation.enabled",
            "llm.profile_extraction.enabled",
            "llm.followup_suggestion.enabled",
            "llm.abnormal_detection.enabled",
            "llm.summary.enabled",
        ]
        if key not in configs
    ]
    enabled_scene_configs = [
        key for key in [
            "llm.reply_generation.enabled",
            "llm.profile_extraction.enabled",
            "llm.followup_suggestion.enabled",
            "llm.abnormal_detection.enabled",
            "llm.summary.enabled",
        ]
        if configs.get(key) == "<set>"
    ]
    add_check(
        checks,
        "llm scene switches",
        not missing_enabled_defaults,
        "enabled scene switches present; currently set: " + (", ".join(enabled_scene_configs) if enabled_scene_configs else "none")
        if not missing_enabled_defaults else "missing scene switches: " + ", ".join(missing_enabled_defaults),
        data={"enabledSwitches": enabled_scene_configs, "missing": missing_enabled_defaults},
    )
    add_check(
        checks,
        "llm route scene table",
        all(scene in LLM_SCENES for scene in route_scenes) or not route_scenes,
        "configured scenes: " + (", ".join(route_scenes) if route_scenes else "no explicit routes; active LLM environment/global fallback will be used"),
        required=False,
        data={"expectedScenes": LLM_SCENES, "configuredScenes": route_scenes},
    )
    missing = [key for key in REAL_EXTERNAL_CONFIG_KEYS if configs.get(key) in {"<empty>", "<missing>"}]
    add_check(
        checks,
        "real external config values",
        len(missing) == 0,
        "missing or empty: " + ", ".join(missing) if missing else "all configured",
        required=False,
        data={"configs": {key: configs.get(key, "<missing>") for key in REAL_EXTERNAL_CONFIG_KEYS}, "missing": missing},
    )


def check_runtime_pid(checks: list[dict[str, object]]) -> None:
    pid_file = ROOT / ".tools" / "runtime" / "backend.pid"
    if not pid_file.exists():
        add_check(checks, "runtime pid file", False, "backend.pid not found", required=False)
        return
    pid = pid_file.read_text(encoding="utf-8", errors="replace").strip()
    result = wsl_run(f"kill -0 {wsl_quote(pid)} 2>/dev/null", timeout=5)
    add_check(checks, "runtime pid file", bool(result["ok"]), f"pid={pid} {'running' if result['ok'] else 'stale'}", required=False)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--backend-url", default=DEFAULT_BACKEND_URL)
    parser.add_argument("--username", default="admin")
    parser.add_argument("--password", default="admin123")
    parser.add_argument("--db-name", default=DEFAULT_DB_NAME)
    parser.add_argument("--db-user", default=DEFAULT_DB_USER)
    parser.add_argument("--db-password", default=DEFAULT_DB_PASSWORD)
    parser.add_argument("--require-real-externals", action="store_true")
    args = parser.parse_args()

    checks: list[dict[str, object]] = []
    check_windows_tools(checks)
    check_wsl_tools(checks)
    check_runtime_pid(checks)
    check_backend(checks, args.backend_url, args.username, args.password)
    check_wsl_database(checks, args.db_name, args.db_user, args.db_password)

    if args.require_real_externals:
        for check in checks:
            if check["name"] == "real external config values":
                check["required"] = True

    required_failed = [check for check in checks if check["required"] and not check["ok"]]
    report = {
        "generatedAt": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
        "backendUrl": args.backend_url.rstrip("/"),
        "database": args.db_name,
        "passed": len(required_failed) == 0,
        "requireRealExternals": args.require_real_externals,
        "checks": checks,
        "notes": [
            "Windows does not need mvn/mysql on PATH when the WSL backend scripts are used.",
            "Empty external config values do not block local mock testing; pass --require-real-externals before real-provider acceptance.",
            "LLM scene config rows are required for local readiness, but every LLM business switch can remain disabled until real providers are configured.",
            "If backend auth config passes but database health fails, inspect .tools/runtime/backend*.log and WSL MariaDB service.",
        ],
    }
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    path = REPORT_DIR / "local_runtime_readiness.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    ok_count = sum(1 for check in checks if check["ok"])
    print(f"local_runtime_readiness_report={path}")
    print(f"passed={str(report['passed']).lower()} checks={ok_count}/{len(checks)} backend={report['backendUrl']} database={args.db_name}")
    for check in checks:
        if not check["ok"]:
            level = "failure" if check["required"] else "warning"
            print(f"{level}: {check['name']} {check['detail']}", file=sys.stderr)
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
