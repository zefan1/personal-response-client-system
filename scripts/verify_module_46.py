from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/tags/TagAdminController.java",
    "src/main/java/com/privateflow/modules/tags/TagAdminService.java",
    "src/main/java/com/privateflow/modules/tags/TagRepository.java",
    "src/main/java/com/privateflow/modules/tags/TagDirectoryService.java",
    "src/main/java/com/privateflow/modules/tags/TagCandidateBuilder.java",
    "src/main/java/com/privateflow/modules/tags/TagCategory.java",
    "src/main/java/com/privateflow/modules/tags/TagValue.java",
    "src/main/java/com/privateflow/modules/tags/TagCategoryRequest.java",
    "src/main/java/com/privateflow/modules/tags/TagValueRequest.java",
    "src/main/java/com/privateflow/modules/tags/TagErrorCodes.java",
    "src/main/java/com/privateflow/modules/tags/TagMergeRepository.java",
    "src/main/java/com/privateflow/modules/tags/TagRuleReferenceService.java",
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
    "/admin/api/v1/tags/categories/{id}/merge-preview",
    "/admin/api/v1/tags/categories/{id}/merge",
    "/admin/api/v1/tags/values/{id}/merge-preview",
    "/admin/api/v1/tags/values/{id}/merge",
    "@ExceptionHandler(ApiException.class)",
]:
    if token not in controller:
        errors.append(f"TagAdminController missing {token}")

service = read("src/main/java/com/privateflow/modules/tags/TagAdminService.java")
for token in [
    "generateCategoryKey",
    "generateTagValue",
    "validateCategorySettings",
    "validateValueSettings",
    "BUILTIN_CATEGORY_DELETE_FORBIDDEN",
    "CATEGORY_HAS_VALUES",
    "VALUE_IN_USE",
    "MERGED_ITEM_READ_ONLY",
    "VERSION_REQUIRED",
    "mergeRepository.mergeValueReferences",
    "ruleReferenceService.rewriteCategory",
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
    "customer_tag_assignments",
    "UPDATE tag_values",
    "display_name = COALESCE",
    "WHERE id = ? AND version = ?",
    "trimIfPresent",
]:
    if token not in repo:
        errors.append(f"TagRepository missing {token}")

for token in ["LIKE CONCAT('%', ?, '%')", "usageCount"]:
    if token in repo:
        errors.append(f"TagRepository still contains legacy usage pattern {token}")

directory = read("src/main/java/com/privateflow/modules/tags/TagDirectoryService.java")
for token in ["class TagDirectoryService", "repository.listTree()", "getSnapshot()"]:
    if token not in directory:
        errors.append(f"TagDirectoryService missing {token}")

candidate = read("src/main/java/com/privateflow/modules/tags/TagCandidateBuilder.java")
for token in ["class TagCandidateBuilder", "TagDirectoryService", "directoryService.getSnapshot()"]:
    if token not in candidate:
        errors.append(f"TagCandidateBuilder missing {token}")

merge_repo = read("src/main/java/com/privateflow/modules/tags/TagMergeRepository.java")
for token in [
    "transferBoundField",
    "mergeValueReferences",
    "mergeCategoryOnlyReferences",
    "saveLegacyMappings",
    "recordOperation",
    "target_lock.locked_by = CASE",
]:
    if token not in merge_repo:
        errors.append(f"TagMergeRepository missing {token}")

builder = read("src/main/java/com/privateflow/modules/skill/service/SkillRequestBuilder.java")
for token in [
    "TagCandidateBuilder",
    "tagCandidateBuilder.build",
    "value.displayName()",
    "value.tagValue()",
]:
    if token not in builder:
        errors.append(f"SkillRequestBuilder missing tag candidate integration {token}")

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
    "Customer data is not modified",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 46 verification passed")
