# Production Gap Audit

## Audit Position

This repository is not production-complete yet. The current evidence proves a runnable baseline, full mapped-route acceptance in the current harness, and controlled non-mock external HTTP acceptance. It still does not prove live third-party provider acceptance, exhaustive branch coverage, or signed installer readiness.

## Verified Evidence

- Module inventory exists for 34 actual modules: `01A-01H`, `20-33`, `40-51`.
- Backend compiles and runs Java tests with Java 17:
  - `mvn -Dstyle.color=never clean test`
  - Latest current-state rerun from WSL because Windows PowerShell has no `mvn` on PATH: `wsl bash -lc "cd '/mnt/c/Users/85314/Desktop/私域工具/私域辅助系统' && mvn -Dstyle.color=never test"`, `BUILD SUCCESS`, `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused rerun: `mvn -Dstyle.color=never -Dtest=AiConfigControllerTest test`, `BUILD SUCCESS`, `Tests run: 10, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused analytics rerun: `mvn -Dstyle.color=never -Dtest=AnalyticsControllerTest test`, `BUILD SUCCESS`, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused tags rerun: `mvn -Dstyle.color=never -Dtest=TagAdminControllerTest test`, `BUILD SUCCESS`, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused followup rerun: `mvn -Dstyle.color=never -Dtest=FollowupControllerTest test`, `BUILD SUCCESS`, `Tests run: 6, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused datasource controller rerun: `mvn -Dstyle.color=never -Dtest=DatasourceAdminControllerTest test`, `BUILD SUCCESS`, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused customer controller rerun: `mvn -Dstyle.color=never -Dtest=CustomerControllerTest test`, `BUILD SUCCESS`, `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused chat controller rerun: `mvn -Dstyle.color=never -Dtest=ChatControllerTest test`, `BUILD SUCCESS`, `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest focused web core rerun: `mvn -Dstyle.color=never -Dtest=WebCoreControllerTest test`, `BUILD SUCCESS`, `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`.
  - Latest full-suite result after renderer desktop smoke expansion: `mvn -Dstyle.color=never test`, `BUILD SUCCESS`, `Tests run: 107, Failures: 0, Errors: 0, Skipped: 0`.
  - Coverage now includes AuthService, JwtAuthenticationFilter preflight behavior, DatasourceAdminService, DatasourceAdminController all datasource CRUD/toggle/replace/mapping/version/restore/compare/columns/customer-fields/sync/import routes and error mapping, AnalyticsController overview/funnels/staff/sources/stages/health/lifecycle/risks/content-ranking binding and error mapping, AccountAdminController list/create/update/toggle/reset/delete/error mapping, AuditLogController list/actions/export/status/download/error mapping, AuthController desktop/admin login, refresh, and auth config, ConfigController list/get/update, HealthController, HelpController request/resolve, user QuickSearchController list, ChatController recognize/generate/regenerate/send-confirm binding and error mapping, CustomerController search/profile/batch/update/suggestion-resolve/save-to-table and local exception mappings, FollowupController today/rules CRUD/toggle/search/error mapping, NoticeController list/create/update/stop/delete/active/error mapping, QuickSearchAdminController list/create/update/toggle/delete/upload/error mapping, SkillAdminController list/create/update/toggle/delete/available/test/analytics/error mapping, AiConfigController Skill/Image environment CRUD/activate/delete/test and prompt version restore/error mapping, TagAdminController category/value CRUD/toggle and tag business error mapping, DesktopVersionController error/status mapping, and DesktopVersionRepository SQL persistence/upsert behavior.
- Desktop renderer type-checks:
  - `cd desktop && npm run typecheck`
  - Latest rerun after renderer desktop smoke expansion: passed.
- Desktop renderer unit tests pass:
  - `cd desktop && npm run test`
  - Latest result: 14 test files, 103 tests passed for offline manager failure/recovery branches, quick-search store cache/search/copy/failure behavior, workbench store metrics/sorting/new-lead/notice/load-failure/event behavior, save-to-table profile persistence/retry/pending/sync behavior, followup-list grouping/selection/reminder/event behavior, customer-profile search/cache/edit/suggestion/event behavior, reply-suggestion loading/fallback/regenerate/help/suggestion/event behavior, chat-recognition dedupe/concurrency/status/error/event behavior, copy-backfill clipboard/send-confirm/suggestion-toast behavior, help-mode request/resolve/reply event behavior, batch-template customer/template/copy/confirm behavior, stage-suggestion event/confirm/ignore behavior, new-lead-toast queue/copy/event behavior, and abnormal-alert validation/history/ack/router behavior.
- Desktop build and Electron smoke pass:
  - `cd desktop && npm run build`
  - `cd desktop && npm run electron:smoke`
  - Latest build rerun after renderer desktop smoke expansion: passed via `cd desktop && npm run renderer:smoke`. Latest standalone Electron smoke evidence remains from the controller coverage expansion checkpoint.
- Desktop dependency audit passes:
  - `cd desktop && npm audit --json`
  - Latest current-state result after abnormal-alert coverage expansion: 0 vulnerabilities (`critical=0 high=0 moderate=0 low=0`).
- Desktop packaged directory verification passes:
  - `cd desktop && npm run package:verify`
  - Latest current-state result after abnormal-alert coverage expansion: Windows x64 directory artifact created under `desktop/release/Private Domain Assistant-win32-x64`, `app.asar` present with SHA-256 report, `asarBytes=28334580`, `signed=false` because no production code-signing certificate is configured locally.
- Renderer click smoke passes:
  - `PDA_SMOKE_API_BASE_URL=http://<WSL-IP>:8080 cd desktop && npm run renderer:smoke`
  - Covers login, dynamic traversal of all admin navigation sections, API-backed read panel refresh/rendering, action form JSON input presence, structured action controls for simple JSON bodies, desktop workbench switch, desktop panel presence, workbench/followup refresh buttons, all followup tabs, recognition text-mode form, customer search input/button, quick-search overlay, and quick-search lead-type filters.
  - Latest rerun after renderer desktop smoke expansion used `http://172.19.250.154:8080` because Windows localhost forwarding to WSL was unavailable; result: passed.
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
  - Latest rerun result after renderer desktop smoke expansion against `http://172.19.250.154:8080`: `passed=158 failed=0 total=158`.
  - Coverage categories reported by the harness: `conflict:8`, `create:4`, `download:1`, `invalid:13`, `permission:6`, `read:34`, `representative:90`, `update:2`.
