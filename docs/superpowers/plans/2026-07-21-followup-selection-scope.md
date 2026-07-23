# Followup Selection Scope Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make followup selections belong only to the visible category so switching categories cannot send templates to hidden customers.

**Architecture:** Keep `selectedPhones` as the checkbox identity store, but derive selected batch recipients only from `activeFollowupItems`. Route every category change through `setActiveFollowupTab`, which clears the selection only when the target category differs from the current category. This covers direct tab clicks, workbench events, and the new-reminder banner without changing the downstream `batch:start` contract.

**Tech Stack:** Vue 3 reactive store, TypeScript, Vitest, jsdom.

---

## File Structure

- Modify `desktop/src/renderer/modules/followup-list/followupListStore.test.ts`: store-level regression tests for category switching, same-category behavior, and batch recipients.
- Modify `desktop/src/renderer/modules/followup-list/FollowupListPanel.test.ts`: rendered regression test proving the batch bar disappears after a user changes category.
- Modify `desktop/src/renderer/modules/followup-list/followupListStore.ts`: enforce the active-category selection invariant and route reminder navigation through the common switch function.
- No change to `desktop/src/renderer/modules/batch-template/*`: its existing `batch:start { phones, source }` input contract remains unchanged.

### Task 1: Add Failing Store Regressions

**Files:**
- Modify: `desktop/src/renderer/modules/followup-list/followupListStore.test.ts`
- Test: `desktop/src/renderer/modules/followup-list/followupListStore.test.ts`

- [x] **Step 1: Replace the cross-tab selection expectation with current-category behavior**

Add tests equivalent to:

```ts
it('clears selected customers when switching to a different followup tab', async () => {
  const { followups, eventBus } = await freshStore();
  const batchEvents: unknown[] = [];
  eventBus.on('batch:start', (payload) => batchEvents.push(payload));
  followups.followupListState.groups.OVERDUE = [
    item({ phone: 'masked-1', phoneFull: '18800000001', reminderType: 'OVERDUE' }),
    item({ phone: 'masked-2', phoneFull: '18800000002', reminderType: 'OVERDUE' })
  ];
  followups.followupListState.groups.DUE_TODAY = [
    item({ phone: 'today', phoneFull: '18800000003', reminderType: 'DUE_TODAY' })
  ];

  followups.setActiveFollowupTab('OVERDUE');
  followups.selectAllActiveFollowups();
  expect(followups.selectedFollowupItems.value).toHaveLength(2);

  followups.setActiveFollowupTab('DUE_TODAY');
  followups.startBatchTemplate();

  expect(followups.followupListState.selectedPhones.size).toBe(0);
  expect(followups.selectedFollowupItems.value).toHaveLength(0);
  expect(batchEvents).toEqual([]);
});

it('keeps current selections when the active tab is selected again', async () => {
  const { followups } = await freshStore();
  followups.followupListState.groups.DUE_TODAY = [item({ phone: 'today' })];

  followups.toggleFollowupSelection(followups.followupListState.groups.DUE_TODAY[0]);
  followups.setActiveFollowupTab('DUE_TODAY');

  expect(followups.selectedFollowupItems.value.map((entry) => entry.phone)).toEqual(['today']);
});
```

- [x] **Step 2: Add a reminder-banner caller regression**

```ts
followups.followupListState.activeTab = 'OVERDUE';
followups.followupListState.selectedPhones.add('18800000001');
followups.followupListState.newReminderTab = 'APPOINTMENT';
followups.openNewReminderBanner();
expect(followups.followupListState.activeTab).toBe('APPOINTMENT');
expect(followups.followupListState.selectedPhones.size).toBe(0);
```

- [x] **Step 3: Run the store tests and verify RED**

Run:

```powershell
cd desktop
npm test -- src/renderer/modules/followup-list/followupListStore.test.ts
```

Expected: FAIL because `setActiveFollowupTab` and `openNewReminderBanner` do not clear `selectedPhones`.

### Task 2: Add Failing Rendered Regression

**Files:**
- Modify: `desktop/src/renderer/modules/followup-list/FollowupListPanel.test.ts`
- Test: `desktop/src/renderer/modules/followup-list/FollowupListPanel.test.ts`

