# Admin Demo V1 Style Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle every desktop management-console section to match Demo V1 while preserving all existing behavior and backend contracts.

**Architecture:** Keep `AdminConsole.vue` as the business and rendering owner. Add a small presentation-only navigation component and apply a shared token-based visual system through existing class names. API calls, reactive state, role filtering, forms, and dialogs remain unchanged.

**Tech Stack:** Vue 3, TypeScript, Vite, Vitest, scoped CSS.

---

## File Structure

- `desktop/src/renderer/modules/admin/AdminConsole.vue`: retain data and action logic, switch the shell to reusable navigation, and replace management-console CSS with Demo V1 tokens and responsive layout.
- `desktop/src/renderer/modules/admin/AdminNavigation.vue`: render the same group/page links with compact sidebar markup.
- `desktop/src/renderer/modules/admin/AdminNavigationComponent.test.ts`: verify navigation emits unchanged section keys.
- `desktop/src/renderer/modules/admin/AdminConsole.test.ts`: verify navigation, tag-only isolation, dialogs, exports, uploads, and destructive-action behavior survive the restyle.

### Task 1: Lock Navigation And Permission Behavior

**Files:**

- Modify: `desktop/src/renderer/modules/admin/AdminNavigation.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminNavigationComponent.test.ts`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`

- [ ] **Step 1: Add a failing navigation test for Demo V1 class hooks.**

```ts
expect(host.querySelector('.ops-admin-nav-group')).not.toBeNull();
expect(host.querySelector('.ops-admin-subnav-button.active')).not.toBeNull();
```

- [ ] **Step 2: Run `npm test -- AdminNavigationComponent.test.ts`; expect a failure until the class hooks exist.**

- [ ] **Step 3: Render grouped navigation with existing selection emits.**

```vue
<button class="ops-admin-subnav-button" :class="{ active: activeSectionKey === section.key }" type="button" @click="$emit('select', section.key)">
  <span class="ops-admin-subnav-module">{{ section.module }}</span>
  <small>{{ section.title }}</small>
</button>
```

- [ ] **Step 4: Replace the inline sidebar iteration with `AdminNavigation` while preserving `selectSection`.**

```vue
<AdminNavigation :groups="navGroups" :active-section="activeSection" :active-section-key="activeSectionKey" @select="selectSection" />
```

- [ ] **Step 5: Run `npm test -- AdminNavigationComponent.test.ts AdminConsole.test.ts`; expect all navigation and tag-only endpoint assertions to pass.**

- [ ] **Step 6: Commit only the navigation files with message `feat: apply demo navigation shell`.**

### Task 2: Apply The Demo V1 Visual System

**Files:**

- Modify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Modify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Add a failing shell-structure assertion.**

```ts
expect(host.querySelector('.ops-admin-shell')).not.toBeNull();
expect(host.querySelector('.ops-admin-toolbar')).not.toBeNull();
expect(host.querySelectorAll('.ops-metric-card')).not.toHaveLength(0);
```

- [ ] **Step 2: Run `npm test -- AdminConsole.test.ts`; expect a failure if the shell markup is absent.**

- [ ] **Step 3: Add Demo V1 visual tokens to the existing shell.**

```css
.ops-admin-shell {
  --admin-canvas: #f7f4ed;
  --admin-paper: #fffdfa;
  --admin-ink: #30271f;
  --admin-muted: #8f8275;
  --admin-line: #eee3d5;
  --admin-primary: #d77d37;
  display: grid;
  grid-template-columns: 236px minmax(0, 1fr);
  min-height: 100vh;
  color: var(--admin-ink);
  background: var(--admin-canvas);
}
```

- [ ] **Step 4: Restyle shared panels, tables, primary controls, forms, notices, drawers, badges, and pagination through their existing class names.**

```css
.ops-panel { border: 1px solid var(--admin-line); background: var(--admin-paper); border-radius: 8px; }
.ops-table-row { border-top: 1px solid var(--admin-line); }
.primary { color: #fff; background: var(--admin-primary); border-color: var(--admin-primary); }
.ops-drawer { background: var(--admin-paper); }
```

- [ ] **Step 5: Add narrow-width rules that retain access to navigation and actions.**

```css
@media (max-width: 760px) {
  .ops-admin-shell { grid-template-columns: 62px minmax(0, 1fr); }
  .ops-admin-main { padding-inline: 14px; }
  .ops-admin-dashboard { grid-template-columns: 1fr; }
  .ops-admin-toolbar-actions { flex-wrap: wrap; }
}
```

- [ ] **Step 6: Run `npm test -- AdminConsole.test.ts`; expect configuration, data, tags, analytics, audit, health, export, upload, confirmation, and failure-state checks to pass.**

- [ ] **Step 7: Commit the visual change with message `feat: restyle admin console with demo v1`.**

### Task 3: Verify Build And Visible Admin Behavior

**Files:**

- Verify: `desktop/src/renderer/modules/admin/AdminConsole.vue`
- Verify: `desktop/src/renderer/modules/admin/AdminConsole.test.ts`

- [ ] **Step 1: Run `npm test`; expect no desktop test failures.**
- [ ] **Step 2: Run `npm run typecheck`; expect exit code 0.**
- [ ] **Step 3: Run `npm run build`; expect exit code 0.**
- [ ] **Step 4: Start `npm run dev` and inspect `http://127.0.0.1:5173/#/admin`; expect Demo V1 shell and real controls.**
- [ ] **Step 5: Check navigation, an add/edit drawer, a destructive confirmation, tag-only mode, and horizontal table scrolling.**
- [ ] **Step 6: Run `git diff --check`; expect no whitespace errors.**
