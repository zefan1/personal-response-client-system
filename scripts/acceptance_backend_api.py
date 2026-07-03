#!/usr/bin/env python3
import argparse
import json
import os
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass, field
from datetime import datetime, timedelta
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REPORT_DIR = ROOT / ".tools" / "acceptance"
BASE_URL = os.environ.get("PDA_BASE_URL", "http://127.0.0.1:8080")


@dataclass
class Result:
  name: str
  method: str
  path: str
  status: int
  ok: bool
  duration_ms: int
  success: bool | None = None
  error_code: str | None = None
  message: str | None = None
  summary: str | None = None


@dataclass
class Context:
  token: str | None = None
  refresh_token: str | None = None
  ts: str = field(default_factory=lambda: datetime.now().strftime("%Y%m%d%H%M%S"))
  created: dict = field(default_factory=dict)


class ApiClient:
  def __init__(self, base_url: str):
    self.base_url = base_url.rstrip("/")
    self.results: list[Result] = []

  def request(self, name, method, path, body=None, token=None, headers=None, files=None, expect_success=True, allow_status=None):
    allow_status = set(allow_status or range(200, 300))
    headers = dict(headers or {})
    if token:
      headers["Authorization"] = f"Bearer {token}"
    data = None
    url = self.base_url + path
    if files is not None:
      data, content_type = encode_multipart(files)
      headers["Content-Type"] = content_type
    elif body is not None:
      data = json.dumps(body).encode("utf-8")
      headers["Content-Type"] = "application/json"
    start = time.time()
    status = 0
    payload = None
    raw = ""
    try:
      req = urllib.request.Request(url, data=data, headers=headers, method=method)
      with urllib.request.urlopen(req, timeout=30) as resp:
        status = resp.status
        raw = resp.read().decode("utf-8", errors="replace")
    except urllib.error.HTTPError as ex:
      status = ex.code
      raw = ex.read().decode("utf-8", errors="replace")
    except Exception as ex:
      raw = str(ex)
    duration_ms = int((time.time() - start) * 1000)
    try:
      payload = json.loads(raw) if raw else None
    except json.JSONDecodeError:
      payload = None

    response_success = payload.get("success") if isinstance(payload, dict) else None
    error_code = payload.get("errorCode") if isinstance(payload, dict) else None
    message = payload.get("message") if isinstance(payload, dict) else None
    ok = status in allow_status and payload is not None
    if expect_success:
      ok = ok and response_success is True
    else:
      ok = ok and response_success is False
    result = Result(
        name=name,
        method=method,
        path=path,
        status=status,
        ok=ok,
        duration_ms=duration_ms,
        success=response_success,
        error_code=error_code,
        message=message,
        summary=summarize(payload if payload is not None else raw))
    self.results.append(result)
    if not ok:
      raise AssertionError(f"{name} failed: status={status} body={raw[:500]}")
    return payload


def summarize(value):
  if isinstance(value, dict):
    keys = ",".join(list(value.keys())[:8])
    data = value.get("data")
    if isinstance(data, dict):
      return f"keys={keys}; dataKeys={','.join(list(data.keys())[:8])}"
    if isinstance(data, list):
      return f"keys={keys}; dataList={len(data)}"
    return f"keys={keys}"
  if isinstance(value, list):
    return f"list={len(value)}"
  text = str(value)
  return text[:240]


def encode_multipart(files):
  boundary = "----pda-acceptance-%d" % int(time.time() * 1000)
  chunks = []
  for field, item in files.items():
    filename, content_type, content = item
    chunks.append(f"--{boundary}\r\n".encode())
    chunks.append(
        f'Content-Disposition: form-data; name="{field}"; filename="{filename}"\r\n'
        f"Content-Type: {content_type}\r\n\r\n".encode())
    chunks.append(content)
    chunks.append(b"\r\n")
  chunks.append(f"--{boundary}--\r\n".encode())
  return b"".join(chunks), f"multipart/form-data; boundary={boundary}"


