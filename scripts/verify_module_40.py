from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")


def require(path: str, tokens: list[str]) -> None:
    text = read(path)
    missing = [token for token in tokens if token not in text]
    if missing:
        raise AssertionError(f"{path} missing: {', '.join(missing)}")


def forbid(path: str, tokens: list[str]) -> None:
    text = read(path)
    found = [token for token in tokens if token in text]
    if found:
        raise AssertionError(f"{path} forbidden tokens: {', '.join(found)}")


def main() -> None:
    require("src/main/java/com/privateflow/modules/skill/admin/SkillAdminController.java", [
        "/admin/api/v1/skills",
        "/admin/api/v1/skills/{id}",
        "/admin/api/v1/skills/{id}/toggle",
        "/admin/api/v1/skills/available",
        "/admin/api/v1/skills/{id}/test",
        "/admin/api/v1/analytics/skill-calls",
        "SkillAdminException",
    ])
    require("src/main/java/com/privateflow/modules/skill/admin/SkillAdminService.java", [
        "CHAT_RECOGNIZE",
        "ACTIVE_REPLY",
        "REGENERATE",
        "PROFILE_EXTRACT",
        "OPENING",
        "TUAN_GOU",
        "XIAN_SUO",
        "PENDING",
        "80-10001",
        "CONFIG_REFRESH",
        "ConfigChangedEvent",
        "skill_scene_bindings",
        "same scene+leadType",
        "TEST_TIMEOUT_MS = 12000",
        "skillHttpClient.call",
        "responseParser.parseReplies",
    ])
    forbid("src/main/java/com/privateflow/modules/skill/admin/SkillAdminService.java", [
        "SkillGatewayService",
        "logCall(",
        "TODO",
        "FIXME",
    ])
    require("src/main/java/com/privateflow/modules/skill/admin/SkillSceneBindingRepository.java", [
        "skill_scene_bindings",
        "idx_scene_lead",
        "last_tested_at",
        "ORDER BY scene ASC, lead_type ASC, priority ASC",
        "skill_id = ? AND scene = ? AND lead_type = ?",
        "enabled = 1 AND id <> ?",
    ])
    require("src/main/java/com/privateflow/modules/skill/admin/SkillCallAnalyticsRepository.java", [
        "skill_call_logs",
        "DATE_SUB(NOW(), INTERVAL ? DAY)",
        "successRate",
        "adoptionRate",
        "GROUP BY scene, lead_type",
        "Math.max(1, Math.min(days, 90))",
    ])
    require("src/main/resources/db/migration/V20__module_40_skill_admin.sql", [
        "last_tested_at",
        "skill.admin.monitor_refresh_interval_s",
        "'30'",
        "skill.admin.monitor_default_days",
        "'7'",
        "skill.admin.test_timeout_ms",
        "'10000'",
        "skill.admin.test_message_max_chars",
        "'2000'",
    ])
    require("dev-progress/40_progress.md", [
        "功能签收清单",
        "python scripts/verify_module_40.py",
        "mvn test",
        "git diff --check",
    ])
    print("module 40 verification passed")


if __name__ == "__main__":
    main()