- API mapping coverage audit exists:
  - `python scripts/verify_api_mapping_coverage.py`
  - Latest current-state result: 113 mappings, 113 covered/matched, 0 remaining route gaps, 0 unclassified gaps.
- Controller Java test coverage audit exists:
  - `python scripts\verify_controller_test_coverage.py`
  - Latest current-state result: 18 controller classes, 18 covered by direct or documented aggregate controller tests, 0 missing, 14 controller test classes.
- Browser admin smoke passed against the Vite renderer and local backend:
  - URL: `http://127.0.0.1:5173/`
  - Login: `admin/admin123`
  - Checked admin sections: health/config, skill bindings, AI/external environments, datasource mappings, accounts, notices/versions/audit.
- Real external readiness verifier exists:
  - `python scripts/verify_real_external_readiness.py`
  - Latest result: `mockExternalsFalseReady=true`, source/config blockers are cleared.
- Controlled non-mock external acceptance now passes against a local HTTP provider while the backend runs with `MOCK_EXTERNALS=false`:
  - `python scripts/acceptance_real_external_local.py`
  - Latest current-state result: `passed=true checks=30`.
  - Covered real HTTP client paths: Skill `/v1/chat/completions`, image `/v1/chat/completions`, WeCom table rows GET/PUT, admin environment create/activate, datasource create/mapping/columns, and customer save-to-table.
