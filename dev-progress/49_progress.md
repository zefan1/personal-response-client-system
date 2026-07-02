# Module 49 Progress - Admin J System Notices

## Scope

- Manual: `49_运营J_系统公告_开发实现手册.md`
- Module: operations admin J, system notices
- Dependencies: backend H WebSocket push service, admin E JWT roles, system configs, audit logs, MySQL migrations

## Acceptance Checklist

- [x] New `system_notices` table added with `uk_notice_id`, `idx_status_stopped_expire`, `idx_status_publish`, `idx_source`, `idx_created_at`.
- [x] Config defaults added for `notice.max_title_chars`, `notice.max_content_chars`, `notice.default_expire_days`, `notice.max_schedule_days`, `notice.scan_interval_s`, `notice.auto_expire_hours`, `notice.list_page_size`.
- [x] Config update validation accepts `notice.*` and enforces manual ranges.
- [x] Admin APIs implemented:
  - [x] `GET /admin/api/v1/notices`
  - [x] `POST /admin/api/v1/notices`
  - [x] `PUT /admin/api/v1/notices/{id}`
  - [x] `PUT /admin/api/v1/notices/{id}/stop`
  - [x] `DELETE /admin/api/v1/notices/{id}`
- [x] Desktop API implemented: `GET /api/v1/notices/active`.
- [x] Enums implemented exactly: level `INFO/WARN/ERROR`, source `MANUAL/AUTO`, status `PUBLISHED/SCHEDULED`, publishType `IMMEDIATE/SCHEDULED`.
- [x] WS broadcast type implemented: `SYSTEM_NOTICE`.
- [x] WS/desktop payload shape implemented: `noticeId`, `title`, `content`, `level`, `createdAt`, `expireAt`.
- [x] ADMIN/LEADER can manage notices; DELETE requires ADMIN; active fetch works for all authenticated roles through the existing JWT filter.
- [x] Only active `SCHEDULED` notices can be edited.
- [x] Only stopped notices can be deleted.
- [x] Stop does not retract already pushed WS messages; it only marks `is_stopped=1`.
- [x] Scheduled scan publishes due notices and broadcasts after marking them published.
- [x] Internal methods implemented:
  - [x] `createAutoNotice(String title, String content, String level, Duration ttl)`
  - [x] `stopAutoNotice(String contentKeyword)`
- [x] AUTO notices dedupe on same active AUTO content.
- [x] Audit actions written: `CREATE_NOTICE`, `STOP_NOTICE`, `PUBLISH_NOTICE`.
- [x] `system_notices` remains separate from `system_alerts`.
- [x] No attachments/images/rich text added.

## Validation Commands

- `python scripts\verify_module_49.py`
- Full verifier chain through 49:
  - `python scripts\verify_module_a.py`
  - `python scripts\verify_module_b.py`
  - `python scripts\verify_module_c.py`
  - `python scripts\verify_module_d.py`
  - `python scripts\verify_module_e.py`
  - `python scripts\verify_module_f.py`
  - `python scripts\verify_module_g.py`
  - `python scripts\verify_module_h.py`
  - `python scripts\verify_module_20.py` through `python scripts\verify_module_33.py`
  - `python scripts\verify_module_40.py` through `python scripts\verify_module_49.py`
- `git diff --check`
- `where.exe mvn; where.exe java; where.exe javac`

## Notes

- This environment still has no Maven/JDK binaries available, so executable Java compilation is not available here.
- The scheduled query uses `FOR UPDATE SKIP LOCKED` to match the manual's multi-node duplicate-publish guard.
- Config values remain database-backed through `system_configs`; code uses manual defaults if a key is missing.