def start_backend():
  if os.name == "nt":
    subprocess.run(["wsl", "-d", "Ubuntu", "--", "bash", "-lc", f"cd '{wsl_path(ROOT)}' && bash scripts/start_backend_mock_wsl.sh"],
        check=True)
  else:
    subprocess.run(["bash", str(ROOT / "scripts" / "start_backend_mock_wsl.sh")], cwd=ROOT, check=True)


def wsl_path(path: Path):
  text = str(path).replace("\\", "/")
  if len(text) >= 2 and text[1] == ":":
    drive = text[0].lower()
    return f"/mnt/{drive}{text[2:]}"
  return text


def data(payload):
  return payload.get("data") if isinstance(payload, dict) else None


def first_id_from_list(payload, list_key="list"):
  body = data(payload)
  if isinstance(body, dict):
    items = body.get(list_key) or body.get("items") or body.get("datasources") or body.get("categories")
  else:
    items = body
  if isinstance(items, list) and items:
    return items[0].get("id")
  return None


def auth_flow(api: ApiClient, ctx: Context):
  api.request("auth public config", "GET", "/api/v1/auth/config")
  login = api.request("admin login", "POST", "/admin/api/v1/auth/login",
      {"username": "admin", "password": "admin123"})
  body = data(login)
  ctx.token = body.get("accessToken") or body.get("token")
  ctx.refresh_token = body.get("refreshToken")
  if not ctx.token:
    raise AssertionError("login response missing accessToken/token")
  if ctx.refresh_token:
    api.request("auth refresh", "POST", "/api/v1/auth/refresh", {"refreshToken": ctx.refresh_token}, ctx.token)


def read_flows(api: ApiClient, ctx: Context):
  token = ctx.token
  for name, path in [
      ("health", "/admin/api/v1/health"),
      ("configs list", "/admin/api/v1/configs"),
      ("configs prefix", "/admin/api/v1/configs?prefix=skill."),
      ("skill env list", "/admin/api/v1/skill-environments"),
      ("image env list", "/admin/api/v1/image-environments"),
      ("prompt versions format", "/admin/api/v1/skill-prompt/format/versions"),
      ("datasource list", "/admin/api/v1/datasources"),
      ("customer fields", "/admin/api/v1/customer-fields"),
      ("datasource sync status", "/admin/api/v1/datasources/sync-status"),
      ("datasource import logs", "/admin/api/v1/datasources/import-logs"),
      ("quick search admin list", "/admin/api/v1/quick-search/items"),
      ("quick search desktop list", "/api/v1/quick-search/items"),
      ("rules list", "/admin/api/v1/rules"),
      ("tags list", "/admin/api/v1/tags/categories"),
      ("notices list", "/admin/api/v1/notices"),
      ("notices active", "/api/v1/notices/active"),
      ("audit logs list", "/admin/api/v1/audit-logs"),
      ("audit actions", "/admin/api/v1/audit-logs/actions"),
      ("versions list", "/admin/api/v1/versions"),
      ("desktop version check", "/api/v1/desktop/version-check?platform=WINDOWS&currentVersion=1.0.0&clientId=acceptance"),
      ("analytics overview", "/admin/api/v1/analytics/overview"),
      ("analytics funnels", "/admin/api/v1/analytics/funnels"),
      ("analytics staff", "/admin/api/v1/analytics/staff"),
      ("analytics sources", "/admin/api/v1/analytics/sources"),
      ("analytics stages", "/admin/api/v1/analytics/stages"),
      ("analytics health", "/admin/api/v1/analytics/health"),
      ("analytics lifecycle", "/admin/api/v1/analytics/lifecycle"),
      ("analytics risks", "/admin/api/v1/analytics/risks"),
      ("analytics content ranking", "/admin/api/v1/analytics/content-ranking"),
      ("skill calls analytics", "/admin/api/v1/analytics/skill-calls"),
      ("available skills", "/admin/api/v1/skills/available"),
      ("followups today", "/api/v1/followups/today"),
      ("customer search empty", "/api/v1/customers/search?q=13900000000"),
  ]:
    api.request(name, "GET", path, token=token)


