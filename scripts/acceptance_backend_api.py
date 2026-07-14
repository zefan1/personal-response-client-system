#!/usr/bin/env python3
import argparse
import csv
import io
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
  coverage: str = "representative"
  success: bool | None = None
  error_code: str | None = None
  message: str | None = None
  summary: str | None = None


@dataclass
class RawResult:
  status: int
  body: str


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

  def request(self, name, method, path, body=None, token=None, headers=None, files=None, expect_success=True, allow_status=None, expect_error_code=None, assert_fn=None, coverage="representative"):
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
      if expect_error_code:
        ok = ok and error_code == expect_error_code
    assertion_error = None
    if ok and assert_fn is not None:
      try:
        assert_fn(payload)
      except AssertionError as ex:
        assertion_error = str(ex)
        ok = False
    result = Result(
        name=name,
        method=method,
        path=path,
        status=status,
        ok=ok,
        duration_ms=duration_ms,
        coverage=coverage,
        success=response_success,
        error_code=error_code,
        message=message,
        summary=summarize(payload if payload is not None else raw))
    self.results.append(result)
    if not ok:
      suffix = f" assertion={assertion_error}" if assertion_error else ""
      raise AssertionError(f"{name} failed: status={status} body={raw[:500]}{suffix}")
    return payload

  def request_raw(self, name, method, path, body=None, token=None, headers=None, allow_status=None):
    allow_status = set(allow_status or range(200, 300))
    headers = dict(headers or {})
    if token:
      headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
      data = json.dumps(body).encode("utf-8")
      headers["Content-Type"] = "application/json"
    start = time.time()
    status = 0
    raw = ""
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
    ok = status in allow_status and bool(raw)
    self.results.append(Result(
        name=name,
        method=method,
        path=path,
        status=status,
        ok=ok,
        duration_ms=int((time.time() - start) * 1000),
        coverage="download",
        summary=raw[:240]))
    if not ok:
      raise AssertionError(f"{name} failed: status={status} body={raw[:500]}")
    return RawResult(status, raw)


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


def require_data_keys(*keys):
  def check(payload):
    body = data(payload)
    if not isinstance(body, dict):
      raise AssertionError("data is not an object")
    missing = [key for key in keys if key not in body]
    if missing:
      raise AssertionError("missing data keys: " + ",".join(missing))
  return check


def require_data_object(min_keys=0):
  def check(payload):
    body = data(payload)
    if not isinstance(body, dict):
      raise AssertionError("data is not an object")
    if len(body) < min_keys:
      raise AssertionError(f"data object has too few keys: {len(body)} < {min_keys}")
  return check


def require_list_container(*possible_keys):
  def check(payload):
    body = data(payload)
    if isinstance(body, list):
      return
    if not isinstance(body, dict):
      raise AssertionError("data is not list/object")
    if possible_keys:
      found = [key for key in possible_keys if isinstance(body.get(key), list)]
      if not found:
        raise AssertionError("missing list container: " + ",".join(possible_keys))
    elif not any(isinstance(value, list) for value in body.values()):
      raise AssertionError("data object has no list value")
  return check


def require_data_contains(**expected):
  def check(payload):
    body = data(payload)
    if not isinstance(body, dict):
      raise AssertionError("data is not an object")
    mismatches = []
    for key, value in expected.items():
      if body.get(key) != value:
        mismatches.append(f"{key} expected {value!r} actual {body.get(key)!r}")
    if mismatches:
      raise AssertionError("; ".join(mismatches))
  return check


def require_list_item(match: dict, *possible_keys):
  def check(payload):
    body = data(payload)
    if isinstance(body, list):
      items = body
    elif isinstance(body, dict):
      items = None
      for key in possible_keys or ("list", "items", "datasources", "categories", "values", "logs", "versions", "records"):
        if isinstance(body.get(key), list):
          items = body.get(key)
          break
    else:
      items = None
    if not isinstance(items, list):
      raise AssertionError("response does not contain a list")
    for item in items:
      if isinstance(item, dict) and all(item.get(key) == value for key, value in match.items()):
        return
    raise AssertionError("list missing item: " + json.dumps(match, ensure_ascii=False))
  return check


def require_non_empty_list(*possible_keys):
  def check(payload):
    body = data(payload)
    if isinstance(body, list):
      items = body
    elif isinstance(body, dict):
      items = None
      for key in possible_keys or ("list", "items", "datasources", "categories", "logs", "versions"):
        if isinstance(body.get(key), list):
          items = body.get(key)
          break
    else:
      items = None
    if not isinstance(items, list) or not items:
      raise AssertionError("expected non-empty list")
  return check


def list_items_from_payload(payload, *possible_keys):
  body = data(payload)
  if isinstance(body, list):
    return body
  if isinstance(body, dict):
    for key in possible_keys or ("list", "items", "datasources", "categories", "values", "logs", "versions", "records"):
      if isinstance(body.get(key), list):
        return body.get(key)
  return None


def require_optional_list_item_keys(keys, *possible_keys):
  def check(payload):
    items = list_items_from_payload(payload, *possible_keys)
    if items is None:
      raise AssertionError("response does not contain a list")
    for index, item in enumerate(items):
      if not isinstance(item, dict):
        raise AssertionError(f"list item {index} is not an object")
      missing = [key for key in keys if key not in item]
      if missing:
        raise AssertionError(f"list item {index} missing keys: " + ",".join(missing))
  return check


def require_optional_list_enum(field, allowed, *possible_keys):
  def check(payload):
    items = list_items_from_payload(payload, *possible_keys)
    if items is None:
      raise AssertionError("response does not contain a list")
    invalid = []
    for index, item in enumerate(items):
      if isinstance(item, dict) and item.get(field) is not None and item.get(field) not in allowed:
        invalid.append(f"{index}:{item.get(field)}")
    if invalid:
      raise AssertionError(f"{field} has invalid values: " + ",".join(invalid))
  return check


