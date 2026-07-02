from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/api/health/HealthService.java",
    "src/main/java/com/privateflow/modules/api/web/HealthController.java",
    "src/main/java/com/privateflow/modules/api/alert/SystemAlertRepository.java",
    "src/main/resources/db/migration/V29__module_51_health_monitor.sql",
    "dev-progress/51_progress.md",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

health = read("src/main/java/com/privateflow/modules/api/health/HealthService.java")
for token in [
    "Role.ADMIN",
    "Role.LEADER",
    "FORBIDDEN",
    '"status"',
    '"timestamp"',
    '"components"',
    '"recentAlerts"',
    '"refreshIntervalS"',
    "components.put(\"skill\"",
    "components.put(\"imageRecognition\"",
    "components.put(\"wecomTable\"",
    "components.put(\"redis\"",
    "components.put(\"db\"",
    "UP",
    "DOWN",
    "DEGRADED",
    "UNKNOWN",
    '"lastCheckedAt"',
    '"duration"',
    "Duration.between",
    '"circuitState"',
    '"successRate5min"',
    '"consecutiveFailures"',
    '"lastError"',
    '"pendingCount"',
    '"staleFailedCount"',
    "health.alert_history_days",
    "health.alert_history_max",
    "health.refresh_interval_s",
]:
    if token not in health:
        errors.append(f"HealthService missing {token}")

alerts = read("src/main/java/com/privateflow/modules/api/alert/SystemAlertRepository.java")
for token in [
    "recentAlerts",
    "FROM system_alerts",
    "occurred_at >= DATE_SUB(NOW(), INTERVAL ? DAY)",
    "ORDER BY status ASC, occurred_at DESC",
    "LIMIT ?",
    '"alertId"',
    '"alertType"',
    '"alertLevel"',
    '"status"',
    '"detail"',
    '"occurredAt"',
    '"resolvedAt"',
]:
    if token not in alerts:
        errors.append(f"SystemAlertRepository missing {token}")

migration = read("src/main/resources/db/migration/V29__module_51_health_monitor.sql")
for token in [
    "health.refresh_interval_s",
    "'30'",
    "health.alert_history_days",
    "'7'",
    "health.alert_history_max",
    "'100'",
]:
    if token not in migration:
        errors.append(f"migration missing {token}")

config = read("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java")
for token in [
    'key.startsWith("health.")',
    '"health.refresh_interval_s"',
    '"health.alert_history_days"',
    '"health.alert_history_max"',
    "0 or 15-120",
]:
    if token not in config:
        errors.append(f"ConfigAdminService missing {token}")

skill = read("src/main/java/com/privateflow/modules/skill/health/SkillHealthMonitor.java")
if "circuitState()" not in skill or "circuitBreaker.state().name()" not in skill:
    errors.append("SkillHealthMonitor missing circuitState exposure")

progress = read("dev-progress/51_progress.md")
for token in [
    "python scripts\\verify_module_51.py",
    "/admin/api/v1/health",
    "recentAlerts",
    "skill",
    "imageRecognition",
    "wecomTable",
    "redis",
    "db",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 51 verification passed")
