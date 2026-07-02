from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "dev-progress/01B_progress.md",
    "src/main/resources/db/migration/V3__module_b_skill_gateway.sql",
    "src/main/java/com/privateflow/modules/skill/Scene.java",
    "src/main/java/com/privateflow/modules/skill/SkillGatewayService.java",
    "src/main/java/com/privateflow/modules/skill/SkillRequest.java",
    "src/main/java/com/privateflow/modules/skill/SkillResponse.java",
    "src/main/java/com/privateflow/modules/skill/ProfileExtractRequest.java",
    "src/main/java/com/privateflow/modules/skill/ProfileUpdates.java",
    "src/main/java/com/privateflow/modules/skill/config/SkillConfig.java",
    "src/main/java/com/privateflow/modules/skill/config/SkillConfigProvider.java",
    "src/main/java/com/privateflow/modules/skill/client/DefaultSkillHttpClient.java",
    "src/main/java/com/privateflow/modules/skill/client/MockSkillHttpClient.java",
    "src/main/java/com/privateflow/modules/skill/parser/SkillResponseParser.java",
    "src/main/java/com/privateflow/modules/skill/circuit/SkillCircuitBreaker.java",
    "src/main/java/com/privateflow/modules/skill/service/SkillRequestBuilder.java",
    "src/main/java/com/privateflow/modules/skill/service/SkillFallbackHandler.java",
    "src/main/java/com/privateflow/modules/skill/service/SkillGatewayServiceImpl.java",
    "src/main/java/com/privateflow/modules/skill/infra/SkillCallLogger.java",
]

scene_values = ["CHAT_RECOGNIZE", "ACTIVE_REPLY", "REGENERATE", "PROFILE_EXTRACT", "OPENING"]
skill_keys = [
    "skill.api_base_url",
    "skill.api_key",
    "skill.phone_transfer_mode",
    "skill.phone_encryption_key",
    "skill.timeout_ms",
    "skill.circuit_breaker_window_s",
    "skill.circuit_breaker_failure_rate",
    "skill.circuit_breaker_min_calls",
    "skill.circuit_breaker_open_s",
    "skill.fallback_reply",
    "skill.tuan_skill_group_id",
    "skill.xiansuo_skill_group_id",
    "skill.default_skill_id",
    "skill.system_prompt_template",
    "skill.red_lines",
    "skill.alert_failure_rate",
    "skill.alert_failure_duration_minutes",
]

errors = []
for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

scene = (ROOT / "src/main/java/com/privateflow/modules/skill/Scene.java").read_text(encoding="utf-8")
for value in scene_values:
    if value not in scene:
        errors.append(f"Scene enum missing {value}")

sql = (ROOT / "src/main/resources/db/migration/V3__module_b_skill_gateway.sql").read_text(encoding="utf-8")
for table in ["skill_call_logs", "skill_scene_bindings", "personality_tags"]:
    if f"CREATE TABLE IF NOT EXISTS {table}" not in sql:
        errors.append(f"migration missing table {table}")
for index in ["idx_caller_time", "idx_success_time", "idx_scene", "idx_lead_type", "idx_scene_lead", "idx_tag_value", "idx_enabled_sort"]:
    if index not in sql:
        errors.append(f"migration missing index {index}")
for key in skill_keys:
    if key not in sql:
        errors.append(f"migration missing skill config {key}")
if "profile.extract_timeout_ms" not in sql:
    errors.append("migration missing profile.extract_timeout_ms")

service = (ROOT / "src/main/java/com/privateflow/modules/skill/service/SkillGatewayServiceImpl.java").read_text(encoding="utf-8")
for token in ["generateReplies", "extractProfile", "fallbackHandler.fallback()", "ProfileUpdates.empty()", "circuitBreaker.allowRequest()", "circuitBreaker.recordSuccess()", "circuitBreaker.recordFailure()", "callLogger.logCall"]:
    if token not in service:
        errors.append(f"SkillGatewayServiceImpl missing {token}")

builder = (ROOT / "src/main/java/com/privateflow/modules/skill/service/SkillRequestBuilder.java").read_text(encoding="utf-8")
for token in ["customerQueryService.getByPhone", "toSnakeCase", "LAST_FOUR", "{{red_lines}}", "{{available_tags}}", "{{scene}}", "skill_group_id"]:
    if token not in builder:
        errors.append(f"SkillRequestBuilder missing {token}")

parser = (ROOT / "src/main/java/com/privateflow/modules/skill/parser/SkillResponseParser.java").read_text(encoding="utf-8")
for token in ["REPEATED_", "suggestions", "customer_analysis", "followup_suggest", "profile_updates", "ProfileUpdates.empty"]:
    if token not in parser:
        errors.append(f"SkillResponseParser missing {token}")

client = (ROOT / "src/main/java/com/privateflow/modules/skill/client/DefaultSkillHttpClient.java").read_text(encoding="utf-8")
for token in ["/v1/chat/completions", "Authorization", "application/json", "HttpTimeoutException", "SKILL_API_KEY_INVALID"]:
    if token not in client:
        errors.append(f"DefaultSkillHttpClient missing {token}")
if "retry" in client.lower():
    errors.append("DefaultSkillHttpClient appears to contain retry logic")

circuit = (ROOT / "src/main/java/com/privateflow/modules/skill/circuit/SkillCircuitBreaker.java").read_text(encoding="utf-8")
for token in ["CLOSED", "OPEN", "HALF_OPEN", "AtomicBoolean", "compareAndSet", "failureRate", "circuitBreakerMinCalls"]:
    if token not in circuit:
        errors.append(f"SkillCircuitBreaker missing {token}")

logger = (ROOT / "src/main/java/com/privateflow/modules/skill/infra/SkillCallLogger.java").read_text(encoding="utf-8")
for token in ["@Async(\"skillLogExecutor\")", "skill_call_logs", "request_summary", "UNKNOWN"]:
    if token not in logger:
        errors.append(f"SkillCallLogger missing {token}")

progress = (ROOT / "dev-progress/01B_progress.md").read_text(encoding="utf-8")
for label in ["SF-B01", "SF-B02", "SF-B03", "SF-B04", "SF-B05", "SF-B06", "SF-B07", "SF-B08", "SF-B09", "SF-B10"]:
    if label not in progress:
        errors.append(f"progress card missing {label}")

if errors:
    print("Module B verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module B static verification passed.")
print(f"Checked {len(required_files)} required files, {len(scene_values)} scene values, {len(skill_keys)} skill config keys.")