def combine_assertions(*checks):
  def check(payload):
    for assertion in checks:
      assertion(payload)
  return check


def require_error(code):
  return {"expect_success": False, "expect_error_code": code}


def require_audit_csv(raw_body: str):
  text = raw_body.lstrip("\ufeff")
  rows = list(csv.reader(io.StringIO(text)))
  if not rows:
    raise AssertionError("audit export CSV has no rows")
  expected_header = ["操作时间", "操作人", "操作类型", "操作对象", "操作摘要", "详情"]
  if rows[0] != expected_header:
    raise AssertionError(f"audit export CSV header mismatch: {rows[0]}")
  if len(rows) < 2:
    raise AssertionError("audit export CSV has header but no data rows")
  for index, row in enumerate(rows[1:], start=2):
    if len(row) != len(expected_header):
      raise AssertionError(f"audit export CSV row {index} column count mismatch: {len(row)}")
    if not row[0].strip() or not row[1].strip() or not row[2].strip():
      raise AssertionError(f"audit export CSV row {index} missing required audit fields")


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
  api.request("auth public config", "GET", "/api/v1/auth/config", assert_fn=require_data_keys("captchaEnabled"))
  login = api.request("admin login", "POST", "/admin/api/v1/auth/login",
      {"username": "admin", "password": "admin123"}, assert_fn=require_data_keys("accessToken"))
  body = data(login)
  ctx.token = body.get("accessToken") or body.get("token")
  ctx.refresh_token = body.get("refreshToken")
  if not ctx.token:
    raise AssertionError("login response missing accessToken/token")
  if ctx.refresh_token:
    api.request("auth refresh", "POST", "/api/v1/auth/refresh", {"refreshToken": ctx.refresh_token}, ctx.token,
        assert_fn=require_data_keys("accessToken"))
  api.request("desktop login", "POST", "/api/v1/auth/login",
      {"username": "admin", "password": "admin123"}, assert_fn=require_data_keys("accessToken"))


def read_flows(api: ApiClient, ctx: Context):
  token = ctx.token
  for name, path, assert_fn in [
      ("health", "/admin/api/v1/health", require_data_keys("status")),
      ("configs list", "/admin/api/v1/configs", require_data_object(10)),
      ("configs prefix", "/admin/api/v1/configs?prefix=skill.", require_data_object(1)),
      ("config get table api url", "/admin/api/v1/configs/table.api_base_url", require_data_keys("configKey", "value")),
      ("skill env list", "/admin/api/v1/skill-environments", require_list_container("items", "list")),
      ("image env list", "/admin/api/v1/image-environments", require_list_container("items", "list")),
      ("prompt versions format", "/admin/api/v1/skill-prompt/format/versions", require_list_container("versions", "list", "items")),
      ("datasource list", "/admin/api/v1/datasources", require_optional_list_item_keys({"id", "name", "sheetId", "sourceTable", "enabled"}, "datasources", "list", "items")),
      ("customer fields", "/admin/api/v1/customer-fields", require_non_empty_list("fields", "list", "items")),
      ("datasource sync status", "/admin/api/v1/datasources/sync-status", require_optional_list_item_keys({"datasourceId", "sourceTable", "syncStatus", "mappingCount"}, "items", "list")),
      ("datasource import logs", "/admin/api/v1/datasources/import-logs", require_list_container("logs", "list", "items")),
      ("quick search admin list", "/admin/api/v1/quick-search/items", combine_assertions(
          require_optional_list_item_keys({"id", "contentType", "leadType", "title", "shortcutCode", "enabled"}, "items", "list"),
          require_optional_list_enum("contentType", {"TEMPLATE", "KNOWLEDGE", "LOCATION", "IMAGE", "MINI_PROGRAM"}, "items", "list"),
          require_optional_list_enum("leadType", {"GENERAL", "TUAN_GOU", "XIAN_SUO"}, "items", "list"))),
      ("quick search desktop list", "/api/v1/quick-search/items", require_list_container("items", "list")),
      ("desktop status", "/api/v1/desktop/status", require_data_keys("accountName", "skillStatus", "runtimeMode", "runtimeConfig")),
      ("rules list", "/admin/api/v1/rules", require_optional_list_item_keys({"id", "name", "actionType", "enabled"}, "rules", "items", "list")),
      ("tags list", "/admin/api/v1/tags/categories", require_optional_list_item_keys({"id", "categoryKey", "categoryName", "boundField", "values"}, "categories", "list")),
      ("notices list", "/admin/api/v1/notices", combine_assertions(
          require_optional_list_item_keys({"id", "title", "level", "status", "isStopped"}, "notices", "items", "list"),
          require_optional_list_enum("level", {"INFO", "WARN", "ERROR"}, "notices", "items", "list"),
          require_optional_list_enum("status", {"PUBLISHED", "SCHEDULED"}, "notices", "items", "list"))),
      ("notices active", "/api/v1/notices/active", require_list_container("notices", "items", "list")),
      ("audit logs list", "/admin/api/v1/audit-logs", require_optional_list_item_keys({"id", "action", "operator", "createdAt"}, "logs", "records", "items", "list")),
      ("audit actions", "/admin/api/v1/audit-logs/actions", require_list_container("actions", "items", "list")),
      ("versions list", "/admin/api/v1/versions", combine_assertions(
          require_optional_list_item_keys({"id", "version", "platform", "status", "updateStrategy"}, "versions", "items", "list"),
          require_optional_list_enum("platform", {"WINDOWS", "MAC"}, "versions", "items", "list"),
          require_optional_list_enum("status", {"DRAFT", "PUBLISHED", "REVOKED"}, "versions", "items", "list"))),
      ("desktop version check", "/api/v1/desktop/version-check?platform=WINDOWS&currentVersion=1.0.0&clientId=acceptance", require_data_keys("hasUpdate")),
      ("analytics overview", "/admin/api/v1/analytics/overview", require_data_keys("summary", "dailyTrend", "sceneBreakdown")),
      ("analytics funnels", "/admin/api/v1/analytics/funnels", require_data_keys("tuanGou", "xianSuo")),
      ("analytics staff", "/admin/api/v1/analytics/staff", require_list_container("staff", "items", "list")),
      ("analytics sources", "/admin/api/v1/analytics/sources", require_list_container("sources", "items", "list")),
      ("analytics stages", "/admin/api/v1/analytics/stages", require_list_container("stages", "items", "list")),
      ("analytics health", "/admin/api/v1/analytics/health", require_data_keys("summary", "systemAlerts")),
      ("analytics lifecycle", "/admin/api/v1/analytics/lifecycle", require_list_container("items", "list", "lifecycle")),
      ("analytics risks", "/admin/api/v1/analytics/risks", require_data_keys("customers", "alerts")),
      ("analytics content ranking", "/admin/api/v1/analytics/content-ranking", require_list_container("items", "list", "ranking")),
      ("skill calls analytics", "/admin/api/v1/analytics/skill-calls", require_data_keys("summary", "details")),
      ("available skills", "/admin/api/v1/skills/available", require_list_container("items", "list", "skills")),
      ("followups today", "/api/v1/followups/today", require_list_container("items", "list", "followups")),
      ("customer search empty", "/api/v1/customers/search?q=13900000000", require_list_container("items", "list", "customers")),
  ]:
    api.request(name, "GET", path, token=token, assert_fn=assert_fn, coverage="read")

  api.request("config update table api url", "PUT", "/admin/api/v1/configs/table.api_base_url", {"value": ""}, token=token,
      assert_fn=require_data_keys("configKey", "updated"), coverage="update")