- Database alignment verifier now checks required columns, every table declared by migrations, config keys inserted by migrations, and static repository SQL table references:
  - It now also verifies selected production-critical nullable/default assumptions, live enum values for accounts, customers, skill bindings, quick search, desktop versions, notices, and audit exports, and scanned repository SQL column references for INSERT/UPDATE/table-alias qualified columns.
  - Latest current-state result: 31 live tables, 30 migration-declared tables, 0 missing migration tables, 0 missing config keys, 0 missing repository tables, 0 repository column violations, 0 column property violations, 0 enum violations.
- Enum contract alignment verifier now checks production-critical enum strings across backend Java enums/constants, service validation allowlists, database enum allowlists, and frontend visible/manual-acceptance option paths:
  - `python scripts\verify_enum_contract_alignment.py`
  - Latest current-state result: 40 contract checks, 0 mismatches, 4 documented frontend exposure exceptions for JSON-form/admin-default-only controls.

## Addressed Since Initial Audit

- Datasource mapping compare no longer returns a fixed placeholder; it now returns a structured diff against the latest mapping snapshot.
- Datasource columns no longer returns only an empty placeholder; it now samples `SheetClient` rows when available and otherwise exposes mapped columns with `fetchStatus`.
- Datasource import logs no longer returns a fixed empty list; it now reads persisted `customer_import_log` rows.
- Desktop renderer now has a login flow that persists the backend token into `desktop_config`.
- Admin console pages now exist in the desktop/Vite renderer and are backed by real `/admin/api/v1/*` calls rather than static mock data.
- Admin action panels now expose structured controls for simple JSON body fields: enums render as select boxes, booleans as checkboxes, numbers as numeric inputs, and text fields as inputs while preserving the raw JSON editor for complex payloads.
- Browser/Vite runtime no longer requires the Electron preload bridge for login/admin smoke testing; desktop bridge calls have web fallbacks where possible.
- Backend CORS now permits local Vite origins and the auth filter bypasses OPTIONS preflight.
- WeCom smart table read/write no longer uses unavailable placeholder clients; `HttpWecomTableClient` implements both `SheetClient` and `WecomTableClient` behind `table.api_base_url` / `table.api_key`.
- Skill and image real HTTP clients now unwrap OpenAI-compatible `choices[0].message.content` responses before handing business JSON to the existing parsers.
- Added a local fake external provider plus repeatable `MOCK_EXTERNALS=false` acceptance runner for controlled non-mock verification.
- Backend API acceptance now covers every mapped route in the current route inventory, including chat recognize/generate/regenerate, help request/resolve, prompt restore, audit export status/download, Skill/image test routes via controlled non-mock acceptance, and draft version delete.
- Backend API acceptance now also includes a repeatable invalid/permission/conflict matrix covering unauthenticated requests, malformed bearer token, keeper admin denial, bad login, invalid account/customer/chat/config/datasource/analytics/quick-search/notice/audit/version inputs, duplicate skill/quick-search/version guards, disabled datasource sync conflict, and published-version edit/delete conflicts.
- Backend API acceptance response assertions now validate more returned state after high-risk create/update/toggle/publish/revoke flows, including accounts, skill bindings, datasource mapping/replace/toggle, quick search item update/list, followup rules, notices, audit export completion/download, desktop version publish/report/revoke, and Mac draft creation.
- Database alignment verifier now scans static repository SQL table references and fails if a referenced table is absent from the live smoke schema.
- Database alignment verifier now also scans repository SQL INSERT columns, UPDATE assignments, and alias-qualified `table.column` references and fails if any scanned column is absent from the live smoke schema.
- Customer `lead_type` writes now normalize unknown values to `PENDING` at the repository boundary, and migration `V53__normalize_customer_lead_types.sql` repairs existing out-of-contract customer enum values. This prevents quick-search `GENERAL` from leaking into the customer table.
- Added `scripts/verify_enum_contract_alignment.py` and frontend/manual acceptance coverage for missing enum paths: quick search `MINI_PROGRAM`, scheduled notice creation, and Mac desktop version creation.
- Renderer smoke now discovers all admin nav sections at runtime instead of checking a fixed representative subset.
- Renderer smoke now also verifies every admin section has API-backed read panels that refresh into rendered JSON and that action panels expose editable JSON request bodies.
- Renderer smoke now also verifies desktop-mode primary panels and non-destructive clickable paths: workbench/followup refresh controls, followup tab switching, chat recognition text mode, customer search, quick-search overlay, and quick-search filters.
- Desktop toolchain moved off vulnerable Electron/Vite versions; `npm audit --json` currently reports 0 vulnerabilities.
- Added repeatable desktop package verification for Windows x64 unpacked artifacts. It proves build structure and ASAR integrity metadata, while explicitly recording that this local artifact is not signed.
- Java tests now include controller-layer MockMvc checks for version APIs plus H2-backed repository tests for desktop version persistence, publish/revoke, latest published lookup, and desktop client report upsert semantics.
- Java tests now also include QuickSearch admin controller MockMvc coverage for list, create, invalid create error mapping, update, toggle, delete, and image upload response wrapping.
- Java tests now also include Account admin controller MockMvc coverage for list filters, enum query binding errors, create/update/toggle/reset/delete, and service validation error mapping. Global API exception handling now maps request parameter type mismatches, including bad enum query values, to standard `80-10001` bad request responses instead of generic 500 responses.
- Java tests now also include Notice controller MockMvc coverage for list filters, invalid status filter handling, immediate create, scheduled update, stop, delete, active desktop payloads, and status-conflict HTTP 409 mapping.
- Java tests now also include Skill admin controller MockMvc coverage for scene/leadType filters, invalid enum query handling, create/update/toggle/delete, available skills, Skill test responses, analytics filters, and SkillAdminException bad request mapping.
- Java tests now also include Audit log controller MockMvc coverage for list query construction, invalid date handling, action metadata, export request construction, export status, CSV download headers/body, and export error mapping.
- Java tests now also include AI config controller MockMvc coverage for Skill/Image environment list/create/update/activate/delete/test paths, validation/service error mapping, prompt version listing, prompt restore, and unsupported prompt type handling.
- Java tests now also include Analytics controller MockMvc coverage for all nine admin report endpoints, days/leadType/caller query binding, returned payload wrapping, invalid `days` bad request handling, and service permission failure mapping to 403.
- Java tests now also include Tag admin controller MockMvc coverage for category list/create/update/delete, value create/update/toggle/delete, builtin-category deletion 403 mapping, and duplicate value bad-request mapping.
- Java tests now also include Followup controller MockMvc coverage for today's followups, rule search criteria binding, rule create/update/delete/toggle, invalid action type standard bad-request body, condition parse failures, and forbidden failures.
- Java tests now also include Datasource admin controller MockMvc coverage for all 16 datasource management routes, including CRUD, toggle, replace, mapping save/version/restore/compare, column/customer-field metadata, sync status/start, CSV multipart import, import logs, and conflict error mapping.
- Java tests now also include Customer controller MockMvc coverage for search, profile, batch dedupe and not-found skipping, manual update, suggestion batch resolve, save-to-table, empty batch validation, customer not found 404, profile version conflict 409, and table-write queue full 429 mappings.
- Java tests now also include Chat controller MockMvc coverage for recognize, generate, regenerate, send-confirm request binding, Skill payload wrapping, warning propagation, accepted send-confirm payloads, and standard bad-request error mapping.
- Java tests now also include Web core controller MockMvc coverage for desktop/admin auth login IP binding, token refresh, auth config, admin config list/get/update, health, help request/resolve, and user quick-search enabled item listing.
- Added `scripts/verify_controller_test_coverage.py` so the controller-layer coverage claim is now machine-checked: all 18 `@RestController` classes are covered by direct or documented aggregate MockMvc controller tests.