def account_flow(api: ApiClient, ctx: Context):
  phone = "139%08d" % (int(ctx.ts[-8:]) % 100000000)
  created = api.request("account create leader", "POST", "/admin/api/v1/accounts", {
      "phone": phone,
      "password": "pass1234",
      "displayName": "验收组长",
      "role": "LEADER",
      "leaderId": None
  }, ctx.token)
  account_id = data(created)["id"]
  ctx.created["account_id"] = account_id
  api.request("account list filter", "GET", f"/admin/api/v1/accounts?keyword={phone}", token=ctx.token)
  api.request("account update", "PUT", f"/admin/api/v1/accounts/{account_id}", {
      "displayName": "验收组长改",
      "role": "LEADER",
      "leaderId": None,
      "isEnabled": True
  }, ctx.token)
  api.request("account toggle disabled", "PUT", f"/admin/api/v1/accounts/{account_id}/toggle", {"isEnabled": False}, ctx.token)
  api.request("account reset password", "PUT", f"/admin/api/v1/accounts/{account_id}/reset-password", {"newPassword": "pass5678"}, ctx.token)
  api.request("account delete", "DELETE", f"/admin/api/v1/accounts/{account_id}", token=ctx.token)


def skill_flow(api: ApiClient, ctx: Context):
  code = "acceptance" + ctx.ts[-6:]
  created = api.request("skill binding create", "POST", "/admin/api/v1/skills", {
      "skillId": code,
      "skillName": "验收技能",
      "scene": "OPENING",
      "leadType": "PENDING",
      "priority": 99
  }, ctx.token)
  skill_id = data(created)["id"]
  api.request("skill binding list filtered", "GET", "/admin/api/v1/skills?scene=OPENING&leadType=PENDING", token=ctx.token)
  api.request("skill binding update", "PUT", f"/admin/api/v1/skills/{skill_id}", {
      "skillId": code,
      "skillName": "验收技能改",
      "scene": "OPENING",
      "leadType": "PENDING",
      "priority": 98
  }, ctx.token)
  api.request("skill binding toggle", "PUT", f"/admin/api/v1/skills/{skill_id}/toggle", {"enabled": False}, ctx.token)
  api.request("skill binding delete", "DELETE", f"/admin/api/v1/skills/{skill_id}", token=ctx.token)


def ai_env_flow(api: ApiClient, ctx: Context):
  suffix = ctx.ts[-6:]
  for kind in ("skill", "image"):
    created = api.request(f"{kind} env create", "POST", f"/admin/api/v1/{kind}-environments", {
        "envName": f"acceptance-{kind}-{suffix}",
        "baseUrl": f"https://example.com/{kind}",
        "apiKey": f"key-{suffix}-{kind}"
    }, ctx.token)
    env_id = data(created)["id"]
    api.request(f"{kind} env update", "PUT", f"/admin/api/v1/{kind}-environments/{env_id}", {
        "envName": f"acceptance-{kind}-{suffix}-updated",
        "baseUrl": f"https://example.com/{kind}/updated",
        "apiKey": f"key-{suffix}-{kind}-updated"
    }, ctx.token)
    api.request(f"{kind} env activate", "PUT", f"/admin/api/v1/{kind}-environments/{env_id}/activate", token=ctx.token)


