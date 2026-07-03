#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PID_FILE="$ROOT_DIR/.tools/runtime/backend.pid"

if [[ ! -f "$PID_FILE" ]]; then
  echo "backend_not_running"
  exit 0
fi

pid="$(cat "$PID_FILE" || true)"
if [[ -n "$pid" ]] && kill -0 "$pid" 2>/dev/null; then
  kill "$pid" 2>/dev/null || true
  sleep 2
  if kill -0 "$pid" 2>/dev/null; then
    kill -9 "$pid" 2>/dev/null || true
  fi
  echo "backend_stopped pid=${pid}"
else
  echo "backend_not_running"
fi

rm -f "$PID_FILE"