def account_flow(api: ApiClient, ctx: Context):
  phone = "139%08d" % (int(ctx.ts[-8:]) % 100000000)
  created = api.request("account create leader", "POST", "/admin/api/v1/accounts", {
      "phone": phone,
      "password": "pass1234",
      "displayName": "验收组长",
      "role": "LEADER",
      "leaderId": None
  }, ctx.token, assert_fn=require_data_keys("id"))
  account_id = data(created)["id"]
  ctx.created["account_id"] = account_id
  api.request("account list filter", "GET", f"/admin/api/v1/accounts?keyword={phone}", token=ctx.token,
      assert_fn=require_list_item({"id": account_id, "phone": phone}))
  api.request("account update", "PUT", f"/admin/api/v1/accounts/{account_id}", {
      "displayName": "验收组长改",
      "role": "LEADER",
      "leaderId": None,
      "isEnabled": True
  }, ctx.token, assert_fn=require_data_contains(id=account_id, role="LEADER", isEnabled=True))
  api.request("account toggle disabled", "PUT", f"/admin/api/v1/accounts/{account_id}/toggle", {"isEnabled": False}, ctx.token,
      assert_fn=require_data_contains(id=account_id, isEnabled=False))
  api.request("account reset password", "PUT", f"/admin/api/v1/accounts/{account_id}/reset-password", {"newPassword": "pass5678"}, ctx.token)
  api.request("account delete", "DELETE", f"/admin/api/v1/accounts/{account_id}", token=ctx.token)

  leader_phone = "136%08d" % (int(ctx.ts[-8:]) % 100000000)
  leader = api.request("account create help leader", "POST", "/admin/api/v1/accounts", {
      "phone": leader_phone,
      "password": "pass1234",
      "displayName": "楠屾敹姹傚姪缁勯暱",
      "role": "LEADER",
      "leaderId": None
  }, ctx.token, assert_fn=require_data_keys("id"))
  leader_id = data(leader)["id"]
  keeper_phone = "135%08d" % (int(ctx.ts[-8:]) % 100000000)
  keeper = api.request("account create help keeper", "POST", "/admin/api/v1/accounts", {
      "phone": keeper_phone,
      "password": "pass1234",
      "displayName": "楠屾敹姹傚姪绠″",
      "role": "KEEPER",
      "leaderId": leader_id
  }, ctx.token, assert_fn=require_data_contains(role="KEEPER", leaderId=leader_id))
  ctx.created["help_leader_id"] = leader_id
  ctx.created["help_keeper_id"] = data(keeper)["id"]
  ctx.created["help_keeper_phone"] = keeper_phone
  ctx.created["help_keeper_password"] = "pass1234"


def customer_flow(api: ApiClient, ctx: Context):
  phone = "137%08d" % (int(ctx.ts[-8:]) % 100000000)
  known_phone = "139%08d" % ((int(ctx.ts[-8:]) + 1) % 100000000)
  api.request("customer profile not found", "GET", f"/api/v1/customers/{phone}", token=ctx.token, expect_success=False, allow_status={404})
  api.request("customer batch empty result", "POST", "/api/v1/customers/batch", {"phones": [phone]}, ctx.token)
  api.request("customer update not found", "PUT", f"/api/v1/customers/{phone}", {
      "version": 1,
      "fields": {"nickname": "acceptance"}
  }, ctx.token, expect_success=False, allow_status={400})
  api.request("customer suggestions empty resolve", "POST", f"/api/v1/customers/{phone}/suggestions/batch-resolve", {
      "action": "CONFIRM",
      "suggestionIds": [],
      "operator": "acceptance"
  }, ctx.token)
  api.request("customer save to table bad request", "POST", f"/api/v1/customers/{phone}/save-to-table", {
      "sourceTable": "",
      "sourceRowId": "",
      "fields": {}
  }, ctx.token, expect_success=False, allow_status={400})
  api.request("chat send confirm representative", "POST", "/api/v1/chat/send-confirm", {
      "phone": phone,
      "nickname": "acceptance",
      "isNewCustomer": False,
      "sourceTable": "",
      "leadType": "PENDING",
      "conversationSummary": "acceptance",
      "rawMessages": [],
      "sentText": "acceptance sent",
      "selectedDirection": "ACCEPTANCE",
      "followupSuggest": None
  }, ctx.token)
  api.request("chat recognize text representative", "POST", "/api/v1/chat/recognize", {
      "imageBase64": "",
      "textMessage": "客户想了解产后恢复，想预约到店评估",
      "customerIdentifier": "验收客户",
      "leadType": "XIAN_SUO",
      "sourceTable": "",
      "rawMessages": [
          {"role": "client", "text": "想了解产后恢复", "timestamp": None}
      ]
  }, ctx.token)
  csv = f"phone,nickname\n{known_phone},acceptance-known\n".encode("utf-8")
  api.request("customer fixture csv import", "POST", "/admin/api/v1/datasources/import", token=ctx.token,
      files={"file": ("acceptance-known-customer.csv", "text/csv", csv)})
  api.request("chat generate representative", "POST", "/api/v1/chat/generate", {
      "phone": known_phone,
      "scene": "ACTIVE_REPLY",
      "clientMessage": "想了解产后恢复"
  }, ctx.token)
  api.request("chat regenerate representative", "POST", "/api/v1/chat/regenerate", {
      "phone": known_phone
  }, ctx.token)