def datasource_flow(api: ApiClient, ctx: Context):
  source_table = "acceptance_" + ctx.ts
  name = "验收数据源-" + ctx.ts
  created = api.request("datasource create", "POST", "/admin/api/v1/datasources", {
      "name": name,
      "sheetId": "sheet-" + ctx.ts,
      "sourceTable": source_table,
      "description": "acceptance"
  }, ctx.token)
  ds_id = data(created)["id"]
  api.request("datasource update", "PUT", f"/admin/api/v1/datasources/{ds_id}", {
      "name": name + "-updated",
      "sheetId": "sheet-" + ctx.ts,
      "sourceTable": source_table,
      "description": "acceptance updated"
  }, ctx.token)
  api.request("datasource mappings get", "GET", f"/admin/api/v1/datasources/{ds_id}/mappings", token=ctx.token)
  api.request("datasource mappings save", "PUT", f"/admin/api/v1/datasources/{ds_id}/mappings", {
      "mappings": [
          {"id": None, "sourceField": "phone", "targetField": "phone", "enabled": True},
          {"id": None, "sourceField": "nickname", "targetField": "nickname", "enabled": True}
      ]
  }, ctx.token)
  api.request("datasource mapping versions", "GET", f"/admin/api/v1/datasources/{ds_id}/mappings/versions", token=ctx.token)
  api.request("datasource mapping restore", "POST", f"/admin/api/v1/datasources/{ds_id}/mappings/restore", {"version": 1}, ctx.token)
  compare_payload = api.request("datasource mapping compare", "GET", f"/admin/api/v1/datasources/{ds_id}/mappings/compare", token=ctx.token)
  compare_data = data(compare_payload)
  if not isinstance(compare_data.get("diff"), dict) or "summary" not in compare_data:
    raise AssertionError("datasource compare did not return structured diff")
  columns_payload = api.request("datasource columns", "GET", f"/admin/api/v1/datasources/{ds_id}/columns", token=ctx.token)
  columns_data = data(columns_payload)
  if not columns_data.get("columns"):
    raise AssertionError("datasource columns did not include mapped/source columns")
  api.request("datasource replace", "PUT", f"/admin/api/v1/datasources/{ds_id}/replace", {"sheetId": "sheet-replaced-" + ctx.ts}, ctx.token)
  api.request("datasource toggle off", "PUT", f"/admin/api/v1/datasources/{ds_id}/toggle", {"enabled": False}, ctx.token)
  api.request("datasource sync disabled fails", "POST", f"/admin/api/v1/datasources/{ds_id}/sync", token=ctx.token, expect_success=False, allow_status={409})
  api.request("datasource delete", "DELETE", f"/admin/api/v1/datasources/{ds_id}", token=ctx.token)
  csv = b"phone,nickname\n13900000001,acceptance\n"
  api.request("datasource csv import", "POST", "/admin/api/v1/datasources/import", token=ctx.token,
      files={"file": ("acceptance.csv", "text/csv", csv)})
  logs_payload = api.request("datasource import logs after csv", "GET", "/admin/api/v1/datasources/import-logs", token=ctx.token)
  logs_data = data(logs_payload)
  if logs_data.get("total", 0) < 1 or not logs_data.get("logs"):
    raise AssertionError("datasource import logs did not include persisted import records")


def quick_search_flow(api: ApiClient, ctx: Context):
  shortcut = "AC" + ctx.ts[-8:]
  created = api.request("quick search create", "POST", "/admin/api/v1/quick-search/items", {
      "contentType": "TEMPLATE",
      "leadType": "GENERAL",
      "title": "验收快捷",
      "shortcutCode": shortcut,
      "content": "验收话术",
      "imageUrl": None,
      "sortOrder": 999,
      "enabled": True
  }, ctx.token)
  item_id = data(created)["id"]
  api.request("quick search update", "PUT", f"/admin/api/v1/quick-search/items/{item_id}", {
      "title": "验收快捷改",
      "shortcutCode": shortcut,
      "content": "验收话术改",
      "sortOrder": 998,
      "enabled": True
  }, ctx.token)
  png = bytes.fromhex("89504e470d0a1a0a0000000d49484452")
  api.request("quick search image upload", "POST", "/admin/api/v1/upload/image", token=ctx.token,
      files={"file": ("acceptance.png", "image/png", png)})
  api.request("quick search toggle", "PUT", f"/admin/api/v1/quick-search/items/{item_id}/toggle", token=ctx.token)
  api.request("quick search delete", "DELETE", f"/admin/api/v1/quick-search/items/{item_id}", token=ctx.token)


