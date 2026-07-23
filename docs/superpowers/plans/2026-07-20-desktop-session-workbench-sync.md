# Desktop Session And Workbench Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复桌面端频繁重新登录和工作台缺少周期同步的问题，并收敛重复入口与 Skill 未配置提示。

**Architecture:** 使用数据库持久化、按登录会话隔离且旋转的 refresh token 替代单用户名 Redis token。工作台保留 WebSocket 实时事件，并增加后台可配置的轮询兜底；正式管理后台通过现有系统配置 API 管理登录和桌面参数。

**Tech Stack:** Spring Boot 3、JdbcTemplate、Flyway、Vue 3、TypeScript、Vitest、Electron。

---

### Task 1: Persistent Multi-Session Refresh Tokens

**Files:**
- Create: `src/main/resources/db/migration/V74__persistent_refresh_sessions_and_desktop_sync.sql`
- Create: `src/test/java/com/privateflow/modules/api/auth/RefreshTokenStoreTest.java`
- Modify: `src/main/java/com/privateflow/modules/api/auth/RefreshTokenStore.java`
- Modify: `src/test/java/com/privateflow/modules/api/auth/AuthServiceTest.java`
- Modify: `src/main/java/com/privateflow/modules/api/auth/AuthService.java`

- [ ] Write an H2-backed test proving two tokens for one username remain valid independently, rotation invalidates the old token, expiry is rejected, and account-wide revoke invalidates all sessions.
- [ ] Run `mvn -Dtest=RefreshTokenStoreTest test` and confirm the test fails because the current store only supports one Redis value per username.
- [ ] Add the refresh-session table and default config updates in V74.
- [ ] Replace the Redis implementation with a JdbcTemplate implementation that hashes tokens, rotates them transactionally, and revokes per-token or per-user.
- [ ] Update AuthService refresh tests to expect rotation and a new refresh token.
- [ ] Run `mvn -Dtest=RefreshTokenStoreTest,AuthServiceTest test` and confirm all pass.

### Task 2: Logout And Desktop Refresh Handling

**Files:**
- Modify: `src/main/java/com/privateflow/modules/api/web/AuthController.java`
- Modify: `src/main/java/com/privateflow/modules/api/auth/JwtAuthenticationFilter.java`
- Modify: `src/test/java/com/privateflow/modules/api/web/AuthControllerTest.java`
- Modify: `desktop/src/renderer/App.vue`
- Modify: `desktop/src/renderer/App.test.ts`

- [ ] Write backend and frontend tests proving logout revokes only the presented refresh session and the client clears local state even if logout fails.
- [ ] Run the targeted tests and confirm they fail because no logout endpoint/call exists.
- [ ] Add public `POST /api/v1/auth/logout` accepting username and refresh token.
- [ ] Make desktop logout call the endpoint best-effort before clearing the session.
- [ ] Run targeted backend and frontend tests and confirm they pass.

### Task 3: Admin-Managed Runtime Settings

**Files:**
- Modify: `src/main/java/com/privateflow/modules/desktop/DesktopRuntimeConfigResponse.java`
- Modify: `src/main/java/com/privateflow/modules/desktop/DesktopStatusService.java`
- Modify: `src/test/java/com/privateflow/modules/desktop/DesktopStatusServiceTest.java`
- Modify: `desktop/src/renderer/shared/desktopStatusStore.ts`
- Modify: `desktop/src/renderer/shared/desktopStatusStore.test.ts`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] Write tests for `desktop.workbench_refresh_interval_s`, Skill expiry refresh, and the four-field admin form.
- [ ] Run targeted tests and confirm they fail because these fields are not exposed.
- [ ] Extend desktop status runtime config with the server-backed refresh interval.
- [ ] Add the “登录与桌面设置” panel to account permissions and save through the existing config API.
- [ ] Refresh desktop status when `desktop.workbench_refresh_interval_s` or `skill.subscription_expire_at` changes.
- [ ] Run targeted tests and confirm they pass.

### Task 4: Workbench Automatic Sync And UI Cleanup

**Files:**
- Modify: `desktop/src/renderer/modules/workbench/WorkbenchPanel.vue`
- Modify: `desktop/src/renderer/modules/workbench/WorkbenchPanel.test.ts`
- Modify: `desktop/src/renderer/modules/workbench/workbenchStore.test.ts`
- Modify: `desktop/src/renderer/App.vue`
- Modify: `desktop/src/renderer/App.test.ts`
- Modify: `desktop/src/renderer/styles.css`

