#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

export SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/private_domain_assistant_smoke?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false'
export SPRING_DATASOURCE_USERNAME='pda_smoke'
export SPRING_DATASOURCE_PASSWORD='pda_smoke_pwd'
# Local development stays mock by default, but a controlled acceptance run may
# pass MOCK_EXTERNALS=false from the PowerShell launcher to use the real relay.
export MOCK_EXTERNALS="${MOCK_EXTERNALS:-true}"
export SERVER_PORT='8080'
export WECOM_TRANSPORT_MODE="${WECOM_TRANSPORT_MODE:-RELAY}"
export SPRING_FLYWAY_VALIDATE_ON_MIGRATE='false'

# Development startup does not need test bytecode. Keep an escape hatch for
# debugging a test-dependent runtime: MAVEN_DEV_SKIP_TESTS=false.
maven_test_skip="${MAVEN_DEV_SKIP_TESTS:-true}"

required_relay_variables=(WECOM_RELAY_BASE_URL WECOM_RELAY_KEY_ID WECOM_RELAY_SECRET)
missing_relay_variables=()
for variable_name in "${required_relay_variables[@]}"; do
  variable_value="${!variable_name:-}"
  if [[ -z "${variable_value//[[:space:]]/}" ]]; then
    missing_relay_variables+=("$variable_name")
  fi
done

if (( ${#missing_relay_variables[@]} > 0 )); then
  echo "backend_start_missing_relay_configuration variables=${missing_relay_variables[*]}" >&2
  echo "请用 tools/WecomSmartSheet.ps1 的 Start 模式启动，或先将已保存的中转配置注入当前进程。" >&2
  exit 2
fi

if [[ "$maven_test_skip" == "true" ]]; then
  exec mvn -Dmaven.test.skip=true -Dstyle.color=never org.springframework.boot:spring-boot-maven-plugin:3.3.7:run
fi

exec mvn -Dstyle.color=never org.springframework.boot:spring-boot-maven-plugin:3.3.7:run