def skill_flow(api: ApiClient, ctx: Context):
  code = "acceptance" + ctx.ts[-6:]
  created = api.request("skill binding create", "POST", "/admin/api/v1/skills", {
      "skillId": code,
      "skillName": "验收技能",
      "scene": "OPENING",
      "leadType": "PENDING",
      "priority": 99
  }, ctx.token, assert_fn=require_data_contains(skillId=code, scene="OPENING", leadType="PENDING", priority=99))
  skill_id = data(created)["id"]
  api.request("skill binding list filtered", "GET", "/admin/api/v1/skills?scene=OPENING&leadType=PENDING", token=ctx.token,
      assert_fn=require_list_item({"id": skill_id, "skillId": code}))
  api.request("skill binding update", "PUT", f"/admin/api/v1/skills/{skill_id}", {
      "skillId": code,
      "skillName": "验收技能改",
      "scene": "OPENING",
      "leadType": "PENDING",
      "priority": 98
  }, ctx.token, assert_fn=require_data_contains(id=skill_id, skillId=code, priority=98))
  api.request("skill binding toggle", "PUT", f"/admin/api/v1/skills/{skill_id}/toggle", {"enabled": False}, ctx.token,
      assert_fn=require_data_contains(id=skill_id, enabled=False))
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


def prompt_flow(api: ApiClient, ctx: Context):
  value = "acceptance prompt " + ctx.ts
  api.request("prompt config snapshot update", "PUT", "/admin/api/v1/configs/skill.system_prompt_format", {"value": value}, token=ctx.token)
  versions = api.request("prompt versions after update", "GET", "/admin/api/v1/skill-prompt/format/versions", token=ctx.token)
  items = data(versions).get("versions", [])
  if not items:
    raise AssertionError("prompt versions did not include updated snapshot")
  version = items[0].get("version")
  api.request("prompt restore format", "POST", "/admin/api/v1/skill-prompt/format/restore", {
      "version": version,
      "operator": "acceptance"
  }, ctx.token)


def datasource_flow(api: ApiClient, ctx: Context):
  source_table = "acceptance_" + ctx.ts
  name = "验收数据源-" + ctx.ts
  created = api.request("datasource create", "POST", "/admin/api/v1/datasources", {
      "name": name,
      "sheetId": "sheet-" + ctx.ts,
      "sourceTable": source_table,
      "description": "acceptance"
  }, ctx.token, assert_fn=require_data_keys("id"))
  ds_id = data(created)["id"]
  api.request("datasource update", "PUT", f"/admin/api/v1/datasources/{ds_id}", {
      "name": name + "-updated",
      "sheetId": "sheet-" + ctx.ts,
      "sourceTable": source_table,
      "description": "acceptance updated"
  }, ctx.token, assert_fn=require_data_contains(id=ds_id, name=name + "-updated", sourceTable=source_table))
  api.request("datasource mappings get", "GET", f"/admin/api/v1/datasources/{ds_id}/mappings", token=ctx.token,
      assert_fn=require_list_container("mappings"))
  api.request("datasource mappings save", "PUT", f"/admin/api/v1/datasources/{ds_id}/mappings", {
      "mappings": [
          {"id": None, "sourceField": "phone", "targetField": "phone", "enabled": True},
          {"id": None, "sourceField": "nickname", "targetField": "nickname", "enabled": True}
      ]
  }, ctx.token, assert_fn=require_data_contains(mappingCount=2, version=1))
  api.request("datasource mapping versions", "GET", f"/admin/api/v1/datasources/{ds_id}/mappings/versions", token=ctx.token,
      assert_fn=require_non_empty_list("versions"))
  api.request("datasource mapping restore", "POST", f"/admin/api/v1/datasources/{ds_id}/mappings/restore", {"version": 1}, ctx.token)
  compare_payload = api.request("datasource mapping compare", "GET", f"/admin/api/v1/datasources/{ds_id}/mappings/compare", token=ctx.token)
  compare_data = data(compare_payload)
  if not isinstance(compare_data.get("diff"), dict) or "summary" not in compare_data:
    raise AssertionError("datasource compare did not return structured diff")
  columns_payload = api.request("datasource columns", "GET", f"/admin/api/v1/datasources/{ds_id}/columns", token=ctx.token)
  columns_data = data(columns_payload)
  if not columns_data.get("columns"):
    raise AssertionError("datasource columns did not include mapped/source columns")
  api.request("datasource replace", "PUT", f"/admin/api/v1/datasources/{ds_id}/replace", {"sheetId": "sheet-replaced-" + ctx.ts}, ctx.token,
      assert_fn=require_data_contains(mappingPreserved=True, newSheetId="sheet-replaced-" + ctx.ts, oldSheetId="sheet-" + ctx.ts))
  api.request("datasource toggle off", "PUT", f"/admin/api/v1/datasources/{ds_id}/toggle", {"enabled": False}, ctx.token,
      assert_fn=require_data_contains(id=ds_id, enabled=False))
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
  }, ctx.token, assert_fn=require_data_keys("id"))
  item_id = data(created)["id"]
  api.request("quick search update", "PUT", f"/admin/api/v1/quick-search/items/{item_id}", {
      "title": "验收快捷改",
      "shortcutCode": shortcut,
      "content": "验收话术改",
      "sortOrder": 998,
      "enabled": True
  }, ctx.token, assert_fn=require_data_contains(id=item_id, shortcutCode=shortcut, sortOrder=998, enabled=True))
  api.request("quick search admin list after update", "GET", "/admin/api/v1/quick-search/items", token=ctx.token,
      assert_fn=require_list_item({"id": item_id, "shortcutCode": shortcut}))
  png = bytes.fromhex("89504e470d0a1a0a0000000d49484452")
  api.request("quick search image upload", "POST", "/admin/api/v1/upload/image", token=ctx.token,
      files={"file": ("acceptance.png", "image/png", png)})
  api.request("quick search toggle", "PUT", f"/admin/api/v1/quick-search/items/{item_id}/toggle", token=ctx.token,
      assert_fn=require_data_keys("isEnabled"))
  api.request("quick search delete", "DELETE", f"/admin/api/v1/quick-search/items/{item_id}", token=ctx.token)


