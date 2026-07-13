from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/api/audit/AuditLogController.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditLogService.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditLogRepository.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditLogQuery.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditLogEntry.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditExportRequest.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditExportRecord.java",
    "src/main/java/com/privateflow/modules/api/audit/AuditExportStatus.java",
    "src/main/resources/db/migration/V28__module_50_audit_logs.sql",
    "dev-progress/50_progress.md",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

controller = read("src/main/java/com/privateflow/modules/api/audit/AuditLogController.java")
for token in [
    "/admin/api/v1/audit-logs",
    "/admin/api/v1/audit-logs/actions",
    "/admin/api/v1/audit-logs/export",
    "/admin/api/v1/audit-logs/export/{exportId}",
    "/admin/api/v1/audit-logs/export/{exportId}/download",
    "action.split(\",\")",
    "DateTimeFormat.ISO.DATE",
    "HttpHeaders.CONTENT_DISPOSITION",
]:
    if token not in controller:
        errors.append(f"AuditLogController missing {token}")

service = read("src/main/java/com/privateflow/modules/api/audit/AuditLogService.java")
for token in [
    "Role.ADMIN",
    "CALL_SKILL",
    "COPY_REPLY",
    "SEND_MESSAGE",
    "BATCH_TEMPLATE",
    "UPDATE_PROFILE",
    "UPDATE_STAGE",
    "UPDATE_TAG",
    "SAVE_TO_TABLE",
    "ASK_FOR_HELP",
    "RESOLVE_HELP",
    "UPDATE_CONFIG",
    "CREATE_NOTICE",
    "STOP_NOTICE",
    "PUBLISH_NOTICE",
    "VERSION_PUBLISH",
    "VERSION_REVOKE",
    "DATASOURCE_CREATE",
    "DATASOURCE_UPDATE",
    "DATASOURCE_DELETE",
    "DATASOURCE_TOGGLE",
    "DATASOURCE_REPLACE_SHEET",
    "DATASOURCE_MAPPING_SAVE",
    "DATASOURCE_MAPPING_RESTORE",
    "DATASOURCE_SYNC_START",
    "DATASOURCE_CSV_IMPORT",
    "QUICK_SEARCH_CREATE",
    "QUICK_SEARCH_UPDATE",
    "QUICK_SEARCH_DELETE",
    "QUICK_SEARCH_TOGGLE",
    "QUICK_SEARCH_IMAGE_UPLOAD",
    "SKILL_BINDING_CREATE",
    "SKILL_BINDING_UPDATE",
    "SKILL_BINDING_DELETE",
    "SKILL_BINDING_TOGGLE",
    "ACCOUNT_CREATE",
    "ACCOUNT_UPDATE",
    "ACCOUNT_DELETE",
    "ACCOUNT_TOGGLE",
    "ACCOUNT_RESET_PASSWORD",
    '"actionLabel"',
    '"actionGroup"',
    '"targetTypeLabel"',
    '"detailParsed"',
    '"detailSummary"',
    "objectMapper.readValue",
    "return null;",
    "audit.export_max_rows",
    "audit.export_cos_retention_hours",
    "audit.export_timeout_seconds",
    "audit.list_page_size_default",
    "audit.list_max_page_size",
    "system.audit_log_retention_days",
    "system.audit_log_cleanup_batch_size",
    "@Scheduled(cron = \"0 0 4 * * *\")",
    "\\uFEFF操作时间,操作人,操作类型,操作对象,操作摘要,详情",
    "PROCESSING",
    "COMPLETED",
    "FAILED",
]:
    if token not in service:
        errors.append(f"AuditLogService missing {token}")

repository = read("src/main/java/com/privateflow/modules/api/audit/AuditLogRepository.java")
for token in [
    "FROM audit_logs",
    "action IN (",
    "operator LIKE ?",
    "target_type = ?",
    "target_id = ?",
    "detail LIKE ?",
    "ORDER BY created_at DESC",
    "INSERT INTO audit_log_exports",
    "UPDATE audit_log_exports",
    "DELETE FROM audit_logs",
    "LIMIT ?",
]:
    if token not in repository:
        errors.append(f"AuditLogRepository missing {token}")

migration = read("src/main/resources/db/migration/V28__module_50_audit_logs.sql")
for token in [
    "ALTER TABLE audit_logs",
    "MODIFY COLUMN detail TEXT",
    "CREATE INDEX idx_audit_action",
    "CREATE INDEX idx_audit_operator",
    "CREATE INDEX idx_audit_created_at",
    "CREATE INDEX idx_audit_action_created",
    "CREATE TABLE IF NOT EXISTS audit_log_exports",
    "export_id",
    "PROCESSING / COMPLETED / FAILED",
    "csv_content",
    "audit.export_max_rows",
    "audit.export_cos_retention_hours",
    "audit.export_timeout_seconds",
    "audit.list_page_size_default",
    "audit.list_max_page_size",
    "system.audit_log_cleanup_batch_size",
]:
    if token not in migration:
        errors.append(f"migration missing {token}")

config = read("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java")
for token in [
    'key.startsWith("audit.")',
    '"system.audit_log_retention_days"',
    '"system.audit_log_cleanup_batch_size"',
    '"audit.export_max_rows"',
    '"audit.export_cos_retention_hours"',
    '"audit.export_timeout_seconds"',
    '"audit.list_page_size_default"',
    '"audit.list_max_page_size"',
    'key.endsWith("_rows")',
    'key.endsWith("_seconds")',
]:
    if token not in config:
        errors.append(f"ConfigAdminService missing {token}")

progress = read("dev-progress/50_progress.md")
for token in [
    "python scripts\\verify_module_50.py",
    "/admin/api/v1/audit-logs",
    "audit_log_exports",
    "UTF-8 BOM",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 50 verification passed")