## Hard Production Gaps

### P0 - No Real Java Test Coverage

- Maven now runs 107 Java tests covering AuthService, JwtAuthenticationFilter preflight behavior, DatasourceAdminService, DatasourceAdminController, AnalyticsController, AccountAdminController, AuditLogController, AuthController, ConfigController, HealthController, HelpController, user QuickSearchController, ChatController, CustomerController, FollowupController, NoticeController, QuickSearchAdminController, SkillAdminController, AiConfigController, TagAdminController, DesktopVersionController, and DesktopVersionRepository.
- Controller test coverage verifier reports all 18 controller classes covered by direct or documented aggregate controller tests.
- Remaining before production:
  - broader service tests for failure branches
  - repository tests against real MySQL-compatible database beyond the live acceptance/database verifier coverage
  - desktop store/component tests or scripted browser checks for all clickable paths

### P0 - Admin Frontend Is Missing

- Addressed for manual acceptance: the desktop/Vite renderer now includes a management console for health/config, skill scenes, AI configuration, datasource mapping, quick search management, accounts, followup rules, tags, analytics, desktop versions, notices, audit logs, and health dashboard.
- Current limitation: the first admin console is a production-connected operations surface with structured controls for simple action fields plus raw JSON for complex payloads; it is not yet a fully bespoke polished admin product UI for every module workflow.

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
- Expanded negative branch matrix and response-state assertions now pass with 158 total calls and explicit coverage counts for invalid, permission, conflict, read, create, update, download, and representative branches.
- Remaining work:
  - continue extending the invalid/permission/conflict matrix beyond the current production-critical branch set
  - deepen response body assertions for remaining list/detail/statistics/export fields that are still checked structurally rather than field-by-field
  - live-provider branch replay once real Skill/image/WeCom credentials are available