def followup_flow(api: ApiClient, ctx: Context):
  condition = json.dumps({
      "conditions": [
          {"field": "leadType", "op": "EQ", "value": "PENDING"}
      ]
  }, ensure_ascii=False)
  created = api.request("rule create", "POST", "/admin/api/v1/rules", {
      "name": "验收规则" + ctx.ts[-6:],
      "conditionJson": condition,
      "actionType": "ALERT",
      "actionConfig": "{\"level\":\"WARN\"}",
      "priority": 90,
      "enabled": True
  }, ctx.token, assert_fn=require_data_keys("id"))
  rule_id = data(created)["id"]
  api.request("rule update", "PUT", f"/admin/api/v1/rules/{rule_id}", {
      "name": "验收规则改" + ctx.ts[-6:],
      "conditionJson": condition,
      "actionType": "ALERT",
      "actionConfig": "{\"level\":\"WARN\"}",
      "priority": 91,
      "enabled": True
  }, ctx.token, assert_fn=require_data_contains(id=rule_id, priority=91, enabled=True))
  api.request("rule toggle", "PUT", f"/admin/api/v1/rules/{rule_id}/toggle", {"enabled": False}, ctx.token,
      assert_fn=require_data_contains(id=rule_id, enabled=False))
  api.request("rule delete", "DELETE", f"/admin/api/v1/rules/{rule_id}", token=ctx.token)


def tag_flow(api: ApiClient, ctx: Context):
  api.request("tag list before create", "GET", "/admin/api/v1/tags/categories", token=ctx.token)
  api.request("tag legacy binding guard", "POST", "/admin/api/v1/tags/categories", {
      "categoryName": "非法绑定标签" + ctx.ts[-4:],
      "boundField": "intentLevel",
      "isEnabled": True,
      "sortOrder": 1000
  }, ctx.token, expect_success=False, allow_status={400})
  created = api.request("tag category create", "POST", "/admin/api/v1/tags/categories", {
      "categoryName": "验收标签" + ctx.ts[-4:],
      "purpose": "标签管理验收分类",
      "isEnabled": True,
      "sortOrder": 999
  }, ctx.token)
  category = data(created)
  cat_id = category["id"]
  updated_category = api.request("tag category update", "PUT", f"/admin/api/v1/tags/categories/{cat_id}", {
      "categoryName": "验收标签改" + ctx.ts[-4:],
      "isEnabled": True,
      "sortOrder": 998,
      "version": category["version"]
  }, ctx.token)
  created_value = api.request("tag value create", "POST", "/admin/api/v1/tags/values", {
      "categoryId": cat_id,
      "tagValue": "ACCEPTANCE_" + ctx.ts[-6:],
      "displayName": "验收值",
      "isEnabled": True,
      "sortOrder": 999
  }, ctx.token)
  value = data(created_value)
  value_id = value["id"]
  updated_value = api.request("tag value update", "PUT", f"/admin/api/v1/tags/values/{value_id}", {
      "displayName": "验收值改",
      "isEnabled": True,
      "sortOrder": 998,
      "version": value["version"]
  }, ctx.token)
  api.request("tag value toggle", "PUT", f"/admin/api/v1/tags/values/{value_id}/toggle", {
      "isEnabled": False,
      "version": data(updated_value)["version"]
  }, ctx.token)
  api.request("tag value delete", "DELETE", f"/admin/api/v1/tags/values/{value_id}", token=ctx.token)
  api.request("tag category delete", "DELETE", f"/admin/api/v1/tags/categories/{cat_id}", token=ctx.token)


def help_flow(api: ApiClient, ctx: Context):
  keeper_phone = ctx.created.get("help_keeper_phone")
  keeper_password = ctx.created.get("help_keeper_password")
  if not keeper_phone or not keeper_password:
    raise AssertionError("help keeper fixture missing")
  login = api.request("help keeper login", "POST", "/api/v1/auth/login", {
      "username": keeper_phone,
      "password": keeper_password
  })
  keeper_token = data(login).get("accessToken")
  if not keeper_token:
    raise AssertionError("help keeper login missing token")
  requested = api.request("help request", "POST", "/api/v1/help/request", {
      "phone": "13900000001",
      "clientMessage": "客户需要更专业的产后修复解释",
      "aiSuggestions": [{"text": "建议到店评估", "direction": "INVITE", "reason": "acceptance"}],
      "keeperNote": "acceptance",
      "context": {"source": "acceptance"}
  }, keeper_token)
  help_id = data(requested).get("helpId")
  if not help_id:
    raise AssertionError("help request missing helpId")
  api.request("help resolve", "POST", "/api/v1/help/resolve", {
      "helpId": help_id,
      "replyText": "建议先安抚客户并预约到店评估",
      "helperReplies": [{"text": "先询问产后时间，再预约评估", "reason": "acceptance", "direction": "GUIDE"}]
  }, ctx.token)


