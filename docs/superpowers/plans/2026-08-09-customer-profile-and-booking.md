# Customer Profile And Booking Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Connect the customer workbench profile to live data and add the simplest chat-based booking-to-arrival flow.

**Architecture:** Add three booking fields to `customers`; keep the original source row untouched. A small booking service stores a pending booking and, only after the existing AI suggestion is approved, creates one linked arrival-table row. The existing profile panel gets real booking controls; the existing admin detail stays read-only.

**Tech Stack:** Spring Boot, JdbcTemplate, Flyway, MariaDB, Vue 3, TypeScript, JUnit 5, Vitest.

---

### Task 1: Store Current Booking State

**Files:**
- Create: `src/main/resources/db/migration/V93__customer_booking_fields.sql`
- Modify: `src/main/java/com/privateflow/modules/customer/Customer.java`
- Modify: `src/main/java/com/privateflow/modules/customer/infra/CustomerRowMapper.java`
- Modify: `src/main/java/com/privateflow/modules/profile/infra/ProfileFieldRegistry.java`
- Modify: `desktop/src/renderer/modules/customer-profile/types.ts`
- Test: `src/test/java/com/privateflow/modules/profile/infra/ProfileFieldRegistryTest.java`

- [ ] Write a failing registry test for `appointmentStatus`, `appointmentTime`, and `arrivalSourceRowId`.
- [ ] Add the three nullable columns and map them through the existing customer model.
- [ ] Run `wsl bash -lc "cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 && ./mvnw -Dtest=ProfileFieldRegistryTest test"`.
- [ ] Commit with `git commit -m "feat: store customer booking state"`.

### Task 2: Confirm Booking And Create Arrival Row Once

**Files:**
- Create: `src/main/java/com/privateflow/modules/customer/booking/BookingConfirmRequest.java`
- Create: `src/main/java/com/privateflow/modules/customer/booking/BookingConfirmResult.java`
- Create: `src/main/java/com/privateflow/modules/customer/booking/CustomerBookingService.java`
- Modify: `src/main/java/com/privateflow/modules/match/web/CustomerController.java`
- Modify: `src/main/java/com/privateflow/modules/profile/service/SuggestionQueueManager.java`
- Test: `src/test/java/com/privateflow/modules/customer/booking/CustomerBookingServiceTest.java`

- [ ] Write failing tests: confirmation writes `待确认`; suggestion approval creates one arrival row; a repeated approval reuses the stored row ID.
- [ ] Add `POST /api/v1/customers/{phone}/booking`, accepting date, time, store, and project; return a copyable chat template.
- [ ] Save `待确认` first. After existing profile-suggestion confirmation, change to `已预约` and create the row in `新客到店衔接表（辅助）` only when the customer name exists and `arrivalSourceRowId` is blank.
- [ ] Reuse the current pending table-write queue on external failure. Do not overwrite `sourceTable` or `sourceRowId`, and do not match customers by name or nickname.
- [ ] Run `wsl bash -lc "cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 && ./mvnw -Dtest=CustomerBookingServiceTest,SuggestionQueueManagerTest test"`.
- [ ] Commit with `git commit -m "feat: add booking confirmation flow"`.

### Task 3: Render The Real Workbench Profile

**Files:**
- Modify: `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.vue`
- Modify: `desktop/src/renderer/modules/customer-profile/customerProfileStore.ts`
- Modify: `desktop/src/renderer/modules/customer-profile/types.ts`
- Test: `desktop/src/renderer/modules/customer-profile/CustomerProfilePanel.test.ts`

- [ ] Write a failing component test for the `确认预约并生成填写信息` control and returned appointment template.
- [ ] Keep the confirmed overview hierarchy, place stage beside the name, and show live data in every section. Rename AI confirmation to `同意并执行`.
- [ ] Add a small booking dialog containing only date, time, store, project, and a copyable generated template. Use `postJson` to call the booking endpoint; do not add hard-coded customer content.
- [ ] Run `npm run test -- CustomerProfilePanel.test.ts` and `npm run typecheck` in `desktop`.
- [ ] Commit with `git commit -m "feat: add booking action to customer profile"`.

### Task 4: Extend Existing Read-Only Admin Detail

**Files:**
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminListItem.java`
- Modify: `src/main/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepository.java`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Test: `src/test/java/com/privateflow/modules/customer/admin/CustomerAdminSearchRepositoryTest.java`
- Test: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] Write failing tests for booking status and time in an existing admin search result.
- [ ] Extend the existing expandable `查看档案` block with the same current booking and arrival details. Do not add an AI action, editor, or a new navigation entry.
- [ ] Run `wsl bash -lc "cd /mnt/c/Users/85314/Desktop/私域辅助系统/私域辅助系统 && ./mvnw -Dtest=CustomerAdminSearchRepositoryTest test"` and `npm run test -- AdminConsole.test.ts`.
- [ ] Commit with `git commit -m "feat: show booking details in admin customers"`.

### Task 5: Verify And Push

**Files:**
- Modify: none

- [ ] Run focused backend tests, frontend tests, `npm run typecheck`, and `npm run build`.
- [ ] Start the local stack and inspect the profile and existing admin detail against the confirmed layout.
- [ ] Push the completed commits with `git push origin chore/worktree-isolation`.
