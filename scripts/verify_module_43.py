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
    text = "\n".join(child.read_text(encoding="utf-8") for child in target.rglob("*.java")) if target.is_dir() else read(path)
    found = [token for token in tokens if token in text]
    if found:
        raise AssertionError(f"{path} forbidden tokens: {', '.join(found)}")


def main() -> None:
    require("src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminController.java", [
        "/admin/api/v1/quick-search/items",
        "/admin/api/v1/quick-search/items/{id}",
        "/admin/api/v1/quick-search/items/{id}/toggle",
        "/admin/api/v1/upload/image",
        "MultipartFile",
    ])
    require("src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminService.java", [
        "TEMPLATE",
        "KNOWLEDGE",
        "LOCATION",
        "IMAGE",
        "MINI_PROGRAM",
        "TUAN_GOU",
        "XIAN_SUO",
        "GENERAL",
        "quick_search",
        "CONFIG_REFRESH",
        "ConfigChangedEvent",
        "shortcutCode already exists",
        "30-10002",
        "MAX_IMAGE_BYTES = 10 * 1024 * 1024",
        "detectImageExt",
        "COS_UPLOAD_FAILED",
    ])
    require("src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminRepository.java", [
        "quick_search_items",
        "cos_cleanup_queue",
        "uk_shortcut_code",
        "idx_content_type",
        "idx_lead_type",
        "idx_is_enabled",
        "LOWER(shortcut_code) = LOWER(?)",
        "INSERT INTO quick_search_items",
        "DELETE FROM quick_search_items",
        "PENDING",
    ])
    require("src/main/java/com/privateflow/modules/quicksearch/admin/QuickSearchAdminItem.java", [
        "contentType",
        "leadType",
        "title",
        "shortcutCode",
        "content",
        "imageUrl",
        "sortOrder",
        "enabled",
        "updatedAt",
    ])
    require("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java", [
        "quicksearch.",
        "quicksearch.admin.page_size",
        "10-50",
        "quicksearch.admin.image_max_size_mb",
        "1-50",
        "quicksearch.admin.cos_retention_days",
        "7-90",
    ])
    require("src/main/resources/db/migration/V23__module_43_quicksearch_admin.sql", [
        "created_by",
        "created_at",
        "CREATE TABLE IF NOT EXISTS cos_cleanup_queue",
        "idx_status",
        "idx_deleted_at",
        "quicksearch.admin.page_size",
        "'20'",
        "quicksearch.admin.image_max_size_mb",
        "'10'",
        "quicksearch.admin.cos_retention_days",
        "'30'",
    ])
    require("dev-progress/43_progress.md", [
        "功能签收清单",
        "python scripts/verify_module_43.py",
        "mvn test",
        "git diff --check",
    ])
    forbid("src/main/java/com/privateflow/modules/quicksearch/admin", [
        "TODO",
        "FIXME",
    ])
    print("module 43 verification passed")


if __name__ == "__main__":
    main()