def notice_flow(api: ApiClient, ctx: Context):
  created = api.request("notice create immediate", "POST", "/admin/api/v1/notices", {
      "title": "验收公告" + ctx.ts[-6:],
      "content": "验收公告内容",
      "level": "INFO",
      "publishType": "IMMEDIATE",
      "publishAt": None,
      "expireDays": 1
  }, ctx.token, assert_fn=require_data_contains(level="INFO", status="PUBLISHED"))
  notice_id = data(created)["id"]
  api.request("notice stop", "PUT", f"/admin/api/v1/notices/{notice_id}/stop", token=ctx.token,
      assert_fn=require_data_contains(id=notice_id, isStopped=True))
  api.request("notice delete", "DELETE", f"/admin/api/v1/notices/{notice_id}", token=ctx.token)
  publish_at = (datetime.now() + timedelta(minutes=5)).replace(microsecond=0).isoformat()
  scheduled = api.request("notice create scheduled", "POST", "/admin/api/v1/notices", {
      "title": "验收定时公告" + ctx.ts[-6:],
      "content": "验收定时公告内容",
      "level": "WARN",
      "publishType": "SCHEDULED",
      "publishAt": publish_at,
      "expireDays": 1
  }, ctx.token, assert_fn=require_data_contains(level="WARN", status="SCHEDULED"))
  scheduled_id = data(scheduled)["id"]
  api.request("notice update scheduled", "PUT", f"/admin/api/v1/notices/{scheduled_id}", {
      "title": "验收定时公告改" + ctx.ts[-6:],
      "content": "验收定时公告内容改",
      "level": "WARN",
      "publishAt": publish_at,
      "expireDays": 2
  }, ctx.token, assert_fn=require_data_contains(id=scheduled_id, level="WARN", status="SCHEDULED"))
  api.request("notice stop scheduled", "PUT", f"/admin/api/v1/notices/{scheduled_id}/stop", token=ctx.token,
      assert_fn=require_data_contains(id=scheduled_id, isStopped=True))
  api.request("notice delete scheduled", "DELETE", f"/admin/api/v1/notices/{scheduled_id}", token=ctx.token)


def audit_export_flow(api: ApiClient, ctx: Context):
  created = api.request("audit export create", "POST", "/admin/api/v1/audit-logs/export", {}, ctx.token)
  export_id = data(created).get("exportId")
  if not export_id:
    raise AssertionError("audit export did not return exportId")
  status_payload = None
  for _ in range(20):
    status_payload = api.request("audit export status", "GET", f"/admin/api/v1/audit-logs/export/{export_id}", token=ctx.token)
    if data(status_payload).get("terminal"):
      break
    time.sleep(0.5)
  status_data = data(status_payload)
  if status_data.get("status") != "COMPLETED":
    raise AssertionError(f"audit export did not complete: {status_data}")
  raw = api.request_raw("audit export download", "GET", f"/admin/api/v1/audit-logs/export/{export_id}/download", token=ctx.token)
  if raw.status != 200 or not raw.body.strip():
    raise AssertionError("audit export download returned empty body")
  require_audit_csv(raw.body)


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
  }, ctx.token, assert_fn=require_data_contains(version=version, platform="WINDOWS", status="DRAFT"))
  version_id = data(created)["id"]
  api.request("version update", "PUT", f"/admin/api/v1/versions/{version_id}", {
      "downloadUrl": "https://example.com/installer-updated.exe",
      "changelog": "acceptance updated",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": 23456
  }, ctx.token, assert_fn=require_data_contains(id=version_id, downloadUrl="https://example.com/installer-updated.exe", updateStrategy="OPTIONAL"))
  exe = b"MZacceptance"
  api.request("version upload", "POST", "/admin/api/v1/versions/upload?platform=WINDOWS", token=ctx.token,
      files={"file": ("acceptance.exe", "application/octet-stream", exe)})
  api.request("version publish", "PUT", f"/admin/api/v1/versions/{version_id}/publish", token=ctx.token,
      assert_fn=require_data_contains(id=version_id, status="PUBLISHED"))
  api.request("desktop version report", "POST", "/api/v1/desktop/version-report", {
      "clientId": "acceptance-" + ctx.ts,
      "version": version,
      "platform": "WINDOWS",
      "osVersion": "Windows acceptance"
  }, ctx.token, assert_fn=require_data_contains(ok=True))
  api.request("version revoke", "PUT", f"/admin/api/v1/versions/{version_id}/revoke", {
      "reason": "acceptance cleanup",
      "alternativeVersion": None
  }, ctx.token, assert_fn=require_data_contains(id=version_id, status="REVOKED"))
  draft_version = f"8.{int(ctx.ts[-4:-2])}.{int(ctx.ts[-2:])}"
  draft = api.request("version create draft for delete", "POST", "/admin/api/v1/versions", {
      "version": draft_version,
      "platform": "MAC",
      "downloadUrl": "https://example.com/installer.dmg",
      "changelog": "acceptance delete",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": 12345
  }, ctx.token, assert_fn=require_data_contains(version=draft_version, platform="MAC", status="DRAFT"))
  draft_id = data(draft)["id"]
  api.request("version delete draft", "DELETE", f"/admin/api/v1/versions/{draft_id}", token=ctx.token)


