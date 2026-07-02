# 47 Progress - Admin Module H Analytics Dashboard

## Scope
- Implemented read-only operations analytics APIs under `/admin/api/v1/analytics/*`.
- No migration or new table was added; module 47 consumes `skill_call_logs`, `audit_logs`, `customers`, `accounts`, and `system_alerts`.

## Functional Checklist
- [x] `GET /admin/api/v1/analytics/overview`
- [x] `GET /admin/api/v1/analytics/funnels`
- [x] `GET /admin/api/v1/analytics/staff`
- [x] `GET /admin/api/v1/analytics/sources`
- [x] `GET /admin/api/v1/analytics/stages`
- [x] `GET /admin/api/v1/analytics/health`
- [x] `GET /admin/api/v1/analytics/lifecycle`
- [x] `GET /admin/api/v1/analytics/risks`
- [x] `GET /admin/api/v1/analytics/content-ranking`
- [x] Preserved existing `/admin/api/v1/analytics/skill-calls`.
- [x] KEEPER can access only `GET /admin/api/v1/analytics/overview`; other admin APIs remain forbidden.
- [x] ADMIN can query all callers; LEADER is scoped to self plus current team keepers; KEEPER overview is forced to self.
- [x] `days` is clamped to `1-90`; `leadType` accepts `TUAN_GOU`, `XIAN_SUO`, `PENDING`, or all.

## Implementation Notes
- Adoption rate is v1 approximate: `audit_logs.action='COPY_REPLY' / successful skill calls`.
- Funnel lifecycle uses `customers.updated_at` as the current approximation source.
- Customer stage matching follows the manual's fuzzy Chinese keyword strategy for appointment/arrival stages.
- Content ranking aggregates existing audit actions and returns an empty or coarse ranking if `detail` does not include content identifiers.

## Validation Commands
- `python scripts\verify_module_47.py`
- Full chain: `python scripts\verify_module_a.py` through `python scripts\verify_module_h.py`, `python scripts\verify_module_20.py` through `python scripts\verify_module_33.py`, and `python scripts\verify_module_40.py` through `python scripts\verify_module_47.py`
- `git diff --check`
- Java/Maven environment check: `where.exe mvn`, `where.exe java`, `where.exe javac`
