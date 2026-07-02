# Module 51 Progress - Admin L Health Monitor

## Scope

- Manual: `51_运营L_系统健康监控_开发实现手册.md`
- Module: operations admin L, system health monitor
- Dependencies: backend H `/admin/api/v1/health`, `system_alerts`, Skill/Image/Table health state, system configs

## Acceptance Checklist

- [x] No new business table added; module extends existing health endpoint and system config defaults.
- [x] `GET /admin/api/v1/health` returns health snapshot for ADMIN/LEADER.
- [x] KEEPER is denied by service-level role check and existing admin JWT filter.
- [x] Response includes top-level `status`, `timestamp`, `components`, `recentAlerts`.
- [x] Component keys match manual: `skill`, `imageRecognition`, `wecomTable`, `redis`, `db`.
- [x] Component status values use `UP` / `DOWN` / `DEGRADED` / `UNKNOWN`.
- [x] Component fields include `lastCheckedAt` and ISO-8601 `duration`.
- [x] Skill detail includes `circuitState`, `successRate5min`, `totalCalls5min`.
- [x] Image detail includes `consecutiveFailures`, `lastError`.
- [x] WeCom table detail includes `pendingCount`, `staleFailedCount`.
- [x] `recentAlerts` queries `system_alerts` by configurable last N days, ACTIVE first, newest first, bounded limit.
- [x] `recentAlerts` response fields: `alertId`, `alertType`, `alertLevel`, `status`, `detail`, `occurredAt`, `resolvedAt`.
- [x] Config defaults added:
  - [x] `health.refresh_interval_s = 30`
  - [x] `health.alert_history_days = 7`
  - [x] `health.alert_history_max = 100`
- [x] Config update validation accepts `health.*` and enforces manual ranges.
- [x] No WS push, browser notification, direct desktop integration, or health detection engine was added.

## Validation Commands

- `python scripts\verify_module_51.py`
- Full verifier chain through 51:
  - `python scripts\verify_module_a.py`
  - `python scripts\verify_module_b.py`
  - `python scripts\verify_module_c.py`
  - `python scripts\verify_module_d.py`
  - `python scripts\verify_module_e.py`
  - `python scripts\verify_module_f.py`
  - `python scripts\verify_module_g.py`
  - `python scripts\verify_module_h.py`
  - `python scripts\verify_module_20.py` through `python scripts\verify_module_33.py`
  - `python scripts\verify_module_40.py` through `python scripts\verify_module_51.py`
- `git diff --check`
- `where.exe mvn; where.exe java; where.exe javac`

## Notes

- The environment still has no Maven/JDK binaries, so Java compilation cannot be run locally.
- The manual frames module 51 as a frontend page, but this repo currently contains the backend API implementation path; the backend health response was upgraded to satisfy the page contract.
- Existing health endpoint remains at `/admin/api/v1/health`; no extra route was introduced.
