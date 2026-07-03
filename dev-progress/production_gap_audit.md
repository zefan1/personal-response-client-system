# Production Gap Audit

## Audit Position

This repository is not production-complete yet. The current evidence proves a runnable baseline, not full production acceptance.

## Verified Evidence

- Module inventory exists for 34 actual modules: `01A-01H`, `20-33`, `40-51`.
- Backend compiles with Java 17:
  - `mvn -Dstyle.color=never clean test`
  - Result: `BUILD SUCCESS`, but `No tests to run`.
- Desktop renderer type-checks:
  - `cd desktop && npm run typecheck`
- Static module verifiers pass:
  - `verify_module_a.py` through `verify_module_h.py`
  - `verify_module_20.py` through `verify_module_33.py`
  - `verify_module_40.py` through `verify_module_51.py`
- Runtime smoke passes with WSL MariaDB + Redis and mock external providers:
  - `bash scripts/smoke_backend_wsl.sh`
  - Verified auth config, admin login, health endpoint, 19 Flyway migrations, 31 tables.
- Runtime database evidence from `private_domain_assistant_smoke`:
  - tables: 31
  - system configs: 126
  - seeded accounts: 1
  - successful Flyway migrations: 19

## Hard Production Gaps

### P0 - No Real Test Coverage

- Maven reports `No tests to run`.
- Current verifiers are static contract/token checks, not behavior tests.
- Required before production:
  - controller integration tests for all API groups
  - service tests for failure branches
  - repository tests against real MySQL-compatible database
  - desktop store/component tests or scripted browser checks for all clickable paths

### P0 - Admin Frontend Is Missing

- Modules `40-51` are implemented as backend Admin APIs.
- There is no dedicated admin web frontend in the repository.
- Existing desktop renderer covers desktop/sidebar modules only.
- Production cannot claim "all interfaces visible" until admin pages exist for:
  - Skill scenes
  - AI configuration
  - datasource mapping
  - quick search management
  - accounts and permissions
  - followup rules
  - tags
  - analytics
  - desktop versions
  - notices
  - audit logs
  - health dashboard

### P0 - Real External Providers Not Accepted

- Runtime smoke currently uses `MOCK_EXTERNALS=true`.
- Real provider acceptance is missing for:
  - Skill API
  - image recognition API
  - WeCom table read/write API
- `MOCK_EXTERNALS=false` still needs complete configuration and endpoint-level validation.

### P0 - API Behavior Coverage Is Incomplete

- Current source exposes 115 HTTP mappings.
- Only auth config, admin login, and health were runtime-smoked.
- Need a route-by-route acceptance suite covering:
  - list/detail/create/update/delete
  - toggle/restore/publish/revoke/stop
  - filters/pagination/sorting
  - upload/download/export
  - empty data, invalid input, permission denied, conflict, dependency failure

### P1 - Known Placeholder / Incomplete Behaviors

Observed source-level gaps requiring implementation or explicit product decision:

- `DatasourceAdminController.compare` returns `"manual comparison pending"`.
- `DatasourceAdminService.columns` returns fallback empty columns instead of real column fetch.
- `DatasourceAdminService.importLogs` returns empty logs.
- `UnavailableSheetClient` and `UnavailableWecomTableClient` are production placeholders unless real clients are supplied.
- Desktop renderer has no login flow; it expects `desktop_config.accessToken` in `localStorage`.
- Desktop package has Vite dev preview, but no packaged Electron launch script in `package.json`.

### P1 - Database / Repository Alignment Not Fully Audited

- Flyway migrations apply successfully to MariaDB.
- This proves DDL can run, but not that every repository query maps all columns correctly.
- Required:
  - generate actual schema snapshot from `information_schema`
  - compare each repository SELECT/INSERT/UPDATE with real columns
  - verify nullable/default assumptions
  - verify all enum strings are accepted by service validation and UI option lists

### P1 - Frontend Runtime Coverage Is Incomplete

- Desktop modules exist under `desktop/src/renderer/modules`.
- Vite page can be served, but no Playwright/browser flow has been completed.
- Need browser checks for:
  - chat recognition
  - reply suggestions
  - customer profile search/edit/save
  - followup list tabs
  - new lead toast
  - quick search
  - batch template
  - help mode
  - abnormal alert
  - workbench
  - offline states

## Recommended Repair Order

1. Build an automated backend acceptance harness for the 115 routes using the running MariaDB/Redis environment.
2. Fix the known placeholders in datasource/admin flows.
3. Add desktop login/session setup and a repeatable browser visual smoke suite.
4. Create or scaffold the admin frontend for modules `40-51`.
5. Replace unavailable external clients with real configurable provider clients and validate `MOCK_EXTERNALS=false`.
6. Add Java integration tests so `mvn test` no longer reports `No tests to run`.
7. Run full route, UI, and external-provider acceptance before making any production-complete claim.

## Current Acceptance Verdict

- Runnable baseline: passed.
- Production-ready SaaS: not passed.
- Safe next action: start P0 backend route acceptance and fix all failures module by module.
