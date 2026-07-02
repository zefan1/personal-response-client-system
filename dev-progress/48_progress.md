# 48 Progress - Admin Module I Version Management

## Scope
- Implemented desktop release management for operations admins.
- Added desktop startup APIs for version check and version report.
- Added the two required persistence tables: `desktop_versions` and `desktop_client_versions`.

## Functional Checklist
- [x] `GET /admin/api/v1/versions`
- [x] `POST /admin/api/v1/versions`
- [x] `PUT /admin/api/v1/versions/{id}`
- [x] `PUT /admin/api/v1/versions/{id}/publish`
- [x] `PUT /admin/api/v1/versions/{id}/revoke`
- [x] `DELETE /admin/api/v1/versions/{id}`
- [x] `POST /admin/api/v1/versions/upload`
- [x] `GET /api/v1/desktop/version-check`
- [x] `POST /api/v1/desktop/version-report`
- [x] Admin APIs require ADMIN role in service layer.
- [x] Desktop APIs allow any authenticated desktop role through the existing JWT filter.
- [x] Version status enum: `DRAFT / PUBLISHED / REVOKED`.
- [x] Update strategy enum: `FORCED / OPTIONAL / GRADUAL`.
- [x] Platform enum: `WINDOWS / MAC`.
- [x] Version comparison parses `X.Y.Z` numerically instead of string comparing.
- [x] Gradual release uses a stable clientId hash bucket.
- [x] Revoke validates that an alternative version, when supplied, exists and is PUBLISHED.
- [x] Version report uses `INSERT ... ON DUPLICATE KEY UPDATE`.
- [x] Config keys added: `version.max_file_size_mb`, `version.cos_upload_timeout_s`, `version.report_interval_hours`.

## Implementation Notes
- The upload endpoint follows the existing quick-search COS placeholder convention and returns `cos://desktop-releases/...`; admins can still provide an external `downloadUrl`.
- Real COS SDK integration remains an environment/configuration concern and is not mocked as a successful network upload.
- Publish/revoke actions write `VERSION_PUBLISH` and `VERSION_REVOKE` audit logs.

## Validation Commands
- `python scripts\verify_module_48.py`
- Full chain: `python scripts\verify_module_a.py` through `python scripts\verify_module_h.py`, `python scripts\verify_module_20.py` through `python scripts\verify_module_33.py`, and `python scripts\verify_module_40.py` through `python scripts\verify_module_48.py`
- `git diff --check`
- Java/Maven environment check: `where.exe mvn`, `where.exe java`, `where.exe javac`
