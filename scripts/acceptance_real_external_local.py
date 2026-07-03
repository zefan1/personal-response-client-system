#!/usr/bin/env python3
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.request
from dataclasses import dataclass
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
RUNTIME_DIR = ROOT / ".tools" / "runtime"
BASE_URL = os.environ.get("PDA_BASE_URL", "http://127.0.0.1:8081")
FAKE_URL = os.environ.get("PDA_FAKE_EXTERNAL_URL", "http://127.0.0.1:18080")
DB_NAME = os.environ.get("REAL_ACCEPTANCE_DB_NAME", "private_domain_assistant_real_acceptance")
DB_USER = os.environ.get("SMOKE_DB_USER", "pda_smoke")
DB_PASSWORD = os.environ.get("SMOKE_DB_PASSWORD", "pda_smoke_pwd")


@dataclass
class Check:
    name: str
    ok: bool
    status: int
    summary: str


class Api:
    def __init__(self, base_url):
        self.base_url = base_url.rstrip("/")
        self.token = None
        self.checks = []

    def request(self, name, method, path, body=None, token=True, expect_success=True, allow_status=None):
        if os.name == "nt" and os.environ.get("PDA_ACCEPTANCE_VIA_WSL", "true").lower() == "true":
            return self.request_via_wsl(name, method, path, body, token, expect_success, allow_status)
        headers = {}
        if token and self.token:
            headers["Authorization"] = f"Bearer {self.token}"
        data = None
        if body is not None:
            data = json.dumps(body, ensure_ascii=False).encode("utf-8")
            headers["Content-Type"] = "application/json"
        status = 0
        raw = ""
        payload = None
        try:
            req = urllib.request.Request(self.base_url + path, data=data, headers=headers, method=method)
            with urllib.request.urlopen(req, timeout=30) as resp:
                status = resp.status
                raw = resp.read().decode("utf-8", errors="replace")
        except urllib.error.HTTPError as ex:
            status = ex.code
            raw = ex.read().decode("utf-8", errors="replace")
        except Exception as ex:
            raw = str(ex)
        try:
            payload = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            payload = None
        success = payload.get("success") if isinstance(payload, dict) else None
        ok = status in set(allow_status or range(200, 300)) and isinstance(payload, dict)
        ok = ok and (success is True if expect_success else success is False)
        self.checks.append(Check(name, ok, status, summarize(payload if payload is not None else raw)))
        if not ok:
            raise AssertionError(f"{name} failed status={status} body={raw[:1000]}")
        return payload

    def request_via_wsl(self, name, method, path, body=None, token=True, expect_success=True, allow_status=None):
        request_file = None
        curl = ["curl", "-sS", "-X", method]
        if token and self.token:
            curl.extend(["-H", f"Authorization: Bearer {self.token}"])
        if body is not None:
            REPORT_DIR.mkdir(parents=True, exist_ok=True)
            safe_name = "".join(ch if ch.isalnum() else "_" for ch in name.lower())
            request_file = REPORT_DIR / f"{safe_name}.request.json"
            request_file.write_text(json.dumps(body, ensure_ascii=False), encoding="utf-8")
            curl.extend(["-H", "Content-Type: application/json", "--data-binary", "@" + wsl_path(request_file)])
        curl.extend(["-w", "\\n__STATUS__:%{http_code}", self.base_url + path])
        quoted = " ".join(shell_quote(part) for part in curl)
        result = subprocess.run(
            ["wsl", "-d", "Ubuntu", "--", "bash", "-lc", quoted],
            cwd=ROOT,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
        raw_output = (result.stdout or b"").decode("utf-8", errors="replace")
        stderr = (result.stderr or b"").decode("utf-8", errors="replace")
        marker = "\n__STATUS__:"
        if marker in raw_output:
            raw, status_text = raw_output.rsplit(marker, 1)
            try:
                status = int(status_text.strip().splitlines()[0])
            except ValueError:
                status = 0
        else:
            raw = raw_output + stderr
            status = 0
        try:
            payload = json.loads(raw) if raw.strip() else None
        except json.JSONDecodeError:
            payload = None
        success = payload.get("success") if isinstance(payload, dict) else None
        ok = status in set(allow_status or range(200, 300)) and isinstance(payload, dict)
        ok = ok and (success is True if expect_success else success is False)
        self.checks.append(Check(name, ok, status, summarize(payload if payload is not None else raw)))
        if not ok:
            raise AssertionError(f"{name} failed status={status} stderr={stderr[-500:]} body={raw[:1000]}")
        return payload

    def login(self):
        payload = self.request(
            "admin login",
            "POST",
            "/admin/api/v1/auth/login",
            {"username": "admin", "password": "admin123"},
            token=False,
        )
        data = payload.get("data") or {}
        self.token = data.get("accessToken") or data.get("token")
        if not self.token:
            raise AssertionError("admin login missing token")


def summarize(value):
    if isinstance(value, dict):
        data = value.get("data")
        if isinstance(data, dict):
            return "dataKeys=" + ",".join(list(data.keys())[:10])
        if isinstance(data, list):
            return f"dataList={len(data)}"
        return "keys=" + ",".join(list(value.keys())[:10])
    return str(value)[:240]


def wsl_path(path: Path):
    text = str(path).replace("\\", "/")
    if len(text) >= 2 and text[1] == ":":
        return f"/mnt/{text[0].lower()}{text[2:]}"
    return text


def shell_quote(value):
    return "'" + str(value).replace("'", "'\"'\"'") + "'"


def ps_kill_port(port):
    if os.name != "nt":
        return
    subprocess.run(
        [
            "powershell",
            "-NoProfile",
            "-Command",
            f"$c=Get-NetTCPConnection -LocalPort {port} -State Listen -ErrorAction SilentlyContinue; "
            "$c | ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }",
        ],
        check=False,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def start_fake_provider():
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    ps_kill_port(18080)
    subprocess.run(
        ["wsl", "-d", "Ubuntu", "--", "bash", "-lc", "fuser -k 18080/tcp 2>/dev/null || true; pkill -f fake_external_provider.py || true"],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    log = open(RUNTIME_DIR / "fake-external-provider.log", "w", encoding="utf-8")
    proc = subprocess.Popen(
        [
            "wsl",
            "-d",
            "Ubuntu",
            "--",
            "bash",
            "-lc",
            f"cd '{wsl_path(ROOT)}' && python3 scripts/fake_external_provider.py --host 127.0.0.1 --port 18080",
        ],
        cwd=ROOT,
        stdout=log,
        stderr=subprocess.STDOUT,
        text=True,
    )
    wait_wsl_url("http://127.0.0.1:18080/health", "fake provider", process=proc)
    return proc


def start_real_backend():
    RUNTIME_DIR.mkdir(parents=True, exist_ok=True)
    ps_kill_port(8081)
    subprocess.run(
        ["wsl", "-d", "Ubuntu", "--", "bash", "-lc", "fuser -k 8081/tcp 2>/dev/null || true; pkill -f private_domain_assistant_real_acceptance || true"],
        cwd=ROOT,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        check=False,
    )
    log = open(RUNTIME_DIR / "real-backend.log", "w", encoding="utf-8")
    script = f"""
set -euo pipefail
sudo service mariadb start >/dev/null 2>&1 || sudo /etc/init.d/mariadb start >/dev/null 2>&1
sudo service redis-server start >/dev/null 2>&1 || sudo /etc/init.d/redis-server start >/dev/null 2>&1
mysql -uroot <<SQL
CREATE DATABASE IF NOT EXISTS {DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '{DB_USER}'@'localhost' IDENTIFIED BY '{DB_PASSWORD}';
GRANT ALL PRIVILEGES ON {DB_NAME}.* TO '{DB_USER}'@'localhost';
FLUSH PRIVILEGES;
SQL
mysql -u'{DB_USER}' -p'{DB_PASSWORD}' -e "DROP DATABASE IF EXISTS {DB_NAME}; CREATE DATABASE {DB_NAME} CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
cd '{wsl_path(ROOT)}'
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/{DB_NAME}?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false' \
SPRING_DATASOURCE_USERNAME='{DB_USER}' \
SPRING_DATASOURCE_PASSWORD='{DB_PASSWORD}' \
MOCK_EXTERNALS=false \
SERVER_PORT='8081' \
MAVEN_OPTS='-Dstyle.color=never' \
mvn -Dstyle.color=never org.springframework.boot:spring-boot-maven-plugin:3.3.7:run
"""
    proc = subprocess.Popen(
        ["wsl", "-d", "Ubuntu", "--", "bash", "-lc", script],
        cwd=ROOT,
        stdout=log,
        stderr=subprocess.STDOUT,
        text=True,
    )
    wait_wsl_url(f"{BASE_URL}/api/v1/auth/config", "real backend", process=proc, attempts=150)
    return proc


def wait_url(url, label, process=None, attempts=60):
    last = ""
    for _ in range(attempts):
        if process is not None and process.poll() is not None:
            output = read_recent_runtime_logs()
            raise RuntimeError(f"{label} exited early code={process.returncode}\n{output[-4000:]}")
        try:
            with urllib.request.urlopen(url, timeout=2) as resp:
                if resp.status < 500:
                    return
        except Exception as ex:
            last = str(ex)
        time.sleep(1)
    raise TimeoutError(f"{label} not ready at {url}: {last}")


def wait_wsl_url(url, label, process=None, attempts=60):
    last = ""
    for _ in range(attempts):
        if process is not None and process.poll() is not None:
            output = read_recent_runtime_logs()
            raise RuntimeError(f"{label} exited early code={process.returncode}\n{output[-4000:]}")
        result = subprocess.run(
            ["wsl", "-d", "Ubuntu", "--", "bash", "-lc", f"curl -fsS '{url}' >/dev/null"],
            cwd=ROOT,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.PIPE,
        )
        if result.returncode == 0:
            return
        last = (result.stderr or b"").decode("utf-8", errors="replace").strip()
        time.sleep(1)
    raise TimeoutError(f"{label} not ready at {url}: {last}")


def read_recent_runtime_logs():
    chunks = []
    for name in ["real-backend.log", "fake-external-provider.log"]:
        path = RUNTIME_DIR / name
        if path.exists():
            chunks.append(f"\n--- {name} ---\n" + path.read_text(encoding="utf-8", errors="replace")[-4000:])
    return "\n".join(chunks)


def configure_external(api: Api):
    for key in [
        "skill.api_base_url",
        "image.api_base_url",
        "table.api_base_url",
    ]:
        api.request(f"configure {key}", "PUT", f"/admin/api/v1/configs/{key}", {"value": FAKE_URL})
        api.login()
    for key in ["skill.api_key", "image.api_key", "table.api_key"]:
        api.request(f"configure {key}", "PUT", f"/admin/api/v1/configs/{key}", {"value": "fake-acceptance-key"})
        api.login()


def first_id(api: Api, path, list_keys):
    payload = api.request(f"read {path}", "GET", path)
    data = payload.get("data")
    if isinstance(data, list) and data:
        return data[0]["id"]
    if isinstance(data, dict):
        for key in list_keys:
            value = data.get(key)
            if isinstance(value, list) and value:
                return value[0]["id"]
    raise AssertionError(f"No id found in {path}: {payload}")


def ensure_skill_binding(api: Api):
    payload = api.request("read skill bindings", "GET", "/admin/api/v1/skills")
    data = payload.get("data")
    if isinstance(data, list) and data:
        return data[0]["id"]
    created = api.request(
        "create acceptance skill binding",
        "POST",
        "/admin/api/v1/skills",
        {
            "skillId": "acceptance-skill",
            "skillName": "受控验收 Skill",
            "scene": "CHAT_RECOGNIZE",
            "leadType": "XIAN_SUO",
            "priority": 1,
        },
    )
    return (created.get("data") or {})["id"]


def ensure_environment(api: Api, kind: str):
    path = f"/admin/api/v1/{kind}-environments"
    payload = api.request(f"read {kind} environments", "GET", path)
    data = payload.get("data")
    if isinstance(data, list) and data:
        env_id = data[0]["id"]
    else:
        created = api.request(
            f"create {kind} environment",
            "POST",
            path,
            {
                "envName": f"受控验收 {kind}",
                "baseUrl": FAKE_URL,
                "apiKey": "fake-acceptance-key",
                "remark": "local non-mock acceptance",
            },
        )
        env_id = (created.get("data") or {})["id"]
    api.request(f"activate {kind} environment", "PUT", f"{path}/{env_id}/activate", {})
    api.login()
    return env_id


def ensure_datasource(api: Api):
    payload = api.request("read datasources", "GET", "/admin/api/v1/datasources")
    data = payload.get("data") or {}
    datasources = data.get("datasources") if isinstance(data, dict) else []
    if isinstance(datasources, list) and datasources:
        return datasources[0]["id"]
    created = api.request(
        "create acceptance datasource",
        "POST",
        "/admin/api/v1/datasources",
        {
            "name": "受控验收数据源",
            "sheetId": "acceptance-sheet",
            "sourceTable": "acceptance_customers",
            "description": "local non-mock acceptance datasource",
        },
    )
    datasource_id = (created.get("data") or {})["id"]
    api.request(
        "save acceptance datasource mappings",
        "PUT",
        f"/admin/api/v1/datasources/{datasource_id}/mappings",
        {
            "mappings": [
                {"sourceField": "phone", "targetField": "phone", "enabled": True},
                {"sourceField": "nickname", "targetField": "nickname", "enabled": True},
                {"sourceField": "lead_type", "targetField": "leadType", "enabled": True},
            ]
        },
    )
    return datasource_id


def run_acceptance(api: Api):
    api.login()
    configure_external(api)
    ensure_environment(api, "skill")
    skill_id = ensure_skill_binding(api)
    skill_test = api.request(
        "real skill http client test",
        "POST",
        f"/admin/api/v1/skills/{skill_id}/test",
        {"testMessage": "客户想了解产后修复，请生成跟进话术"},
    )
    suggestions = ((skill_test.get("data") or {}).get("suggestions") or [])
    if len(suggestions) < 1:
        raise AssertionError("skill test did not return suggestions")

    image_id = ensure_environment(api, "image")
    image_test = api.request("real image http client test", "POST", f"/admin/api/v1/image-environments/{image_id}/test", {})
    image_data = image_test.get("data") or {}
    if not image_data.get("success"):
        raise AssertionError(f"image environment test not successful: {image_data}")

    datasource_id = ensure_datasource(api)
    columns = api.request("real wecom sheet columns", "GET", f"/admin/api/v1/datasources/{datasource_id}/columns")
    columns_data = columns.get("data") or {}
    if columns_data.get("fetchStatus") != "OK" or columns_data.get("source") != "SHEET_SAMPLE":
        raise AssertionError(f"datasource columns did not use live SheetClient: {columns_data}")

    api.request(
        "real wecom table update",
        "POST",
        "/api/v1/customers/13900000001/save-to-table",
        {
            "sourceTable": "acceptance_customers",
            "sourceRowId": "acceptance_customers-row-001",
            "fields": {"nickname": "验收客户", "lead_type": "XIAN_SUO"},
        },
    )


def write_report(api: Api):
    REPORT_DIR.mkdir(parents=True, exist_ok=True)
    report = {
        "baseUrl": BASE_URL,
        "fakeProviderUrl": FAKE_URL,
        "mockExternals": False,
        "passed": all(check.ok for check in api.checks),
        "checks": [check.__dict__ for check in api.checks],
    }
    path = REPORT_DIR / "real_external_local.json"
    path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"real_external_local_report={path}")
    print(f"passed={str(report['passed']).lower()} checks={len(api.checks)}")


def stop_process(proc):
    if proc is None:
        return
    if proc.poll() is None:
        proc.terminate()
        try:
            proc.wait(timeout=10)
        except subprocess.TimeoutExpired:
            proc.kill()


def restore_mock_backend():
    if os.name == "nt":
        subprocess.run(
            ["wsl", "-d", "Ubuntu", "--", "bash", "-lc", f"cd '{wsl_path(ROOT)}' && bash scripts/start_backend_mock_wsl.sh"],
            cwd=ROOT,
            check=False,
        )


def main():
    fake_proc = None
    backend_proc = None
    api = Api(BASE_URL)
    try:
        fake_proc = start_fake_provider()
        backend_proc = start_real_backend()
        run_acceptance(api)
        write_report(api)
        return 0
    finally:
        stop_process(backend_proc)
        stop_process(fake_proc)
        restore_mock_backend()


if __name__ == "__main__":
    raise SystemExit(main())