- [x] **Step 1: Add the user-visible reproduction**

```ts
it('hides the batch bar after switching away from the selected customer category', async () => {
  const { app, host } = await mountPanel();
  const tabs = [...host.querySelectorAll('.tab-button')] as HTMLButtonElement[];

  tabs[1].click();
  await flushUi();
  const checkbox = host.querySelector('.followup-row input[type="checkbox"]') as HTMLInputElement;
  checkbox.checked = true;
  checkbox.dispatchEvent(new Event('change', { bubbles: true }));
  await flushUi();
  expect(host.querySelector('.batch-selection-count')?.textContent).toContain('已选 1 个');

  tabs[0].click();
  await flushUi();
  expect(host.querySelector('.batch-bar')).toBeNull();

  app.unmount();
});
```

- [x] **Step 2: Run the component test and verify RED**

Run:

```powershell
cd desktop
npm test -- src/renderer/modules/followup-list/FollowupListPanel.test.ts
```

Expected: FAIL because the batch bar still shows the hidden overdue selection after switching to today.

### Task 3: Enforce Current-Category Selection

**Files:**
- Modify: `desktop/src/renderer/modules/followup-list/followupListStore.ts`
- Test: `desktop/src/renderer/modules/followup-list/followupListStore.test.ts`
- Test: `desktop/src/renderer/modules/followup-list/FollowupListPanel.test.ts`

- [x] **Step 1: Restrict derived recipients to the active category**

Replace the all-tab aggregation with:

```ts
export const selectedFollowupItems = computed(() =>
  activeFollowupItems.value.filter((item) => followupListState.selectedPhones.has(item.phoneFull ?? item.phone))
);
```

- [x] **Step 2: Clear only when the category actually changes**

```ts
export function setActiveFollowupTab(tab: FollowupTab): void {
  if (followupListState.activeTab === tab) {
    return;
  }
  followupListState.selectedPhones.clear();
  followupListState.activeTab = tab;
}
```

- [x] **Step 3: Route the reminder banner through the same switch function**

```ts
export function openNewReminderBanner(): void {
  setActiveFollowupTab(followupListState.newReminderTab);
  followupListState.newReminderCount = 0;
}
```

- [x] **Step 4: Run focused tests and verify GREEN**

Run:

```powershell
cd desktop
npm test -- src/renderer/modules/followup-list/followupListStore.test.ts src/renderer/modules/followup-list/FollowupListPanel.test.ts
```

Expected: both files pass, including current-tab selection, all/select-invert, profile navigation, workbench tab events, and batch event payloads.

### Task 4: Verify Callers and Downstream Batch Flow

**Files:**
- Verify: `desktop/src/renderer/App.test.ts`
- Verify: `desktop/src/renderer/modules/workbench/WorkbenchPanel.test.ts`
- Verify: `desktop/src/renderer/modules/batch-template/batchTemplateStore.test.ts`
- Verify: `desktop/src/renderer/modules/batch-template/BatchTemplateOverlay.test.ts`

- [x] **Step 1: Run adjacent caller and callee tests**

```powershell
cd desktop
npm test -- src/renderer/App.test.ts src/renderer/modules/workbench/WorkbenchPanel.test.ts src/renderer/modules/batch-template/batchTemplateStore.test.ts src/renderer/modules/batch-template/BatchTemplateOverlay.test.ts
```

Expected: workbench tab routing still opens the requested category, and the batch module still receives and processes the unchanged phone array contract.

- [x] **Step 2: Run the full desktop test suite**

```powershell
cd desktop
npm test
```

Expected: all desktop Vitest tests pass.

- [x] **Step 3: Run type checking**

```powershell
cd desktop
npm run typecheck
```

Expected: exit code 0 with no TypeScript errors.

- [x] **Step 4: Run renderer smoke verification**

```powershell
cd desktop
npm run renderer:smoke
```

Expected: build and renderer smoke complete successfully.

## Repository Handling

Do not commit automatically. The current branch contains substantial pre-existing user changes; stage or commit only when the user explicitly requests it.