def followup_flow(api: ApiClient, ctx: Context):
  condition = json.dumps({
      "conditions": [
          {"field": "leadType", "operator": "EQ", "value": "PENDING"}
      ]
  }, ensure_ascii=False)
  created = api.request("rule create", "POST", "/admin/api/v1/rules", {
      "name": "验收规则" + ctx.ts[-6:],
      "conditionJson": condition,
      "actionType": "ALERT",
      "actionConfig": "{\"level\":\"WARN\"}",
      "priority": 90,
      "enabled": True
  }, ctx.token)
  rule_id = data(created)["id"]
  api.request("rule update", "PUT", f"/admin/api/v1/rules/{rule_id}", {
      "name": "验收规则改" + ctx.ts[-6:],
      "conditionJson": condition,
      "actionType": "ALERT",
      "actionConfig": "{\"level\":\"WARN\"}",
      "priority": 91,
      "enabled": True
  }, ctx.token)
  api.request("rule toggle", "PUT", f"/admin/api/v1/rules/{rule_id}/toggle", {"enabled": False}, ctx.token)
  api.request("rule delete", "DELETE", f"/admin/api/v1/rules/{rule_id}", token=ctx.token)


def tag_flow(api: ApiClient, ctx: Context):
  fields_payload = api.request("tag customer fields for create", "GET", "/admin/api/v1/customer-fields", token=ctx.token)
  listed = api.request("tag list before create", "GET", "/admin/api/v1/tags/categories", token=ctx.token)
  categories = data(listed).get("categories", [])
  bound_fields = {item.get("boundField") for item in categories}
  available_fields = [item.get("fieldName") for item in data(fields_payload).get("fields", [])]
  bound_field = next((field for field in available_fields if field and field not in bound_fields), None)
  if not bound_field:
    api.request("tag duplicate category guard", "POST", "/admin/api/v1/tags/categories", {
        "categoryName": "验收标签" + ctx.ts[-4:],
        "boundField": categories[0]["boundField"],
        "isEnabled": True,
        "sortOrder": 999
    }, ctx.token, expect_success=False, allow_status={400})
    return
  created = api.request("tag category create", "POST", "/admin/api/v1/tags/categories", {
      "categoryName": "验收标签" + ctx.ts[-4:],
      "boundField": bound_field,
      "isEnabled": True,
      "sortOrder": 999
  }, ctx.token)
  cat_id = data(created)["id"]
  api.request("tag duplicate category guard", "POST", "/admin/api/v1/tags/categories", {
      "categoryName": "验收标签重复" + ctx.ts[-4:],
      "boundField": bound_field,
      "isEnabled": True,
      "sortOrder": 1000
  }, ctx.token, expect_success=False, allow_status={400})
  api.request("tag category update", "PUT", f"/admin/api/v1/tags/categories/{cat_id}", {
      "categoryName": "验收标签改" + ctx.ts[-4:],
      "boundField": bound_field,
      "isEnabled": True,
      "sortOrder": 998
  }, ctx.token)
  created_value = api.request("tag value create", "POST", "/admin/api/v1/tags/values", {
      "categoryId": cat_id,
      "tagValue": "ACCEPTANCE_" + ctx.ts[-6:],
      "displayName": "验收值",
      "isEnabled": True,
      "sortOrder": 999
  }, ctx.token)
  value_id = data(created_value)["id"]
  api.request("tag value update", "PUT", f"/admin/api/v1/tags/values/{value_id}", {
      "displayName": "验收值改",
      "isEnabled": True,
      "sortOrder": 998
  }, ctx.token)
  api.request("tag value toggle", "PUT", f"/admin/api/v1/tags/values/{value_id}/toggle", {"isEnabled": False}, ctx.token)
  api.request("tag value delete", "DELETE", f"/admin/api/v1/tags/values/{value_id}", token=ctx.token)
  api.request("tag category delete", "DELETE", f"/admin/api/v1/tags/categories/{cat_id}", token=ctx.token)


