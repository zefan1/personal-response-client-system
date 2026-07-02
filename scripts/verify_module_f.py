from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]

required_files = [
    "dev-progress/01F_progress.md",
    "src/main/resources/db/migration/V6__module_f_followup_rules.sql",
    "src/main/java/com/privateflow/common/events/FollowupWsMessageReadyEvent.java",
    "src/main/java/com/privateflow/modules/followup/ReminderType.java",
    "src/main/java/com/privateflow/modules/followup/ActionType.java",
    "src/main/java/com/privateflow/modules/followup/FollowupRule.java",
    "src/main/java/com/privateflow/modules/followup/config/FollowupConfig.java",
    "src/main/java/com/privateflow/modules/followup/config/FollowupConfigProvider.java",
    "src/main/java/com/privateflow/modules/followup/infra/FollowupRuleRepository.java",
    "src/main/java/com/privateflow/modules/followup/infra/ReminderLogRepository.java",
    "src/main/java/com/privateflow/modules/followup/infra/TagSuggestionRepository.java",
    "src/main/java/com/privateflow/modules/followup/service/RuleLoader.java",
    "src/main/java/com/privateflow/modules/followup/service/ConditionEvaluator.java",
    "src/main/java/com/privateflow/modules/followup/service/RuleMatcher.java",
    "src/main/java/com/privateflow/modules/followup/service/ActionExecutor.java",
    "src/main/java/com/privateflow/modules/followup/service/NewLeadEventListener.java",
    "src/main/java/com/privateflow/modules/followup/service/FullScanScheduler.java",
    "src/main/java/com/privateflow/modules/followup/service/LightweightScanScheduler.java",
    "src/main/java/com/privateflow/modules/followup/service/FollowupTodayService.java",
    "src/main/java/com/privateflow/modules/followup/service/RuleAdminService.java",
    "src/main/java/com/privateflow/modules/followup/web/FollowupController.java",
]

followup_keys = [
    "followup.full_scan_cron",
    "followup.lightweight_scan_cron",
    "followup.rule_refresh_interval_s",
    "followup.tuan_alert_hours",
    "followup.xiansuo_alert_hours",
    "followup.pending_alert_hours",
    "followup.sleep_risk_days",
    "followup.loss_risk_days",
    "followup.appointment_remind_hours",
    "followup.scan_batch_size",
    "followup.scan_timeout_s",
    "followup.reminder_dedup_days",
    "followup.tag_suggestion_dedup_days",
    "followup.cursor_ttl_s",
    "followup.keeper_overdue_leader_hours",
]

errors = []

for rel in required_files:
    if not (ROOT / rel).exists():
        errors.append(f"missing required file: {rel}")

def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")

sql = read("src/main/resources/db/migration/V6__module_f_followup_rules.sql")
for table in ["followup_rules", "reminder_sent_log", "system_tag_suggestions"]:
    if f"CREATE TABLE IF NOT EXISTS {table}" not in sql:
        errors.append(f"V6 migration missing table {table}")
for index in ["idx_enabled_priority", "idx_dedup", "idx_sent_date", "idx_phone_status", "idx_cleanup"]:
    if index not in sql:
        errors.append(f"V6 migration missing index {index}")
for name in ["团购超期告警", "线索超期提醒", "PENDING超期提醒", "沉睡风险", "可能流失", "高流失风险", "预约提醒", "管家超期未处理告警"]:
    if name not in sql:
        errors.append(f"V6 migration missing builtin rule {name}")
for key in followup_keys:
    if key not in sql:
        errors.append(f"V6 migration missing config key {key}")

reminder = read("src/main/java/com/privateflow/modules/followup/ReminderType.java")
for value in ["OVERDUE", "DUE_TODAY", "APPOINTMENT", "NEW_LEAD", "TAG_SUGGESTION"]:
    if value not in reminder:
        errors.append(f"ReminderType missing {value}")

