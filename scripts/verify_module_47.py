from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/analytics/AnalyticsController.java",
    "src/main/java/com/privateflow/modules/analytics/AnalyticsService.java",
    "src/main/java/com/privateflow/modules/analytics/AnalyticsRepository.java",
    "src/main/java/com/privateflow/modules/analytics/AnalyticsScope.java",
    "dev-progress/47_progress.md",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

controller = read("src/main/java/com/privateflow/modules/analytics/AnalyticsController.java")
for token in [
    "/admin/api/v1/analytics/overview",
    "/admin/api/v1/analytics/funnels",
    "/admin/api/v1/analytics/staff",
    "/admin/api/v1/analytics/sources",
    "/admin/api/v1/analytics/stages",
    "/admin/api/v1/analytics/health",
    "/admin/api/v1/analytics/lifecycle",
    "/admin/api/v1/analytics/risks",
    "/admin/api/v1/analytics/content-ranking",
]:
    if token not in controller:
        errors.append(f"AnalyticsController missing {token}")

service = read("src/main/java/com/privateflow/modules/analytics/AnalyticsService.java")
for token in [
    "Math.max(1, Math.min(days, 90))",
    '"TUAN_GOU"',
    '"XIAN_SUO"',
    '"PENDING"',
    "requestedCaller = user.username()",
    "requireAdmin()",
    "ApiErrorCodes.FORBIDDEN",
]:
    if token not in service:
        errors.append(f"AnalyticsService missing {token}")

repo = read("src/main/java/com/privateflow/modules/analytics/AnalyticsRepository.java")
for token in [
    "skill_call_logs",
    "audit_logs",
    "customers",
    "accounts",
    "system_alerts",
    "COPY_REPLY",
    "BATCH_TEMPLATE",
    "REGENERATE",
    "CUSTOMER_COMPLAINT",
    "CHURN_RISK",
    r"customer_stage LIKE '%\u9884\u7ea6%'",
    "customers.updated_at",
    "success = 1",
    "leader_id = (SELECT l.id FROM accounts",
    "DATE_SUB(NOW(), INTERVAL ? DAY)",
]:
    if token not in repo:
        errors.append(f"AnalyticsRepository missing {token}")

jwt = read("src/main/java/com/privateflow/modules/api/auth/JwtAuthenticationFilter.java")
for token in [
    "return user.role() == Role.ADMIN",
]:
    if token not in jwt:
        errors.append(f"JwtAuthenticationFilter missing {token}")

skill_controller = read("src/main/java/com/privateflow/modules/skill/admin/SkillAdminController.java")
if "/admin/api/v1/analytics/skill-calls" not in skill_controller:
    errors.append("existing skill-calls analytics endpoint missing")

migrations = "\n".join(path.name for path in (ROOT / "src/main/resources/db/migration").glob("*.sql"))
if "module_47" in migrations or "analytics" in migrations:
    errors.append("module 47 must not add analytics migration/table")

progress = read("dev-progress/47_progress.md")
for token in [
    "No migration or new table was added",
    "python scripts\\verify_module_47.py",
    "ADMIN-only operations admin analytics",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 47 verification passed")
