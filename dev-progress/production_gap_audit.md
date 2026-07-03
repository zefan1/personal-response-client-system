# Production Gap Audit

## Audit Position

This repository is not production-complete yet. The current evidence proves a runnable baseline plus partial backend API acceptance, not full production acceptance.

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
- Backend API acceptance harness exists:
  - `python3 scripts/acceptance_backend_api.py`
  - Latest passing evidence: 99 calls passed in mock external mode.

## Addressed Since Initial Audit

- Datasource mapping compare no longer returns a fixed placeholder; it now returns a structured diff against the latest mapping snapshot.
- Datasource columns no longer returns only an empty placeholder; it now samples `SheetClient` rows when available and otherwise exposes mapped columns with `fetchStatus`.
- Datasource import logs no longer returns a fixed empty list; it now reads persisted `customer_import_log` rows.

## Hard Production Gaps

### P0 - No Real Java Test Coverage

- Maven still reports `No tests to run`.
- Current verifiers are static contract/token checks plus an external Python acceptance harness, not Java integration tests.
- Required before production:
  - controller integration tests for all API groups
  - service tests for failure branches
  - repository tests against real MySQL-compatible database
  - desktop store/component tests or scripted browser checks for all clickable paths

### P0 - Admin Frontend Is Missing

- Modules `40-51` are implemented as backend Admin APIs.
- There is no dedicated admin web frontend in the repository.
- Existing desktop renderer covers desktop/sidebar modules only.
- Production cannot claim "all interfaces visible" until admin pages exist for skill scenes, AI configuration, datasource mapping, quick search management, accounts, followup rules, tags, analytics, desktop versions, notices, audit logs, and health dashboard.

### P0 - Real External Providers Not Accepted

- Runtime smoke and API acceptance currently use `MOCK_EXTERNALS=true`.
- Real provider acceptance is missing for:
  - Skill API
  - image recognition API
  - WeCom table read/write API
- `MOCK_EXTERNALS=false` still needs complete configuration and endpoint-level validation.
- `UnavailableSheetClient` and `UnavailableWecomTableClient` remain production blockers unless real clients are supplied.

### P1 - API Behavior Coverage Still Incomplete

- The acceptance harness covers representative flows, not all 115 HTTP mappings and every invalid/permission/conflict branch.
- Remaining work:
  - list/detail/create/update/delete for every module where applicable
  - toggle/restore/publish/revoke/stop branches
  - filters/pagination/sorting edge cases
  - upload/download/export
  - empty data, invalid input, permission denied, conflict, dependency failure

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
- Desktop renderer still has no login flow; it expects `localStorage.desktop_config.accessToken`.
- Desktop package has Vite dev preview, but no packaged Electron launch script in `package.json`.

## Recommended Repair Order

1. Rerun backend API acceptance after datasource hardening and fix any regressions.
2. Add Java integration tests so `mvn test` no longer reports `No tests to run`.
3. Add desktop login/session setup and a repeatable browser visual smoke suite.
4. Create or scaffold the admin frontend for modules `40-51`.
5. Replace unavailable external clients with real configurable provider clients and validate `MOCK_EXTERNALS=false`.
6. Run full route, UI, and external-provider acceptance before making any production-complete claim.

## Current Acceptance Verdict

- Runnable baseline: passed.
- Backend mock-runtime representative API acceptance: passed for the current harness.
- Production-ready SaaS: not passed.