- [ ] Write failing tests proving the workbench periodically invokes guarded refresh, removes the duplicate action block, and hides unknown Skill expiry state.
- [ ] Run targeted Vitest files and confirm the new tests fail for the expected missing behavior.
- [ ] Add the periodic refresh timer with cleanup and existing retry-only protection.
- [ ] Remove the duplicate workbench action block and unused styles/imports.
- [ ] Hide the Skill status element when the compact label is empty.
- [ ] Run targeted Vitest files and confirm they pass.

### Task 5: Full Verification And Local Package

**Files:**
- Modify if needed: `dev-progress/manual-tests/32_workbench_complete_test.md`

- [ ] Run `mvn test`.
- [ ] Run `npm run typecheck` in `desktop`.
- [ ] Run `npm run test` in `desktop`.
- [ ] Run `npm run renderer:smoke` in `desktop`.
- [ ] Run `npm run package:verify` in `desktop`.
- [ ] Run `git diff --check`.
- [ ] Restart the local backend if required, launch the newly packaged desktop app, and provide the exact manual regression steps.

### Task 6: Today-First Follow-up Priority And Stable Fixtures

**Files:**
- Create: `src/test/java/com/privateflow/modules/followup/service/FollowupTodayServiceTest.java`
- Modify: `src/main/java/com/privateflow/modules/followup/service/FollowupTodayService.java`
- Modify: `desktop/src/renderer/modules/followup-list/FollowupListPanel.test.ts`
- Modify: `desktop/src/renderer/modules/followup-list/FollowupListPanel.vue`
- Modify: `desktop/src/renderer/modules/followup-list/followupListStore.test.ts`
- Modify: `desktop/src/renderer/modules/followup-list/followupListStore.ts`
- Modify: `desktop/src/renderer/modules/workbench/workbenchStore.test.ts`
- Modify: `desktop/src/renderer/modules/workbench/workbenchStore.ts`
- Modify: `scripts/acceptance_sidebar_batch_a.py`
- Modify: `dev-progress/manual-tests/32_workbench_complete_test.md`

- [ ] Add a backend test proving a follow-up scheduled earlier today remains `DUE_TODAY`, while a previous-day follow-up is `OVERDUE`, and run it to confirm the current timestamp comparison fails.
- [ ] Change backend classification and sorting to use calendar-day boundaries and place `DUE_TODAY` before `OVERDUE`; rerun the backend test.
- [ ] Add frontend tests proving the tab order, initial tab, workbench navigation, and workbench row order are today-first; run them to confirm the current overdue-first behavior fails.
- [ ] Apply the minimal frontend changes in the follow-up list and workbench stores; rerun the targeted Vitest files.
- [ ] Extend the local acceptance fixture refresh to set `18800002222.nextFollowupAt` to the current day and assert that it is returned as `DUE_TODAY`.
- [ ] Update the manual checklist from the old overdue-first contract to the new today-first contract.
- [ ] Run targeted backend/frontend tests, `git diff --check`, restart the backend, refresh the live fixture, and verify the local API returns one today customer and one overdue customer in the expected order.

### Task 7: Task Queue Profile Navigation And Narrow Layout

**Files:**
- Modify: `desktop/src/renderer/App.test.ts`
- Modify: `desktop/src/renderer/App.vue`
- Modify: `desktop/src/renderer/modules/followup-list/FollowupListPanel.test.ts`
- Modify: `desktop/src/renderer/modules/followup-list/FollowupListPanel.vue`
- Modify: `desktop/src/renderer/modules/customer-profile/customerProfileStore.test.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/customerProfileStore.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- Modify: `desktop/src/renderer/styles.css`
- Modify: `desktop/src/main/main.ts`
- Modify: `dev-progress/manual-tests/32_workbench_complete_test.md`

- [ ] Add an App test proving `customer:selected` closes the task queue and leaves the customer profile panel active; run it to confirm the drawer currently remains visible.
- [ ] Add customer-profile store and component tests proving the visible copy control writes the exact nickname and reports success, empty-name, and failure states; run them to confirm the function/control is missing.
- [ ] Add follow-up panel assertions for the explicit profile action and semantic batch-bar classes; update renderer smoke to select a customer, verify all visible button contents fit at 420×760 and 360×560, and confirm opening a profile closes the drawer.
- [ ] Implement the minimal event handling, nickname clipboard action, visible profile button, and two-row batch layout.
- [ ] Run targeted Vitest files, typecheck, the full desktop suite, renderer smoke with screenshots, and inspect the 420px and 360px images for overlap or clipping.
- [ ] Update the manual checklist, rebuild the Windows package, restart the packaged app, and provide exact manual regression steps.
