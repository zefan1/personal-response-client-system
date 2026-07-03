# Production Gap Audit

## Audit Position

This repository is not production-complete yet. The current evidence proves a runnable baseline, full mapped-route acceptance in the current harness, and controlled non-mock external HTTP acceptance. It still does not prove live third-party provider acceptance, exhaustive branch coverage, or signed installer readiness.

## Verified Evidence

- Module inventory exists for 34 actual modules: `01A-01H`, `20-33`, `40-51`.
- Backend compiles and runs Java tests with Java 17:
  - `mvn -Dstyle.color=never clean test`
  - Latest result: `BUILD SUCCESS`, `Tests run: 17, Failures: 0, Errors: 0, Skipped: 0`.
  - Coverage now includes AuthService, JwtAuthenticationFilter preflight behavior, DatasourceAdminService, DesktopVersionController error/status mapping, and DesktopVersionRepository SQL persistence/upsert behavior.
- Desktop renderer type-checks:
  - `cd desktop && npm run typecheck`
  - Latest rerun after offline Vitest coverage: passed.
- Desktop renderer unit tests pass:
  - `cd desktop && npm run test`
  - Latest result: 1 test file, 5 tests passed for offline manager failure/recovery branches.
- Desktop build and Electron smoke pass:
  - `cd desktop && npm run build`
  - `cd desktop && npm run electron:smoke`
  - Latest rerun after Electron/Vite security upgrades: passed.
- Desktop dependency audit passes:
  - `cd desktop && npm audit --json`
  - Latest result: 0 vulnerabilities after upgrading Vite and Electron.
- Desktop packaged directory verification passes:
  - `cd desktop && npm run package:verify`
  - Latest result: Windows x64 directory artifact created under `desktop/release/Private Domain Assistant-win32-x64`, `app.asar` present with SHA-256 report, `signed=false` because no production code-signing certificate is configured locally.
- Renderer click smoke passes:
  - `PDA_SMOKE_API_BASE_URL=http://<WSL-IP>:8080 cd desktop && npm run renderer:smoke`
  - Covers login, dynamic traversal of all admin navigation sections, API-backed read/action panels, and desktop workbench switch.
  - Latest rerun used `http://172.19.250.154:8080` because Windows localhost forwarding to WSL was unavailable; result: passed.
- Static module verifiers pass:
  - `verify_module_a.py` through `verify_module_h.py`
  - `verify_module_20.py` through `verify_module_33.py`
  - `verify_module_40.py` through `verify_module_51.py`
- Runtime smoke passes with WSL MariaDB + Redis and mock external providers:
  - `bash scripts/smoke_backend_wsl.sh`
- Backend API acceptance harness exists:
  - `python3 scripts/acceptance_backend_api.py`
- Latest passing evidence: 125 calls passed in mock external mode.
  - Latest rerun command: `python3 scripts/acceptance_backend_api.py --no-start`
  - Latest rerun result after negative matrix expansion: `passed=157 failed=0 total=157`.
  - Coverage categories reported by the harness: `conflict:8`, `create:4`, `download:1`, `invalid:13`, `permission:6`, `read:34`, `representative:89`, `update:2`.
- API mapping coverage audit exists:
  - `python scripts/verify_api_mapping_coverage.py`
  - Latest result: 113 mappings, 113 covered/matched, 0 remaining route gaps, 0 unclassified gaps.
- Browser admin smoke passed against the Vite renderer and local backend:
  - URL: `http://127.0.0.1:5173/`
  - Login: `admin/admin123`
  - Checked admin sections: health/config, skill bindings, AI/external environments, datasource mappings, accounts, notices/versions/audit.
- Real external readiness verifier exists:
  - `python scripts/verify_real_external_readiness.py`
  - Latest result: `mockExternalsFalseReady=true`, source/config blockers are cleared.
- Controlled non-mock external acceptance now passes against a local HTTP provider while the backend runs with `MOCK_EXTERNALS=false`:
  - `python scripts/acceptance_real_external_local.py`
  - Latest result: `passed=true checks=30`.
  - Covered real HTTP client paths: Skill `/v1/chat/completions`, image `/v1/chat/completions`, WeCom table rows GET/PUT, admin environment create/activate, datasource create/mapping/columns, and customer save-to-table.
- Database alignment verifier now checks required columns, every table declared by migrations, config keys inserted by migrations, and static repository SQL table references:
  - It now also verifies selected production-critical nullable/default assumptions and live enum values for accounts, customers, skill bindings, quick search, desktop versions, notices, and audit exports.
  - Latest result: 31 live tables, 30 migration-declared tables, 0 missing migration tables, 0 missing config keys, 0 missing repository tables, 0 column property violations, 0 enum violations.

## Addressed Since Initial Audit