action = read("src/main/java/com/privateflow/modules/followup/ActionType.java")
for value in ["ALERT", "TAG_CHANGE", "NOTIFY_LEADER"]:
    if value not in action:
        errors.append(f"ActionType missing {value}")
if "STATUS_CHANGE" in action:
    errors.append("STATUS_CHANGE must not be enabled in ActionType")

config = read("src/main/java/com/privateflow/modules/followup/config/FollowupConfigProvider.java")
for key in ["followup.full_scan_cron", "followup.lightweight_scan_cron", "followup.rule_refresh_interval_s", "ConfigChangedEvent"]:
    if key not in config:
        errors.append(f"FollowupConfigProvider missing {key}")

loader = read("src/main/java/com/privateflow/modules/followup/service/RuleLoader.java")
for token in ["@Scheduled", "findEnabled", "ConfigChangedEvent", "takeSnapshot"]:
    if token not in loader:
        errors.append(f"RuleLoader missing {token}")

condition = read("src/main/java/com/privateflow/modules/followup/service/ConditionEvaluator.java")
for token in ["FIELD_WHITELIST", "OP_WHITELIST", "AND", "OR", "leadType", "lastFollowupHours", "appointmentDate"]:
    if token not in condition:
        errors.append(f"ConditionEvaluator missing {token}")

executor = read("src/main/java/com/privateflow/modules/followup/service/ActionExecutor.java")
for token in ["FollowupWsMessageReadyEvent", "FOLLOWUP_REMIND", "NEW_LEAD_ALERT", "reminderLogRepository.markSent", "tagSuggestionRepository.upsertPending", "PhoneUtils.mask"]:
    if token not in executor:
        errors.append(f"ActionExecutor missing {token}")

new_lead = read("src/main/java/com/privateflow/modules/followup/service/NewLeadEventListener.java")
for token in ["NewLeadEvent", "@EventListener", "customerQueryService.getByPhone", "executeNewLead"]:
    if token not in new_lead:
        errors.append(f"NewLeadEventListener missing {token}")

full_scan = read("src/main/java/com/privateflow/modules/followup/service/FullScanScheduler.java")
for token in ["@Scheduled", "scanActiveCustomers", "ruleLoader.takeSnapshot", "actionExecutor.execute"]:
    if token not in full_scan:
        errors.append(f"FullScanScheduler missing {token}")

light_scan = read("src/main/java/com/privateflow/modules/followup/service/LightweightScanScheduler.java")
for token in ["appointmentDate", "lastFollowupHours", "@Scheduled", "scanActiveCustomers"]:
    if token not in light_scan:
        errors.append(f"LightweightScanScheduler missing {token}")

today = read("src/main/java/com/privateflow/modules/followup/service/FollowupTodayService.java")
for token in ["OVERDUE", "DUE_TODAY", "APPOINTMENT", "NEW_LEAD", "findTodayPhones", "TUAN_GOU"]:
    if token not in today:
        errors.append(f"FollowupTodayService missing {token}")

admin = read("src/main/java/com/privateflow/modules/followup/service/RuleAdminService.java")
for token in ["create", "update", "delete", "toggle", "builtin", "FORBIDDEN", "CONDITION_PARSE_FAILED"]:
    if token not in admin:
        errors.append(f"RuleAdminService missing {token}")

controller = read("src/main/java/com/privateflow/modules/followup/web/FollowupController.java")
for token in ["/api/v1/followups/today", "/admin/api/v1/rules", "@PostMapping", "@DeleteMapping", "toggle", "FollowupException"]:
    if token not in controller:
        errors.append(f"FollowupController missing {token}")

progress = read("dev-progress/01F_progress.md")
for label in [f"SF-F{i:02d}" for i in range(1, 16)]:
    if label not in progress:
        errors.append(f"progress card missing {label}")

if errors:
    print("Module F verification failed:")
    for error in errors:
        print(f"- {error}")
    sys.exit(1)

print("Module F static verification passed.")
print(f"Checked {len(required_files)} required files and {len(followup_keys)} followup config keys.")