### P1 - Database / Repository Alignment Not Fully Audited

- Flyway migrations apply successfully to MariaDB.
- Added `scripts/verify_database_alignment.py`, which reads the live smoke database `information_schema`.
- Latest result: 31 tables found, 14 key table column sets checked, 30 migration-declared tables checked, 0 missing required columns, 0 missing migration tables, 0 missing config keys, 0 missing repository tables, 0 nullable/default violations, 0 enum violations.
  - Latest expanded repository SQL result: 0 repository column violations across scanned INSERT, UPDATE, and alias-qualified column references.
- Cross-layer enum contract verifier now passes:
  - `python scripts\verify_enum_contract_alignment.py`
  - Latest result: 40 checked contracts, 0 mismatches, with intentional exceptions recorded in `.tools/contracts/enum_contract_alignment.json`.
- Remaining:
  - expand column-level checks to unqualified SELECT lists and dynamically composed query fragments that are not yet safely parseable by the static verifier
  - expand nullable/default checks beyond the current production-critical subset
  - broaden frontend enum exposure from JSON action examples into dedicated controls where product UX requires it

### P1 - Frontend Runtime Coverage Is Incomplete

- Desktop modules exist under `desktop/src/renderer/modules`.
- Vite page can be served and browser smoke has covered login plus representative admin sections.
- Renderer smoke now dynamically traverses every admin navigation section that exists in the DOM.
- Renderer smoke now validates read-panel refresh/rendering and action-form JSON inputs for every admin section that exists in the DOM.
- Desktop renderer now has a login flow and no longer requires manually editing `localStorage.desktop_config.accessToken`.
- Desktop package now has Vite dev, Electron dev/preview, and Electron smoke scripts.
- Desktop package now has a repeatable Windows x64 directory packaging verifier and package report.
- Desktop now has Vitest/jsdom coverage for offline manager branches: consecutive API network failures, non-network business errors, WS degraded/reconnected handling, debounced OS offline/recovery bridge events, and duplicate offline capability registration.
- Desktop now also has Vitest/jsdom coverage for quick-search store behavior: API refresh and cache write, content-type/order sorting, lead-type filtering and shortcut/title/content ranking, retry failure with cached data retained, image copy validation, text copy, and auto-close after copy.
- Desktop now also has Vitest/jsdom coverage for workbench store behavior: followup loading and normalization, dashboard metric aggregation, urgent followup ordering and limits, new-lead queue fallback, notice filtering/dismissal/expiry, stale/retry-only fetch failures, refresh triggers, followup reminder/new-lead merge dedupe, and workbench navigation event emission.
- Desktop now also has Vitest/jsdom coverage for save-to-table behavior: successful profile saves, same-customer concurrent save guard, permanent business-error mapping, transient retry and pending-save persistence, pending recovery with latest version, expired/malformed pending cleanup, and external table sync success/failure/no-source-row branches.
- Fixed save-to-table retry control so permanent `GIVE_UP` errors such as forbidden, invalid input, or missing customer stop immediately instead of entering the transient retry loop.
- Desktop now also has Vitest/jsdom coverage for followup-list behavior: API grouping into tabs, loaded-data stale handling, primary reminder selection and cross-tab row movement, reminder flash cleanup, new-lead upsert behavior, cross-tab selection, batch template event emission, customer navigation, and new-reminder banner tab switching.
- Desktop now also has Vitest/jsdom coverage for customer-profile behavior: search truncation and single-result open, cached profile fallback, alert refresh, online profile caching, candidate selection/dismissal, generate-reply event flow, edit-save table sync prompts, conflict and pending-save UX, field/stage suggestion resolution, websocket suggestion merging, abnormal alert updates, stage updates, and send-confirm refresh.
- Desktop now also has Vitest/jsdom coverage for reply-suggestion behavior: recognize skeleton stages, multiple-match pause, timeout/image-failure stops, recognize result rendering, abnormal alert refresh, reply-selected masking, fallback-mode automatic recovery and retry exhaustion, manual regenerate history/help hints, missing-customer/login-expired failure UX, leader-help request lifecycle, profile suggestion batch resolution, and abnormal alert acknowledgement handling.
- Desktop now also has Vitest/jsdom coverage for chat-recognition behavior: exact and multiple-match event routing, one-second duplicate recognition suppression, concurrent recognition guard with manual screenshot override, image-service DOWN text-mode fallback, clipboard-image ignore rules, image-failure fallback events, and network timeout events.
- Fixed chat-recognition concurrency control so the pending guard is set before async content hashing; rapid duplicate user actions can no longer pass through the guard before the first request reaches the API client.
- Desktop now also has Vitest/jsdom coverage for copy-backfill behavior: empty reply rejection, clipboard success/failure branches, silent send-confirm dispatch, fallback direction mapping, aborting stale send-confirm requests, suggestion toast show/reopen/close/auto-collapse, single and batch suggestion resolution, completion hiding, and resolve-failure recovery.
- Desktop now also has Vitest/jsdom coverage for help-mode behavior: request dialog validation, help request submission with keeper-note truncation, pending and timeout events, permission/network failure UX, helper queue upsert/offline replay, draft reply limits and editing, helper resolve success/failure, received helper response state, reply-selected emission for helper replies, and response expand/close controls.
- Desktop now also has Vitest/jsdom coverage for batch-template behavior: unique phone handling and max-size guard, template API loading and cache fallback, bulk customer loading and one-by-one fallback, visible template filters, auto-selection, unavailable-customer skipping, template variable filling, clipboard copy success/failure, local logs, send-confirm dispatch, navigation, pause/resume, completion, and exit reset.
- Desktop now also has Vitest/jsdom coverage for stage-suggestion behavior: current-profile stage suggestion emission and dedupe, non-current customer pending/flush behavior, pending TTL expiry, confirm-stage success event emission, conflict refresh handling, transient retry exhaustion, and ignore-stage batch resolve with non-blocking backend failure.
- Desktop now also has Vitest/jsdom coverage for new-lead-toast behavior: invalid/reconnect payload suppression, visible and pending queue limits, auto-dismiss and pending promotion, normalized phone copy success/failure, opening-generation customer selection event, followup tab switch event, pending clear, and cleanup timer cancellation.
- Desktop now also has Vitest/jsdom coverage for abnormal-alert behavior: inbound payload validation, alert id/message normalization, memory sorting and acknowledged filtering, persisted alert/history load success and failure paths, acknowledgement persistence and events, panel/history controls, router initialization idempotency, periodic cleanup, event-bus ingestion, and database cleanup.
- Remaining:
  - exhaustive browser click coverage for every desktop/admin workflow and failure branch
  - production certificate-backed code signing and installer/notarization verification
  - expand component-level rendering tests and browser click coverage beyond the current store/service coverage and smoke-tested primary paths

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