- Datasource mapping compare no longer returns a fixed placeholder; it now returns a structured diff against the latest mapping snapshot.
- Datasource columns no longer returns only an empty placeholder; it now samples `SheetClient` rows when available and otherwise exposes mapped columns with `fetchStatus`.
- Datasource import logs no longer returns a fixed empty list; it now reads persisted `customer_import_log` rows.
- Desktop renderer now has a login flow that persists the backend token into `desktop_config`.
- Admin console pages now exist in the desktop/Vite renderer and are backed by real `/admin/api/v1/*` calls rather than static mock data.
- Browser/Vite runtime no longer requires the Electron preload bridge for login/admin smoke testing; desktop bridge calls have web fallbacks where possible.
- Backend CORS now permits local Vite origins and the auth filter bypasses OPTIONS preflight.
- WeCom smart table read/write no longer uses unavailable placeholder clients; `HttpWecomTableClient` implements both `SheetClient` and `WecomTableClient` behind `table.api_base_url` / `table.api_key`.
- Skill and image real HTTP clients now unwrap OpenAI-compatible `choices[0].message.content` responses before handing business JSON to the existing parsers.
- Added a local fake external provider plus repeatable `MOCK_EXTERNALS=false` acceptance runner for controlled non-mock verification.
- Backend API acceptance now covers every mapped route in the current route inventory, including chat recognize/generate/regenerate, help request/resolve, prompt restore, audit export status/download, Skill/image test routes via controlled non-mock acceptance, and draft version delete.
- Backend API acceptance now also includes a repeatable invalid/permission/conflict matrix covering unauthenticated requests, malformed bearer token, keeper admin denial, bad login, invalid account/customer/chat/config/datasource/analytics/quick-search/notice/audit/version inputs, duplicate skill/quick-search/version guards, disabled datasource sync conflict, and published-version edit/delete conflicts.
- Database alignment verifier now scans static repository SQL table references and fails if a referenced table is absent from the live smoke schema.
- Customer `lead_type` writes now normalize unknown values to `PENDING` at the repository boundary, and migration `V53__normalize_customer_lead_types.sql` repairs existing out-of-contract customer enum values. This prevents quick-search `GENERAL` from leaking into the customer table.
- Renderer smoke now discovers all admin nav sections at runtime instead of checking a fixed representative subset.
- Desktop toolchain moved off vulnerable Electron/Vite versions; `npm audit --json` currently reports 0 vulnerabilities.
- Added repeatable desktop package verification for Windows x64 unpacked artifacts. It proves build structure and ASAR integrity metadata, while explicitly recording that this local artifact is not signed.
- Java tests now include controller-layer MockMvc checks for version APIs plus H2-backed repository tests for desktop version persistence, publish/revoke, latest published lookup, and desktop client report upsert semantics.

## Hard Production Gaps

### P0 - No Real Java Test Coverage

- Maven now runs 17 Java tests covering AuthService, JwtAuthenticationFilter preflight behavior, DatasourceAdminService, DesktopVersionController, and DesktopVersionRepository.
- Remaining before production:
  - controller integration tests for every remaining API group beyond the current high-risk version API coverage
  - broader service tests for failure branches
  - repository tests against real MySQL-compatible database beyond the live acceptance/database verifier coverage
  - desktop store/component tests or scripted browser checks for all clickable paths

### P0 - Admin Frontend Is Missing

- Addressed for manual acceptance: the desktop/Vite renderer now includes a management console for health/config, skill scenes, AI configuration, datasource mapping, quick search management, accounts, followup rules, tags, analytics, desktop versions, notices, audit logs, and health dashboard.
- Current limitation: the first admin console is a production-connected operations surface with JSON action forms; it is not yet a fully bespoke polished admin product UI for every module workflow.

### P0 - Real External Providers Not Accepted

- Runtime smoke and API acceptance currently use `MOCK_EXTERNALS=true`.
- Skill API and image recognition have configurable real HTTP clients and admin-managed environment configuration.
- WeCom table read/write now has a configurable real HTTP client and `table.api_base_url` / `table.api_key` config keys.
- `MOCK_EXTERNALS=false` source/config readiness passes.
- Controlled non-mock HTTP acceptance passes against the local fake provider:
  - `python scripts/acceptance_real_external_local.py`
  - Latest result: `passed=true checks=30`.
- Real third-party live provider acceptance is still missing because valid external credentials/endpoints have not been supplied.
- Remaining before production: run endpoint-level live tests against real Skill, image recognition, and WeCom gateway credentials.

### P1 - API Behavior Coverage Still Incomplete

- The acceptance harness now covers all 113 mapped HTTP routes in the current route inventory.
- Mapping coverage audit reports 0 route gaps.
- Expanded negative branch matrix now passes with 157 total calls and explicit coverage counts for invalid, permission, and conflict branches.
- Remaining work:
  - continue extending the invalid/permission/conflict matrix beyond the current production-critical branch set
  - deeper response body assertions for every list/detail/create/edit/delete/statistics/export flow
  - live-provider branch replay once real Skill/image/WeCom credentials are available

### P1 - Database / Repository Alignment Not Fully Audited

- Flyway migrations apply successfully to MariaDB.
- Added `scripts/verify_database_alignment.py`, which reads the live smoke database `information_schema`.
- Latest result: 31 tables found, 14 key table column sets checked, 30 migration-declared tables checked, 0 missing required columns, 0 missing migration tables, 0 missing config keys, 0 missing repository tables, 0 nullable/default violations, 0 enum violations.
- Remaining:
  - expand column-level checks to every repository query
  - expand nullable/default checks beyond the current production-critical subset
  - verify every enum string against all service validation and UI option lists

### P1 - Frontend Runtime Coverage Is Incomplete

- Desktop modules exist under `desktop/src/renderer/modules`.
- Vite page can be served and browser smoke has covered login plus representative admin sections.
- Renderer smoke now dynamically traverses every admin navigation section that exists in the DOM.
- Desktop renderer now has a login flow and no longer requires manually editing `localStorage.desktop_config.accessToken`.
- Desktop package now has Vite dev, Electron dev/preview, and Electron smoke scripts.
- Desktop package now has a repeatable Windows x64 directory packaging verifier and package report.
- Desktop now has Vitest/jsdom coverage for offline manager branches: consecutive API network failures, non-network business errors, WS degraded/reconnected handling, debounced OS offline/recovery bridge events, and duplicate offline capability registration.
- Remaining:
  - exhaustive browser click coverage for every desktop/admin workflow and failure branch
  - production certificate-backed code signing and installer/notarization verification
  - expand component/store tests beyond the current offline manager coverage

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
