from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/notices/NoticeController.java",
    "src/main/java/com/privateflow/modules/notices/NoticeService.java",
    "src/main/java/com/privateflow/modules/notices/NoticeRepository.java",
    "src/main/java/com/privateflow/modules/notices/SystemNotice.java",
    "src/main/java/com/privateflow/modules/notices/NoticeCreateRequest.java",
    "src/main/java/com/privateflow/modules/notices/NoticeUpdateRequest.java",
    "src/main/java/com/privateflow/modules/notices/NoticeLevel.java",
    "src/main/java/com/privateflow/modules/notices/NoticeSource.java",
    "src/main/java/com/privateflow/modules/notices/NoticeStatus.java",
    "src/main/java/com/privateflow/modules/notices/PublishType.java",
    "src/main/resources/db/migration/V27__module_49_system_notices.sql",
    "dev-progress/49_progress.md",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

controller = read("src/main/java/com/privateflow/modules/notices/NoticeController.java")
for token in [
    "/admin/api/v1/notices",
    "/admin/api/v1/notices/{id}",
    "/admin/api/v1/notices/{id}/stop",
    "/api/v1/notices/active",
    "@DeleteMapping",
    "NoticeStatus.valueOf(status)",
]:
    if token not in controller:
        errors.append(f"NoticeController missing {token}")

service = read("src/main/java/com/privateflow/modules/notices/NoticeService.java")
for token in [
    "Role.ADMIN",
    "Role.LEADER",
    "SYSTEM_NOTICE",
    "CREATE_NOTICE",
    "STOP_NOTICE",
    "PUBLISH_NOTICE",
    "NoticeStatus.SCHEDULED",
    "NoticeStatus.PUBLISHED",
    "PublishType.IMMEDIATE",
    "PublishType.SCHEDULED",
    "createAutoNotice(String title, String content, String level, Duration ttl)",
    "stopAutoNotice(String contentKeyword)",
    "repository.activeAutoContentExists",
    "notice.scan_interval_s",
    "notice.max_title_chars",
    "notice.max_content_chars",
    "notice.default_expire_days",
    "notice.max_schedule_days",
    "notice.auto_expire_hours",
    "notice.list_page_size",
    "@Scheduled(fixedDelayString",
    "@Transactional",
    '"noticeId"',
    '"title"',
    '"content"',
    '"level"',
    '"createdAt"',
    '"expireAt"',
]:
    if token not in service:
        errors.append(f"NoticeService missing {token}")

repository = read("src/main/java/com/privateflow/modules/notices/NoticeRepository.java")
for token in [
    "system_notices",
    "FOR UPDATE SKIP LOCKED",
    "status = 'PUBLISHED'",
    "status = 'SCHEDULED'",
    "is_stopped = 0",
    "expire_at > NOW()",
    "source = 'AUTO'",
    "content = ?",
    "DELETE FROM system_notices WHERE id = ? AND is_stopped = 1",
]:
    if token not in repository:
        errors.append(f"NoticeRepository missing {token}")

migration = read("src/main/resources/db/migration/V27__module_49_system_notices.sql")
for token in [
    "CREATE TABLE IF NOT EXISTS system_notices",
    "notice_id",
    "level",
    "source",
    "status",
    "is_stopped",
    "publish_at",
    "pushed_at",
    "expire_at",
    "uk_notice_id",
    "idx_status_stopped_expire",
    "idx_status_publish",
    "idx_source",
    "idx_created_at",
    "notice.max_title_chars",
    "notice.max_content_chars",
    "notice.default_expire_days",
    "notice.max_schedule_days",
    "notice.scan_interval_s",
    "notice.auto_expire_hours",
    "notice.list_page_size",
]:
    if token not in migration:
        errors.append(f"migration missing {token}")

config = read("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java")
for token in [
    'key.startsWith("notice.")',
    '"notice.max_title_chars"',
    '"notice.max_content_chars"',
    '"notice.default_expire_days"',
    '"notice.max_schedule_days"',
    '"notice.scan_interval_s"',
    '"notice.auto_expire_hours"',
    '"notice.list_page_size"',
]:
    if token not in config:
        errors.append(f"ConfigAdminService missing {token}")

progress = read("dev-progress/49_progress.md")
for token in [
    "python scripts\\verify_module_49.py",
    "system_notices",
    "SYSTEM_NOTICE",
    "createAutoNotice",
    "stopAutoNotice",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 49 verification passed")
