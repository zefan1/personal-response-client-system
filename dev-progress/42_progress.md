# 42 Progress

## Baseline

- Module: 42 datasource admin.
- Dependencies: customer cache layer, API/auth layer, table sync primitives.
- Shared contracts: `/admin/api/v1/**`, `datasource.field_mappings`, `datasource.connections`, `cache.*`, `datasource.*`.

## 功能签收清单

- [x] `GET /admin/api/v1/datasources`: returns datasource list, mapping count, last sync time, sync status.
- [x] `POST /admin/api/v1/datasources`: creates datasource with name/sheetId/sourceTable validation.
- [x] `PUT /admin/api/v1/datasources/{id}`: edits datasource connection metadata.
- [x] `DELETE /admin/api/v1/datasources/{id}`: deletes datasource and current mappings.
- [x] `PUT /admin/api/v1/datasources/{id}/toggle`: enables/disables datasource.
- [x] `PUT /admin/api/v1/datasources/{id}/replace`: replaces sheetId while preserving mappings.
- [x] `GET /admin/api/v1/datasources/{id}/mappings`: returns current mappings and current version.
- [x] `PUT /admin/api/v1/datasources/{id}/mappings`: replaces mappings, validates one enabled mapping per targetField, snapshots version.
- [x] `GET /admin/api/v1/datasources/{id}/mappings/versions`: returns latest 20 mapping versions.
- [x] `POST /admin/api/v1/datasources/{id}/mappings/restore`: restores a historical mapping version and creates a new version.
- [x] `GET /admin/api/v1/datasources/{id}/mappings/compare`: returns structured diff against latest snapshot: added/removed/changed/unchanged.
- [x] `GET /admin/api/v1/datasources/{id}/columns`: extracts columns from SheetClient sample rows when available; falls back to saved mappings with explicit `fetchStatus`, `source`, and `externalFetchAvailable`.
- [x] `GET /admin/api/v1/customer-fields`: reflects editable Customer business fields and excludes system fields.
- [x] `GET /admin/api/v1/datasources/sync-status`: returns datasource sync status and unresolved failures.
- [x] `POST /admin/api/v1/datasources/{id}/sync`: triggers one scheduler run and returns accepted datasource metadata.
- [x] `POST /admin/api/v1/datasources/import`: imports CSV, validates `phone`, caps rows at 5000, skips duplicate phones in the same file.
- [x] CSV import only fills supported basic fields and does not overwrite existing non-empty nickname.
- [x] `GET /admin/api/v1/datasources/import-logs`: returns recent persisted import logs from `customer_import_log` with total count.
- [x] Tables: `datasources`, `datasource_mapping_versions`, `customer_import_log`.
- [x] Default configs: `datasource.mapping_version_max=50`, `datasource.import_max_rows=5000`, `datasource.manual_sync_timeout_s=60`, `datasource.sync_status_refresh_s=30`.
- [x] Datasource/mapping changes publish `ConfigChangedEvent` and WS `CONFIG_REFRESH`.

## 验证命令

- `python scripts/verify_module_42.py`
- `mvn test`
- `python scripts/verify_module_a.py; python scripts/verify_module_b.py; python scripts/verify_module_c.py; python scripts/verify_module_d.py; python scripts/verify_module_e.py; python scripts/verify_module_f.py; python scripts/verify_module_g.py; python scripts/verify_module_h.py; python scripts/verify_module_20.py; python scripts/verify_module_21.py; python scripts/verify_module_22.py; python scripts/verify_module_23.py; python scripts/verify_module_24.py; python scripts/verify_module_25.py; python scripts/verify_module_26.py; python scripts/verify_module_27.py; python scripts/verify_module_28.py; python scripts/verify_module_29.py; python scripts/verify_module_30.py; python scripts/verify_module_31.py; python scripts/verify_module_32.py; python scripts/verify_module_33.py; python scripts/verify_module_40.py; python scripts/verify_module_41.py; python scripts/verify_module_42.py`
- `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && MAVEN_OPTS="-Dstyle.color=never" mvn -Dstyle.color=never test'`
- `wsl -d Ubuntu -- bash -lc 'cd /mnt/c/Users/85314/Desktop/私域工具/私域辅助系统 && python3 scripts/acceptance_backend_api.py'`
- `git diff --check`

## 假设与传播记录

- `SheetClient` has row-fetch capability but no dedicated metadata endpoint. Columns are inferred from sampled rows when the configured client can fetch them, otherwise from saved mappings.
- Real WeCom table metadata still depends on implementing the production `SheetClient`; this module now exposes the fetch status instead of hiding that dependency.
- CSV import remains intentionally narrow: `phone` plus optional `nickname`, with persisted import logs for audit.