def negative_matrix_flow(api: ApiClient, ctx: Context):
  bad = require_error("80-10001")
  auth_failed = require_error("80-10002")
  forbidden = require_error("80-10003")
  conflict = require_error("80-10006")
  version_exists = require_error("80-10010")
  version_status_invalid = require_error("80-10011")
  version_package_missing = require_error("80-10012")

  api.request("negative unauthenticated admin health", "GET", "/admin/api/v1/health",
      expect_success=False, allow_status={401}, expect_error_code="80-10002", coverage="permission")
  api.request("negative malformed bearer token", "GET", "/admin/api/v1/health", token="not-a-real-token",
      expect_success=False, allow_status={401}, expect_error_code="80-10002", coverage="permission")
  api.request("negative bad admin login", "POST", "/admin/api/v1/auth/login",
      {"username": "admin", "password": "wrong-password"}, expect_success=False, allow_status={401},
      expect_error_code="80-10002", coverage="permission")

  keeper_phone = ctx.created.get("help_keeper_phone")
  keeper_password = ctx.created.get("help_keeper_password")
  keeper_login = api.request("negative keeper login fixture", "POST", "/api/v1/auth/login", {
      "username": keeper_phone,
      "password": keeper_password
  }, assert_fn=require_data_keys("accessToken"), coverage="permission")
  keeper_token = data(keeper_login).get("accessToken")
  api.request("negative keeper forbidden admin accounts", "GET", "/admin/api/v1/accounts", token=keeper_token,
      allow_status={403}, coverage="permission", **forbidden)
  api.request("negative keeper forbidden health", "GET", "/admin/api/v1/health", token=keeper_token,
      allow_status={403}, coverage="permission", **forbidden)

  api.request("negative account invalid phone", "POST", "/admin/api/v1/accounts", {
      "phone": "123",
      "password": "pass1234",
      "displayName": "bad phone",
      "role": "LEADER",
      "leaderId": None
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative account duplicate phone", "POST", "/admin/api/v1/accounts", {
      "phone": keeper_phone,
      "password": "pass1234",
      "displayName": "duplicate",
      "role": "KEEPER",
      "leaderId": ctx.created.get("help_leader_id")
  }, ctx.token, allow_status={400}, coverage="conflict", **bad)
  api.request("negative account weak password", "PUT", f"/admin/api/v1/accounts/{ctx.created.get('help_keeper_id')}/reset-password", {
      "newPassword": "123"
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative account delete leader with keepers", "DELETE", f"/admin/api/v1/accounts/{ctx.created.get('help_leader_id')}",
      token=ctx.token, allow_status={400}, coverage="conflict", **bad)

  api.request("negative analytics invalid lead type", "GET", "/admin/api/v1/analytics/overview?leadType=GENERAL",
      token=ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative chat recognize missing input", "POST", "/api/v1/chat/recognize", {
      "imageBase64": "",
      "textMessage": "",
      "customerIdentifier": "",
      "leadType": "PENDING",
      "sourceTable": "",
      "rawMessages": []
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative customer batch empty phones", "POST", "/api/v1/customers/batch", {
      "phones": []
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)

  dup_skill = "dup-" + ctx.ts[-6:]
  api.request("negative skill duplicate fixture", "POST", "/admin/api/v1/skills", {
      "skillId": dup_skill,
      "skillName": "duplicate fixture",
      "scene": "OPENING",
      "leadType": "PENDING",
      "priority": 7
  }, ctx.token, assert_fn=require_data_keys("id"), coverage="create")
  api.request("negative skill duplicate", "POST", "/admin/api/v1/skills", {
      "skillId": dup_skill,
      "skillName": "duplicate fixture",
      "scene": "OPENING",
      "leadType": "PENDING",
      "priority": 6
  }, ctx.token, allow_status={400}, coverage="conflict", **bad)
  api.request("negative skill invalid lead type", "POST", "/admin/api/v1/skills", {
      "skillId": "bad-" + ctx.ts[-6:],
      "skillName": "bad lead type",
      "scene": "OPENING",
      "leadType": "GENERAL",
      "priority": 1
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative skill env invalid url", "POST", "/admin/api/v1/skill-environments", {
      "envName": "bad-url-" + ctx.ts[-6:],
      "baseUrl": "not-a-url",
      "apiKey": "secret"
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative image env missing api key", "POST", "/admin/api/v1/image-environments", {
      "envName": "missing-key-" + ctx.ts[-6:],
      "baseUrl": "https://example.com/image",
      "apiKey": ""
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative prompt unsupported type", "GET", "/admin/api/v1/skill-prompt/unknown/versions",
      token=ctx.token, allow_status={400}, coverage="invalid", **bad)

  api.request("negative config unknown key", "PUT", "/admin/api/v1/configs/not.a.real.key", {
      "value": "x"
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative datasource create missing name", "POST", "/admin/api/v1/datasources", {
      "name": "",
      "sheetId": "sheet-" + ctx.ts,
      "sourceTable": "bad_source",
      "description": ""
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  ds = api.request("negative datasource mapping fixture", "POST", "/admin/api/v1/datasources", {
      "name": "negative datasource " + ctx.ts,
      "sheetId": "sheet-neg-" + ctx.ts,
      "sourceTable": "negative_" + ctx.ts,
      "description": "negative"
  }, ctx.token, assert_fn=require_data_keys("id"), coverage="create")
  ds_id = data(ds)["id"]
  api.request("negative datasource duplicate mapping target", "PUT", f"/admin/api/v1/datasources/{ds_id}/mappings", {
      "mappings": [
          {"sourceField": "phone1", "targetField": "phone", "enabled": True},
          {"sourceField": "phone2", "targetField": "phone", "enabled": True}
      ]
  }, ctx.token, allow_status={409}, coverage="conflict", **conflict)

  shortcut = "NEG" + ctx.ts[-6:]
  api.request("negative quick search fixture", "POST", "/admin/api/v1/quick-search/items", {
      "contentType": "TEMPLATE",
      "leadType": "GENERAL",
      "title": "negative fixture",
      "shortcutCode": shortcut,
      "content": "negative",
      "imageUrl": None,
      "sortOrder": 1,
      "enabled": True
  }, ctx.token, assert_fn=require_data_keys("id"), coverage="create")
  api.request("negative quick search duplicate shortcut", "POST", "/admin/api/v1/quick-search/items", {
      "contentType": "TEMPLATE",
      "leadType": "GENERAL",
      "title": "negative duplicate",
      "shortcutCode": shortcut,
      "content": "negative",
      "imageUrl": None,
      "sortOrder": 1,
      "enabled": True
  }, ctx.token, allow_status={400}, coverage="conflict", expect_success=False, expect_error_code="80-10007")
  api.request("negative quick search invalid lead type", "POST", "/admin/api/v1/quick-search/items", {
      "contentType": "TEMPLATE",
      "leadType": "PENDING",
      "title": "bad lead",
      "shortcutCode": "BAD" + ctx.ts[-6:],
      "content": "bad",
      "imageUrl": None,
      "sortOrder": 1,
      "enabled": True
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative quick search image upload bad extension", "POST", "/admin/api/v1/upload/image", token=ctx.token,
      files={"file": ("bad.txt", "text/plain", b"not an image")}, allow_status={400}, coverage="invalid", **bad)

  no_package_version = f"7.{int(ctx.ts[-4:-2])}.{int(ctx.ts[-2:])}"
  api.request("negative version missing package", "POST", "/admin/api/v1/versions", {
      "version": no_package_version,
      "platform": "WINDOWS",
      "downloadUrl": "",
      "changelog": "negative",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": None
  }, ctx.token, allow_status={400}, coverage="invalid", **version_package_missing)
  dup_version = f"6.{int(ctx.ts[-4:-2])}.{int(ctx.ts[-2:])}"
  created = api.request("negative version duplicate fixture", "POST", "/admin/api/v1/versions", {
      "version": dup_version,
      "platform": "WINDOWS",
      "downloadUrl": "https://example.com/negative.exe",
      "changelog": "negative",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": 100
  }, ctx.token, assert_fn=require_data_keys("id"), coverage="create")
  version_id = data(created)["id"]
  api.request("negative version duplicate", "POST", "/admin/api/v1/versions", {
      "version": dup_version,
      "platform": "WINDOWS",
      "downloadUrl": "https://example.com/negative.exe",
      "changelog": "negative",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": 100
  }, ctx.token, allow_status={400}, coverage="conflict", **version_exists)
  api.request("negative version publish fixture", "PUT", f"/admin/api/v1/versions/{version_id}/publish",
      token=ctx.token, assert_fn=require_data_keys("id", "status"), coverage="update")
  api.request("negative version edit published", "PUT", f"/admin/api/v1/versions/{version_id}", {
      "downloadUrl": "https://example.com/negative-updated.exe",
      "changelog": "should fail",
      "updateStrategy": "OPTIONAL",
      "gradualPercent": None,
      "fileSize": 200
  }, ctx.token, allow_status={409}, coverage="conflict", **version_status_invalid)
  api.request("negative version delete published", "DELETE", f"/admin/api/v1/versions/{version_id}",
      token=ctx.token, allow_status={409}, coverage="conflict", **version_status_invalid)
  api.request("negative desktop version check missing client", "GET", "/api/v1/desktop/version-check?platform=WINDOWS&currentVersion=1.0.0",
      token=ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative desktop version report missing fields", "POST", "/api/v1/desktop/version-report", {
      "clientId": "",
      "version": "",
      "platform": "WINDOWS"
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)

  api.request("negative notice schedule missing publishAt", "POST", "/admin/api/v1/notices", {
      "title": "negative notice",
      "content": "negative",
      "level": "INFO",
      "publishType": "SCHEDULED",
      "publishAt": None,
      "expireDays": 1
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative notice expire days too high", "POST", "/admin/api/v1/notices", {
      "title": "negative notice expire",
      "content": "negative",
      "level": "INFO",
      "publishType": "IMMEDIATE",
      "publishAt": None,
      "expireDays": 99
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative notice scheduled publishAt too soon", "POST", "/admin/api/v1/notices", {
      "title": "negative notice soon",
      "content": "negative",
      "level": "INFO",
      "publishType": "SCHEDULED",
      "publishAt": datetime.now().isoformat(),
      "expireDays": 1
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative help request empty content", "POST", "/api/v1/help/request", {
      "customerPhone": "",
      "question": "",
      "context": "",
      "keeperNote": ""
  }, ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative audit invalid date range", "GET", "/admin/api/v1/audit-logs?startDate=2099-01-02&endDate=2099-01-01",
      token=ctx.token, allow_status={400}, coverage="invalid", **bad)
  api.request("negative audit missing export", "GET", "/admin/api/v1/audit-logs/export/not-real-export",
      token=ctx.token, allow_status={400}, coverage="invalid", **bad)


def run_suite(api: ApiClient):
  ctx = Context()
  auth_flow(api, ctx)
  read_flows(api, ctx)
  for flow in [
      account_flow,
      customer_flow,
      skill_flow,
      ai_env_flow,
      prompt_flow,
      datasource_flow,
      quick_search_flow,
      followup_flow,
      tag_flow,
      help_flow,
      notice_flow,
      audit_export_flow,
      version_flow,
      negative_matrix_flow,
  ]:
    flow(api, ctx)
  return ctx


def write_report(api: ApiClient, failed=None):
  REPORT_DIR.mkdir(parents=True, exist_ok=True)
  coverage_counts: dict[str, int] = {}
  for item in api.results:
    coverage_counts[item.coverage] = coverage_counts.get(item.coverage, 0) + 1
  report = {
      "baseUrl": BASE_URL,
      "generatedAt": datetime.now().isoformat(),
      "passed": sum(1 for item in api.results if item.ok),
      "failed": sum(1 for item in api.results if not item.ok),
      "coverageCounts": coverage_counts,
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
  print("coverage=" + ",".join(f"{key}:{value}" for key, value in sorted(report["coverageCounts"].items())))
  if failed:
    print(f"fatal={failed}", file=sys.stderr)
    return 1
  return 0


if __name__ == "__main__":
  raise SystemExit(main())
