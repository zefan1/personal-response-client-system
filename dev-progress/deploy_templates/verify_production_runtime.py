#!/usr/bin/env python3
import json
import pathlib
import urllib.request


BASE_URL = "http://127.0.0.1:8080"
CREDENTIAL_FILE = pathlib.Path("/root/private-domain-assistant-initial-admin.txt")


def request(method, path, body=None, token=None):
  headers = {}
  data = None
  if body is not None:
    data = json.dumps(body).encode("utf-8")
    headers["Content-Type"] = "application/json"
  if token:
    headers["Authorization"] = f"Bearer {token}"
  req = urllib.request.Request(BASE_URL + path, data=data, headers=headers, method=method)
  with urllib.request.urlopen(req, timeout=20) as response:
    return response.status, json.loads(response.read().decode("utf-8"))


def main():
  credentials = {}
  for line in CREDENTIAL_FILE.read_text(encoding="utf-8").splitlines():
    key, value = line.split("=", 1)
    credentials[key] = value

  login_status, login = request(
      "POST",
      "/api/v1/auth/login",
      {"username": credentials["username"], "password": credentials["password"]},
  )
  token = login.get("data", {}).get("accessToken")
  if login_status != 200 or not login.get("success") or not token:
    raise SystemExit("production_login_failed=true")

  health_status, health = request("GET", "/admin/api/v1/health", token=token)
  if health_status != 200 or not health.get("success"):
    raise SystemExit("production_health_failed=true")

  data = health.get("data") or {}
  components = data.get("components") or {}
  print("production_login_passed=true")
  print(f"production_health_status={data.get('status')}")
  for name in sorted(components):
    component = components[name] or {}
    print(f"component_{name}={component.get('status')}")


if __name__ == "__main__":
  main()
