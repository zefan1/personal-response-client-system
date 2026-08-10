# Admin Console Operations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 改造管理后台九处运营界面，使操作路径清晰、分组和发布范围真实可见、规则与漏斗响应式可用。

**Architecture:** 保留 `AdminConsole.vue` 作为当前管理端页面入口，优先复用现有 API 客户端、标签/阶段选项和表格样式。前端交互先以失败测试锁定；后端只补模板范围、业务组和三表连接所需的最小数据契约，旧数据按兼容默认值渲染。

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, Vitest, Spring Boot/JDBC admin API, CSS Grid/Flexbox。

---

### Task 1: Capture existing contracts and add failing UI tests

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Test target: existing `mountConsole`, mock API response map and `findSubnavButton` helpers

- [ ] **Step 1: Add failing tests for the nine behaviors**
  - Assert the test panel exposes an explicit capability selector and run action.
  - Assert Smart Sheet renders three named connection cards and their status/mapping text.
  - Click a customer row body and assert the profile detail opens; click again and assert it closes.
  - Assert candidate template uses a `select` for lead type and a publish-scope control with “全部成员/指定组”.
  - Assert accounts render `.ops-account-group` sections with group leader before members and an “未分组” section.
  - Assert the rule form uses the short search class and no fixed-width modal overflow class.
  - Assert funnel stages have Chinese labels and a non-rectangular clip-path class.
- [ ] **Step 2: Run the focused tests and confirm RED**
  - Run `npm test -- --run desktop/src/renderer/modules/admin/AdminConsole.test.ts` from `desktop`.
  - Expected: new assertions fail because the current DOM lacks the selectors/behavior; existing assertions remain diagnosable.

### Task 2: Implement test flow, customer row expansion, and responsive layout

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Test: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Implement explicit test selection state**
  - Add a selected binding ref and a visible `<select>`/capability list beside the test textarea.
  - Keep the existing row “测试” action as a shortcut that sets the selected binding.
  - Disable run until a binding and non-empty message exist; keep result/error states.
- [ ] **Step 2: Make customer rows toggle details**
  - Add a row click handler that calls `toggleAdminCustomerDetail(customer)` and ignores clicks from `.ops-row-actions`.
  - Preserve the button label and add keyboard Enter/Space handling with `tabindex="0"`.
- [ ] **Step 3: Replace fixed modal/layout widths**
  - Use `minmax(0, 1fr)` grid tracks, `max-width: min(720px, calc(100vw - 32px))`, and mobile single-column rules.
  - Ensure body overflow-x is hidden only at the admin surface root, not globally.
- [ ] **Step 4: Run the focused tests and confirm GREEN for Task 2 behaviors**
  - Run `npm test -- --run desktop/src/renderer/modules/admin/AdminConsole.test.ts`.

### Task 3: Make Smart Sheet connections understandable

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Inspect/modify only if required by API contract: `src/main/java/com/privateflow/modules/api/admin/*`

- [ ] **Step 1: Add failing API contract assertions**
  - Mock a three-item connection response with `role`, `documentName`, `sheetName`, `viewName`, `status`, and `mappingCount`.
  - Assert the save action sends the selected connection role and URL, not a single ambiguous URL.
- [ ] **Step 2: Implement three connection cards**
  - Render roles 客户主表、分配表、到店表 with status badges, names and mapping counts.
  - Keep relay/direct mode in a separate “访问方式” panel and put advanced deployment fields behind disclosure.
  - Add per-card configure/check actions and show actionable failure text.
- [ ] **Step 3: Add the smallest compatible admin response/save DTO if missing**
  - Read the existing controller/service contract first; preserve existing `documentUrl` behavior for legacy responses.
  - Map absent role data to the primary table card and mark the other two as “未配置”.
- [ ] **Step 4: Run UI and targeted backend admin tests**
  - Run Vitest focused file and the matching Maven admin controller/service test class.

### Task 4: Upgrade promotion candidates and account groups

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`
- Modify/add backend files under `src/main/java/com/privateflow/modules/api/admin/` and migration files only where current schema has no group/range fields

- [ ] **Step 1: Add RED tests for group and scope contracts**
  - Mock grouped accounts (`groupName`, `leader`, `members`) and candidate scope options.
  - Assert group sections, leader-first ordering, and scope payload values.
- [ ] **Step 2: Render grouped account list**
  - Group the existing account rows by `groupId/groupName`, render a group header, leader row, member rows, and ungrouped fallback.
  - Add group and role filters without removing search/status filters.
- [ ] **Step 3: Replace candidate cards with a filterable list**
  - Add search, lead-type select from `LEAD_TYPE_OPTIONS`, status/scope filters, pagination-safe empty states, and row actions.
  - Rename the editable field to 模板标题; add scope select/group multiselect and serialize the chosen scope.
- [ ] **Step 4: Add backend persistence only for missing fields**
  - Extend promotion DTO/service with `scopeType` and `groupIds`; default null/legacy records to ALL.
  - Extend account response with group display data; enforce one leader per group and one group per member at service validation.
- [ ] **Step 5: Run RED-GREEN tests and targeted persistence tests**
  - Run focused Vitest, then the affected Maven admin/promotion/account test classes.

### Task 5: Recompose follow-up rules and funnel analytics

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Add failing tests**
  - Assert the rule search input uses the compact layout token and the form exposes distinct condition/meta/action sections.
  - Assert funnel stages render Chinese labels, counts/rates, and the funnel step class.
- [ ] **Step 2: Implement rule list/form layout**
  - Shorten the search field to half-width on desktop; keep filters aligned and stack at narrow widths.
  - Use responsive sections for tag conditions, rule name, lead type, overdue threshold, action, reminder level/type, and suggested tag.
  - Replace English/internal values through the existing translation helpers.
- [ ] **Step 3: Implement funnel visual**
  - Keep the analytics data contract and set each step width from count relative to the first stage.
  - Render trapezoid steps with CSS `clip-path`, min/max widths, and a mobile width of 100% with centered labels.
- [ ] **Step 4: Run focused tests**
  - Run AdminConsole Vitest and typecheck; fix only failures attributable to this task.

### Task 6: Responsive and visual verification

**Files:**
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`

- [ ] **Step 1: Run the Impeccable detector once**
  - Run `node C:\Users\85314\.agents\skills\impeccable\scripts\detect.mjs --json desktop/src/renderer/modules/admin/AdminConsole.vue`.
  - Resolve unexplained overflow, fixed-width, untranslated-English, and touch-target findings.
- [ ] **Step 2: Run the full frontend verification**
  - Run `npm test -- --run` and `npm run typecheck` from `desktop`.
  - Record exact pass/fail counts; do not claim completion if either command fails.
- [ ] **Step 3: Start the existing frontend dev server and inspect the admin route**
  - Use the project's existing dev command, choose another port if occupied, and verify `/#/admin` at desktop and 390px widths.
  - Check no horizontal page scrollbar, rows expand/collapse, grouped accounts are readable, and funnel labels remain Chinese.