def notice_flow(api: ApiClient, ctx: Context):
  created = api.request("notice create immediate", "POST", "/admin/api/v1/notices", {
      "title": "验收公告" + ctx.ts[-6:],
      "content": "验收公告内容",
      "level": "INFO",
      "publishType": "IMMEDIATE",
      "publishAt": None,
      "expireDays": 1
  }, ctx.token)
  notice_id = data(created)["id"]
  api.request("notice stop", "PUT", f"/admin/api/v1/notices/{notice_id}/stop", token=ctx.token)
  api.request("notice delete", "DELETE", f"/admin/api/v1/notices/{notice_id}", token=ctx.token)
  publish_at = (datetime.now() + timedelta(minutes=5)).replace(microsecond=0).isoformat()
  scheduled = api.request("notice create scheduled", "POST", "/admin/api/v1/notices", {
      "title": "验收定时公告" + ctx.ts[-6:],
      "content": "验收定时公告内容",
      "level": "WARN",
      "publishType": "SCHEDULED",
      "publishAt": publish_at,
      "expireDays": 1
  }, ctx.token)
  scheduled_id = data(scheduled)["id"]
  api.request("notice update scheduled", "PUT", f"/admin/api/v1/notices/{scheduled_id}", {
      "title": "验收定时公告改" + ctx.ts[-6:],
      "content": "验收定时公告内容改",
      "level": "WARN",
      "publishAt": publish_at,
      "expireDays": 2
  }, ctx.token)
  api.request("notice stop scheduled", "PUT", f"/admin/api/v1/notices/{scheduled_id}/stop", token=ctx.token)
  api.request("notice delete scheduled", "DELETE", f"/admin/api/v1/notices/{scheduled_id}", token=ctx.token)


def version_flow(api: ApiClient, ctx: Context):
  version = f"9.{int(ctx.ts[-4:-2])}.{int(ctx.ts[-2:])}"
  created = api.request("version create", "POST", "/admin/api/v1/versions", {
      "version": version,
      "platform": "WINDOWS",
      "downloadUrl": "https://example.com/installer.exe",
      "changelog": "acceptance",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": 12345
  }, ctx.token)
  version_id = data(created)["id"]
  api.request("version update", "PUT", f"/admin/api/v1/versions/{version_id}", {
      "downloadUrl": "https://example.com/installer-updated.exe",
      "changelog": "acceptance updated",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": 23456
  }, ctx.token)
  exe = b"MZacceptance"
  api.request("version upload", "POST", "/admin/api/v1/versions/upload?platform=WINDOWS", token=ctx.token,
      files={"file": ("acceptance.exe", "application/octet-stream", exe)})
  api.request("version publish", "PUT", f"/admin/api/v1/versions/{version_id}/publish", token=ctx.token)
  api.request("desktop version report", "POST", "/api/v1/desktop/version-report", {
      "clientId": "acceptance-" + ctx.ts,
      "version": version,
      "platform": "WINDOWS",
      "osVersion": "Windows acceptance"
  }, ctx.token)
  api.request("version revoke", "PUT", f"/admin/api/v1/versions/{version_id}/revoke", {
      "reason": "acceptance cleanup",
      "alternativeVersion": None
  }, ctx.token)


def run_suite(api: ApiClient):
  ctx = Context()
  auth_flow(api, ctx)
  read_flows(api, ctx)
  for flow in [
      account_flow,
      skill_flow,
      ai_env_flow,
      datasource_flow,
      quick_search_flow,
      followup_flow,
      tag_flow,
      notice_flow,
      version_flow,
  ]:
    flow(api, ctx)
  return ctx


def write_report(api: ApiClient, failed=None):
  REPORT_DIR.mkdir(parents=True, exist_ok=True)
  report = {
      "baseUrl": BASE_URL,
      "generatedAt": datetime.now().isoformat(),
      "passed": sum(1 for item in api.results if item.ok),
      "failed": sum(1 for item in api.results if not item.ok),
      "fatal": str(failed) if failed else None,
      "results": [item.__dict__ for item in api.results],
  }
  path = REPORT_DIR / f"backend_api_acceptance_{datetime.now().strftime('%Y%m%d%H%M%S')}.json"
  path.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
  return path, report


def main():
  parser = argparse.ArgumentParser()
  parser.add_argument("--no-start", action="store_true", help="reuse an already running backend")
  args = parser.parse_args()
  if not args.no_start:
    start_backend()
  api = ApiClient(BASE_URL)
  failed = None
  try:
    run_suite(api)
  except Exception as ex:
    failed = ex
  path, report = write_report(api, failed)
  print(f"backend_api_acceptance_report={path}")
  print(f"passed={report['passed']} failed={report['failed']} total={len(api.results)}")
  if failed:
    print(f"fatal={failed}", file=sys.stderr)
    return 1
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
