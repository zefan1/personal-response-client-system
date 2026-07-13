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
    target = ROOT / path
    if target.is_dir():
        text = "\n".join(
            child.read_text(encoding="utf-8")
            for child in target.rglob("*.java")
        )
    else:
        text = read(path)
    found = [token for token in tokens if token in text]
    if found:
        raise AssertionError(f"{path} forbidden tokens: {', '.join(found)}")


def main() -> None:
    require("src/main/java/com/privateflow/modules/api/ai/AiConfigController.java", [
        "/admin/api/v1/skill-environments",
        "/admin/api/v1/skill-environments/{id}",
        "/admin/api/v1/skill-environments/{id}/activate",
        "/admin/api/v1/image-environments",
        "/admin/api/v1/image-environments/{id}",
        "/admin/api/v1/image-environments/{id}/activate",
        "/admin/api/v1/image-environments/{id}/test",
        "/admin/api/v1/skill-prompt/{type}/versions",
        "/admin/api/v1/skill-prompt/{type}/restore",
    ])
    require("src/main/java/com/privateflow/modules/api/web/ConfigController.java", [
        "@GetMapping",
        "/{key:.+}",
        "configAdminService.get",
        "configAdminService.update",
    ])
    require("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java", [
        "CONFIG_REFRESH",
        "ConfigChangedEvent",
        "UPDATE_CONFIG",
        "CONFIG_INVALID",
        "skill.system_prompt_red_lines",
        "match.tag_removal_rules",
        "skill.regenerate_max_count",
        "skill.prompt_version_max",
        "skill.timeout_ms",
        "image.compress_quality",
        "snapshotIfPrompt",
    ])
    require("src/main/java/com/privateflow/modules/api/ai/AiEnvironmentService.java", [
        "skill.api_base_url",
        "skill.api_key",
        "image.api_base_url",
        "image.api_key",
        "CONFIG_REFRESH",
        "ConfigChangedEvent",
        "active environment cannot be deleted",
        "at least one environment must remain",
        "testClient.recognize",
        "TEST_IMAGE",
        "last_test_ok",
    ])
    require("src/main/java/com/privateflow/modules/api/ai/AiEnvironmentRepository.java", [
        "skill_environments",
        "image_environments",
        "api_key_last4",
        "idx_provider_active",
        "last_test_at",
        "last_test_ok",
        "encryptedApiKey",
        "SecretCipher",
        "secretCipher.encrypt",
        "secretCipher.decrypt",
    ])
    require("src/main/java/com/privateflow/modules/api/ai/PromptVersionService.java", [
        "skill.system_prompt_format",
        "skill.system_prompt_red_lines",
        "image.recognition_prompt",
        "restore from version",
        "CONFIG_REFRESH",
        "ConfigChangedEvent",
    ])
    require("src/main/java/com/privateflow/modules/api/ai/PromptVersionRepository.java", [
        "skill_prompt_versions",
        "idx_config_key_version",
        "LIMIT 50",
        "MAX(version)",
    ])
    require("src/main/resources/db/migration/V21__module_41_ai_config_center.sql", [
        "CREATE TABLE IF NOT EXISTS skill_environments",
        "CREATE TABLE IF NOT EXISTS image_environments",
        "CREATE TABLE IF NOT EXISTS skill_prompt_versions",
        "skill.system_prompt_format",
        "skill.system_prompt_red_lines",
        "'[]'",
        "skill.regenerate_max_count",
        "'3'",
        "skill.prompt_version_max",
        "'50'",
        "image.api_base_url",
        "image.api_key",
        "image.timeout_ms",
        "'5000'",
        "image.max_size_bytes",
        "'5242880'",
        "image.max_dimension_px",
        "'1920'",
        "image.compress_quality",
        "'85'",
        "image.recognition_prompt",
        "match.tag_removal_rules",
    ])
    require("dev-progress/41_progress.md", [
        "功能签收清单",
        "python scripts/verify_module_41.py",
        "mvn test",
        "git diff --check",
    ])
    forbid("src/main/java/com/privateflow/modules/api/ai/AiEnvironment.java", [
        "TODO",
        "FIXME",
        "apiKey()",
    ])
    print("module 41 verification passed")


if __name__ == "__main__":
    main()
