#!/usr/bin/env bash
set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEST_ROOT="$(mktemp -d)"
trap 'rm -rf "$TEST_ROOT"' EXIT

mkdir -p "$TEST_ROOT/project/scripts" "$TEST_ROOT/bin"
cp "$PROJECT_ROOT/scripts/start_backend_real_wsl.sh" "$TEST_ROOT/project/scripts/"
ln -s "$(type -P true)" "$TEST_ROOT/bin/curl"

required_wecom_variables=(
  WECOM_CORP_ID
  WECOM_APP_SECRET
  WECOM_SMARTSHEET_DOC_ID
  WECOM_SMARTSHEET_SHEET_ID
  WECOM_SMARTSHEET_VIEW_ID
  WECOM_SMARTSHEET_SOURCE_TABLE
  WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE
)
configured_wecom_environment=()
for variable_name in "${required_wecom_variables[@]}"; do
  configured_wecom_environment+=("${variable_name}=sentinel-${variable_name}")
done

assert_missing_configuration_rejected() {
  local missing_variable="$1"
  shift
  local output status assignment
  local test_environment=()
  for assignment in "${configured_wecom_environment[@]}"; do
    if [[ "$assignment" != "${missing_variable}="* ]]; then
      test_environment+=("$assignment")
    fi
  done
  set +e
  output="$(PATH="$TEST_ROOT/bin:/usr/bin:/bin" \
    env -u "$missing_variable" "${test_environment[@]}" "$@" \
    bash "$TEST_ROOT/project/scripts/start_backend_real_wsl.sh" 2>&1)"
  status=$?
  set -e

  if [[ $status -ne 2 ]]; then
    echo "expected missing WeCom configuration to exit 2 for $missing_variable"
    echo "$output"
    exit 1
  fi
  if [[ "$output" != *"backend_start_missing_wecom_configuration variables=$missing_variable"* ]]; then
    echo "expected an actionable missing WeCom configuration error for $missing_variable"
    echo "$output"
    exit 1
  fi
  if [[ "$output" == *"sentinel-"* ]]; then
    echo "expected missing configuration error not to expose configured values"
    echo "$output"
    exit 1
  fi
}

for variable_name in "${required_wecom_variables[@]}"; do
  assert_missing_configuration_rejected "$variable_name"
done
assert_missing_configuration_rejected WECOM_APP_SECRET WECOM_APP_SECRET="   "

set +e
output="$(PATH="$TEST_ROOT/bin:/usr/bin:/bin" \
  WECOM_CORP_ID=test-corp \
  WECOM_APP_SECRET=test-secret \
  WECOM_SMARTSHEET_DOC_ID=test-doc \
  WECOM_SMARTSHEET_SHEET_ID=test-sheet \
  WECOM_SMARTSHEET_VIEW_ID=test-view \
  WECOM_SMARTSHEET_SOURCE_TABLE=test-source \
  WECOM_SMARTSHEET_UNIQUE_FIELD_TITLE=test-phone \
  bash "$TEST_ROOT/project/scripts/start_backend_real_wsl.sh" 2>&1)"
status=$?
set -e

if [[ $status -eq 0 ]]; then
  echo "expected an unknown backend listener to fail startup"
  echo "$output"
  exit 1
fi

if [[ "$output" != *"backend_port_in_use_unverified"* ]]; then
  echo "expected an actionable unknown-listener error"
  echo "$output"
  exit 1
fi

echo "start_backend_real_wsl unknown-listener test passed"
