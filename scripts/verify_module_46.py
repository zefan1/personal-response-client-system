from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/tags/TagAdminController.java",
    "src/main/java/com/privateflow/modules/tags/TagAdminService.java",
    "src/main/java/com/privateflow/modules/tags/TagRepository.java",
    "src/main/java/com/privateflow/modules/tags/TagCacheService.java",
    "src/main/java/com/privateflow/modules/tags/TagCategory.java",
    "src/main/java/com/privateflow/modules/tags/TagValue.java",
    "src/main/java/com/privateflow/modules/tags/TagCategoryRequest.java",
    "src/main/java/com/privateflow/modules/tags/TagValueRequest.java",
    "src/main/java/com/privateflow/modules/tags/TagErrorCodes.java",
    "src/main/resources/db/migration/V25__module_46_tag_management.sql",
    "dev-progress/46_progress.md",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

controller = read("src/main/java/com/privateflow/modules/tags/TagAdminController.java")
for token in [
    "/admin/api/v1/tags/categories",
    "/admin/api/v1/tags/categories/{id}",
    "/admin/api/v1/tags/values",
    "/admin/api/v1/tags/values/{id}",
    "/admin/api/v1/tags/values/{id}/toggle",
    "@ExceptionHandler(ApiException.class)",
]:
    if token not in controller:
        errors.append(f"TagAdminController missing {token}")

service = read("src/main/java/com/privateflow/modules/tags/TagAdminService.java")
for token in [
    "VALUE_MAX_PER_CATEGORY = 50",
    "^[A-Z0-9_]{1,50}$",
    "Introspector.getBeanInfo(Customer.class)",
    "boundField cannot be changed",
    "BUILTIN_CATEGORY_DELETE_FORBIDDEN",
    "CATEGORY_HAS_VALUES",
    "VALUE_IN_USE",
    "repository.usageCount",
    "UPDATE_TAG",
    "ConfigChangedEvent(\"tag_config\")",
    "CONFIG_REFRESH",
]:
    if token not in service:
        errors.append(f"TagAdminService missing {token}")

repo = read("src/main/java/com/privateflow/modules/tags/TagRepository.java")
for token in [
    "tag_categories",
    "tag_values",
    "findEnabledForPrompt",
    "personality_type",
    "body_concerns",
    "worries",
    "LIKE CONCAT('%', ?, '%')",
    "UPDATE tag_values",
    "display_name = COALESCE",
]:
    if token not in repo:
        errors.append(f"TagRepository missing {token}")

cache = read("src/main/java/com/privateflow/modules/tags/TagCacheService.java")
for token in [
    "getAllEnabledTags",
    "getTagsByCategory",
    "refresh()",
    "@EventListener",
    "tag_config",
    "@Scheduled",
]:
    if token not in cache:
        errors.append(f"TagCacheService missing {token}")

builder = read("src/main/java/com/privateflow/modules/skill/service/SkillRequestBuilder.java")
for token in [
    "TagCacheService",
    "getAllEnabledTags",
    "Available ",
    "value.displayName()",
    "value.tagValue()",
]:
    if token not in builder:
        errors.append(f"SkillRequestBuilder missing tag cache integration {token}")

config = read("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java")
for token in ["key.startsWith(\"tag.\")", "tag.cache_refresh_interval_s", "tag.value_max_per_category"]:
    if token not in config:
        errors.append(f"ConfigAdminService missing {token}")

migration = read("src/main/resources/db/migration/V25__module_46_tag_management.sql")
for token in [
    "CREATE TABLE IF NOT EXISTS tag_categories",
    "CREATE TABLE IF NOT EXISTS tag_values",
    "uk_bound_field",
    "uk_category_key",
    "uk_category_value",
    "personality_type",
    "body_concerns",
    "worries",
    "intent_level",
    "LOYALIST",
    "PEACEMAKER",
    "DIASTASIS_RECTI",
    "FEAR_EXPENSIVE",
    "tag.cache_refresh_interval_s",
    "tag.value_max_per_category",
]:
    if token not in migration:
        errors.append(f"migration missing {token}")

codes = read("src/main/java/com/privateflow/modules/tags/TagErrorCodes.java")
for code in ["90-10001", "90-10002", "90-10003", "90-10004", "90-10005", "90-10006", "90-10007", "90-10008"]:
    if code not in codes:
        errors.append(f"TagErrorCodes missing {code}")

progress = read("dev-progress/46_progress.md")
for token in [
    "python scripts\\verify_module_46.py",
    "Skill prompt now reads available tags from `TagCacheService`",
    "Customer data is not modified",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 46 verification passed")
