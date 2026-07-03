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
        text = "\n".join(child.read_text(encoding="utf-8") for child in target.rglob("*.java"))
    else:
        text = read(path)
    found = [token for token in tokens if token in text]
    if found:
        raise AssertionError(f"{path} forbidden tokens: {', '.join(found)}")


def main() -> None:
    require("src/main/java/com/privateflow/modules/customer/admin/DatasourceAdminController.java", [
        "/admin/api/v1/datasources",
        "/admin/api/v1/datasources/{id}",
        "/admin/api/v1/datasources/{id}/toggle",
        "/admin/api/v1/datasources/{id}/replace",
        "/admin/api/v1/datasources/{id}/mappings",
        "/admin/api/v1/datasources/{id}/mappings/versions",
        "/admin/api/v1/datasources/{id}/mappings/restore",
        "/admin/api/v1/datasources/{id}/mappings/compare",
        "/admin/api/v1/datasources/{id}/columns",
        "/admin/api/v1/customer-fields",
        "/admin/api/v1/datasources/sync-status",
        "/admin/api/v1/datasources/{id}/sync",
        "/admin/api/v1/datasources/import",
        "/admin/api/v1/datasources/import-logs",
        "MultipartFile",
        "service.compareMappings(id)",
    ])
    require("src/main/java/com/privateflow/modules/customer/admin/DatasourceAdminService.java", [
        "datasource.field_mappings",
        "datasource.connections",
        "ConfigChangedEvent",
        "CONFIG_REFRESH",
        "CustomerSyncScheduler",
        "CustomerRepository",
        "SheetClient",
        "Introspector.getBeanInfo(Customer.class)",
        "compareMappings",
        "fetchStatus",
        "externalFetchAvailable",
        "repository.importLogs",
        "IMPORT_MAX_ROWS = 5000",
        "same targetField can only have one enabled mapping",
        "customerRepository.findByPhone(phone).orElseGet(Customer::new)",
        "isBlank(customer.getNickname())",
        "CSV_IMPORT",
    ])
    require("src/main/java/com/privateflow/modules/customer/admin/DatasourceAdminRepository.java", [
        "datasources",
        "datasource_field_mappings",
        "datasource_mapping_versions",
        "customer_import_log",
        "sync_failure_log",
        "idx_enabled",
        "idx_source_target",
        "idx_datasource_ver",
        "DELETE FROM datasource_field_mappings",
        "MAX(version)",
        "LIMIT 20",
        "latestMappingSnapshot",
        "importLogs",
        "nameExists",
        "SELECT COUNT(*) FROM customer_import_log",
    ])
    require("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java", [
        "datasource.",
        "datasource.mapping_version_max",
        "20-200",
        "datasource.import_max_rows",
        "1000-10000",
        "datasource.manual_sync_timeout_s",
        "30-120",
        "datasource.sync_status_refresh_s",
        "15-120",
    ])
    require("src/main/resources/db/migration/V22__module_42_datasource_admin.sql", [
        "CREATE TABLE IF NOT EXISTS datasources",
        "CREATE TABLE IF NOT EXISTS datasource_mapping_versions",
        "CREATE TABLE IF NOT EXISTS customer_import_log",
        "idx_name",
        "idx_enabled",
        "idx_datasource_ver",
        "idx_importer_time",
        "datasource.mapping_version_max",
        "'50'",
        "datasource.import_max_rows",
        "'5000'",
        "datasource.manual_sync_timeout_s",
        "'60'",
        "datasource.sync_status_refresh_s",
        "'30'",
    ])
    require("dev-progress/42_progress.md", [
        "功能签收清单",
        "compare",
        "import-logs",
        "python scripts/verify_module_42.py",
        "mvn test",
        "git diff --check",
    ])
    forbid("src/main/java/com/privateflow/modules/customer/admin", [
        "TODO",
        "FIXME",
        "manual comparison pending",
        "暂无法自动获取列名",
    ])
    print("module 42 verification passed")


if __name__ == "__main__":
    main()
