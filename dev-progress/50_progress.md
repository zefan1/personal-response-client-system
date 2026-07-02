# Module 50 Progress - Admin K Audit Logs

## Scope

- Manual: `50_运营K_操作审计日志_开发实现手册.md`
- Module: operations admin K, audit log search and export
- Dependencies: backend H JWT/auth, existing `audit_logs`, `AuditLogger`, system configs, async executor

## Acceptance Checklist

- [x] Admin-only query API implemented: `GET /admin/api/v1/audit-logs`.
- [x] Admin-only action metadata API implemented: `GET /admin/api/v1/audit-logs/actions`.
- [x] Admin-only export API implemented: `POST /admin/api/v1/audit-logs/export`.
- [x] Export status polling API implemented: `GET /admin/api/v1/audit-logs/export/{exportId}`.
- [x] Protected CSV download endpoint implemented: `GET /admin/api/v1/audit-logs/export/{exportId}/download`.
- [x] Query supports action multi-select, operator prefix search, targetType, targetId, keyword, date range, page, size.
- [x] Query defaults to 7 days through today and clamps page size by DB-backed config.
- [x] Response masks `operator` as first 3 + `****` + last 4.
- [x] Response enriches `actionLabel`, `actionGroup`, `targetTypeLabel`, `detailParsed`, `detailSummary`.
- [x] detail JSON parse failure returns `detailParsed = null` instead of failing the request.
- [x] CSV export enforces `audit.export_max_rows`.
- [x] CSV export is asynchronous and writes PROCESSING / COMPLETED / FAILED task state.
- [x] CSV content starts with UTF-8 BOM and uses headers `操作时间,操作人,操作类型,操作对象,操作摘要,详情`.
- [x] Audit log APIs are read-only for `audit_logs`; no edit/delete endpoint is exposed.
- [x] Config defaults added:
  - [x] `system.audit_log_cleanup_batch_size = 5000`
  - [x] `audit.export_max_rows = 10000`
  - [x] `audit.export_cos_retention_hours = 168`
  - [x] `audit.export_timeout_seconds = 120`
  - [x] `audit.list_page_size_default = 20`
  - [x] `audit.list_max_page_size = 100`
- [x] Config validation accepts `audit.*` and validates audit/system audit ranges.
- [x] Formal audit indexes added while preserving existing table/data.
- [x] Daily cleanup task implemented at `0 0 4 * * *` using retention and batch-size configs.

## Validation Commands

- `python scripts\verify_module_50.py`
- Full verifier chain through 50:
  - `python scripts\verify_module_a.py`
  - `python scripts\verify_module_b.py`
  - `python scripts\verify_module_c.py`
  - `python scripts\verify_module_d.py`
  - `python scripts\verify_module_e.py`
  - `python scripts\verify_module_f.py`
  - `python scripts\verify_module_g.py`
  - `python scripts\verify_module_h.py`
  - `python scripts\verify_module_20.py` through `python scripts\verify_module_33.py`
  - `python scripts\verify_module_40.py` through `python scripts\verify_module_50.py`
- `git diff --check`
- `where.exe mvn; where.exe java; where.exe javac`

## Notes

- The environment still has no Maven/JDK binaries, so Java compilation cannot be run locally.
- No external COS SDK exists in this repo; export files are persisted in `audit_log_exports` and exposed through an ADMIN-protected download endpoint rather than returning a fake COS URL.
- Existing audit writers are preserved; module 50 adds query/presentation/export behavior and config/index support.
