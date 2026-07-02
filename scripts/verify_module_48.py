from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def read(path):
    return (ROOT / path).read_text(encoding="utf-8")


errors = []

required_files = [
    "src/main/java/com/privateflow/modules/versions/DesktopVersionController.java",
    "src/main/java/com/privateflow/modules/versions/DesktopVersionService.java",
    "src/main/java/com/privateflow/modules/versions/DesktopVersionRepository.java",
    "src/main/java/com/privateflow/modules/versions/DesktopVersion.java",
    "src/main/java/com/privateflow/modules/versions/DesktopVersionCreateRequest.java",
    "src/main/java/com/privateflow/modules/versions/DesktopVersionUpdateRequest.java",
    "src/main/java/com/privateflow/modules/versions/VersionRevokeRequest.java",
    "src/main/java/com/privateflow/modules/versions/VersionReportRequest.java",
    "src/main/java/com/privateflow/modules/versions/VersionUploadResponse.java",
    "src/main/java/com/privateflow/modules/versions/DesktopPlatform.java",
    "src/main/java/com/privateflow/modules/versions/VersionStatus.java",
    "src/main/java/com/privateflow/modules/versions/UpdateStrategy.java",
    "src/main/resources/db/migration/V26__module_48_version_management.sql",
    "dev-progress/48_progress.md",
]

for file in required_files:
    if not (ROOT / file).exists():
        errors.append(f"missing {file}")

controller = read("src/main/java/com/privateflow/modules/versions/DesktopVersionController.java")
for token in [
    "/admin/api/v1/versions",
    "/admin/api/v1/versions/{id}",
    "/admin/api/v1/versions/{id}/publish",
    "/admin/api/v1/versions/{id}/revoke",
    "/admin/api/v1/versions/upload",
    "createMultipart",
    "multipart/form-data",
    "/api/v1/desktop/version-check",
    "/api/v1/desktop/version-report",
    "@RequestParam(\"platform\") DesktopPlatform platform",
]:
    if token not in controller:
        errors.append(f"DesktopVersionController missing {token}")

service = read("src/main/java/com/privateflow/modules/versions/DesktopVersionService.java")
for token in [
    "Role.ADMIN",
    "VERSION_EXISTS",
    "VERSION_STATUS_INVALID",
    "VERSION_PACKAGE_MISSING",
    "VERSION_UPLOAD_FAILED",
    "VERSION = Pattern.compile",
    "compareVersion",
    "parseVersion",
    "murmurBucket",
    "createWithOptionalFile",
    "UpdateStrategy.GRADUAL",
    "VERSION_PUBLISH",
    "VERSION_REVOKE",
    "alternative.status() != VersionStatus.PUBLISHED",
    "version.report_interval_hours",
    "version.max_file_size_mb",
    "cos://desktop-releases/",
]:
    if token not in service:
        errors.append(f"DesktopVersionService missing {token}")

repo = read("src/main/java/com/privateflow/modules/versions/DesktopVersionRepository.java")
for token in [
    "desktop_versions",
    "desktop_client_versions",
    "ON DUPLICATE KEY UPDATE",
    "status = 'PUBLISHED'",
    "ORDER BY published_at DESC",
]:
    if token not in repo:
        errors.append(f"DesktopVersionRepository missing {token}")

migration = read("src/main/resources/db/migration/V26__module_48_version_management.sql")
for token in [
    "CREATE TABLE IF NOT EXISTS desktop_versions",
    "CREATE TABLE IF NOT EXISTS desktop_client_versions",
    "uk_version_platform",
    "idx_platform_status_published",
    "uk_client_id",
    "version.max_file_size_mb",
    "version.cos_upload_timeout_s",
    "version.report_interval_hours",
]:
    if token not in migration:
        errors.append(f"migration missing {token}")

codes = read("src/main/java/com/privateflow/modules/api/ApiErrorCodes.java")
for token in ["VERSION_EXISTS", "80-10010", "VERSION_STATUS_INVALID", "80-10011", "VERSION_PACKAGE_MISSING", "80-10012", "VERSION_UPLOAD_FAILED", "80-10013"]:
    if token not in codes:
        errors.append(f"ApiErrorCodes missing {token}")

config = read("src/main/java/com/privateflow/modules/api/config/ConfigAdminService.java")
for token in [
    'key.startsWith("version.")',
    '"version.max_file_size_mb"',
    '"version.cos_upload_timeout_s"',
    '"version.report_interval_hours"',
]:
    if token not in config:
        errors.append(f"ConfigAdminService missing {token}")

progress = read("dev-progress/48_progress.md")
for token in [
    "python scripts\\verify_module_48.py",
    "desktop_versions",
    "desktop_client_versions",
    "Version comparison parses",
]:
    if token not in progress:
        errors.append(f"progress missing {token}")

if errors:
    raise SystemExit("\n".join(errors))

print("module 48 verification passed")
