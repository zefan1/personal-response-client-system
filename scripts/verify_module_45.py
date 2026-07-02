from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "dev-progress/45_progress.md",
    "src/main/java/com/privateflow/modules/followup/web/FollowupController.java",
    "src/main/java/com/privateflow/modules/followup/service/RuleAdminService.java",
    "src/main/java/com/privateflow/modules/followup/infra/FollowupRuleRepository.java",
    "src/main/java/com/privateflow/modules/followup/ActionType.java",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

controller = read("src/main/java/com/privateflow/modules/followup/web/FollowupController.java")
for token in [
    "/admin/api/v1/rules",
    "keyword",
    "actionType",
    "enabled",
    "@PostMapping",
    "@DeleteMapping",
    "/admin/api/v1/rules/{id}/toggle",
]:
    if token not in controller:
        errors.append(f"FollowupController missing {token}")

service = read("src/main/java/com/privateflow/modules/followup/service/RuleAdminService.java")
for token in [
    "MAX_AND_CONDITIONS = 8",
    "MAX_OR_GROUPS = 2",
    "validateConditionComplexity",
    "ruleRepository.nameExists",
    "builtin rule name cannot be changed",
    "builtin rule actionType cannot be changed",
    "builtin rule cannot be deleted",
    "ActionType.ALERT",
    "ActionType.TAG_CHANGE",
    "ActionType.NOTIFY_LEADER",
    "ruleLoader.refresh()",
    "AuditLogger",
    "CREATE_FOLLOWUP_RULE",
    "UPDATE_FOLLOWUP_RULE",
    "DELETE_FOLLOWUP_RULE",
    "TOGGLE_FOLLOWUP_RULE",
]:
    if token not in service:
        errors.append(f"RuleAdminService missing {token}")

repo = read("src/main/java/com/privateflow/modules/followup/infra/FollowupRuleRepository.java")
for token in [
    "nameExists",
    "SELECT COUNT(*) FROM followup_rules WHERE name = ?",
    "ORDER BY priority DESC, id DESC",
    "AND action_type = ?",
    "DELETE FROM followup_rules WHERE id = ? AND is_builtin = 0",
]:
    if token not in repo:
        errors.append(f"FollowupRuleRepository missing {token}")

action = read("src/main/java/com/privateflow/modules/followup/ActionType.java")
for token in ["ALERT", "TAG_CHANGE", "NOTIFY_LEADER"]:
    if token not in action:
        errors.append(f"ActionType missing {token}")
if "STATUS_CHANGE" in action:
    errors.append("STATUS_CHANGE must not be exposed in ActionType")

migrations = sorted((ROOT / "src/main/resources/db/migration").glob("V*__module_45*.sql"))
if migrations:
    errors.append("module 45 must not add a database migration")

progress = read("dev-progress/45_progress.md")
for token in [
    "No new database table",
    "8 direct AND conditions and 2 OR groups",
    "STATUS_CHANGE is not exposed",
    "python scripts\\verify_module_45.py",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 45 verification passed")
