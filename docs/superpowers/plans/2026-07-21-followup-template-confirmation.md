# Followup Template Confirmation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users return from a customer profile to the originating followup list and record a quick-search template only after they explicitly confirm it was sent.

**Architecture:** App owns a single-level profile return context and passes an explicit customer snapshot into Quick Search only when the customer profile is currently visible. Quick Search separates clipboard copy from send confirmation, then emits a completion event after `/api/v1/chat/send-confirm` succeeds. The backend records core followup fields synchronously before publishing existing asynchronous profile/table events, and clears the current due/overdue task only when the request explicitly marks it complete.

**Tech Stack:** Vue 3, TypeScript, Vitest/jsdom, Spring Boot, Java 17, JUnit 5, Mockito, JDBC, existing event bus and table-write queue.

---

## File Structure

- Modify `desktop/src/renderer/App.vue`: capture/restore profile return context, render the back button, and pass explicit Quick Search customer context.
- Modify `desktop/src/renderer/App.test.ts`: cover followup-to-profile-to-list navigation and customer-bound template opening.
- Modify `desktop/src/renderer/modules/quick-search/types.ts`: define explicit customer context and pending send-confirmation types.
- Modify `desktop/src/renderer/modules/quick-search/quickSearchStore.ts`: separate copy from confirmed send and remove implicit stale-profile reads.
- Modify `desktop/src/renderer/modules/quick-search/QuickSearchOverlay.vue`: render the associated customer and inline `已发送 / 未发送` actions.
- Modify `desktop/src/renderer/modules/quick-search/quickSearchStore.test.ts` and `QuickSearchOverlay.test.ts`: cover copy-only, confirm, decline, close, failure, and stale-context prevention.
- Modify `desktop/src/renderer/modules/followup-list/followupListStore.ts`, `FollowupListPanel.vue`, and their tests: remove a confirmed due/overdue customer from the visible queue.
- Modify `desktop/src/renderer/modules/workbench/workbenchStore.ts`, `WorkbenchPanel.vue`, and tests: mark workbench followup data dirty after confirmation.
- Modify `src/main/java/com/privateflow/modules/api/chat/SendConfirmRequest.java`: add the explicit `completeCurrentFollowup` flag.
- Modify `src/main/java/com/privateflow/common/events/CustomerMessageSentEvent.java`: propagate completion intent to table write.
- Create `src/main/java/com/privateflow/modules/profile/service/FollowupConfirmationService.java`: synchronously persist the confirmed followup core fields.
- Create `src/test/java/com/privateflow/modules/profile/service/FollowupConfirmationServiceTest.java`: verify ordinary record, task completion, and next-suggestion replacement.
- Modify `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java` and tests: call the synchronous service before publishing the existing event.
- Modify `src/main/java/com/privateflow/modules/profile/service/ProfileUpdateOrchestrator.java` and tests: keep asynchronous extraction/tag updates but stop duplicating core followup writes.
- Modify `src/main/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdater.java` and tests: send matching followup fields to the enterprise table or existing retry queue.

### Task 1: Restore the Originating Followup List

**Files:**
- Modify: `desktop/src/renderer/App.test.ts`
- Modify: `desktop/src/renderer/App.vue`

- [x] **Step 1: Write failing App navigation tests**

Extend the existing desktop interaction test to prove:

```ts
eventBus.emit('followup:switch-tab', { tab: 'DUE_TODAY' });
await flushUi();
eventBus.emit('customer:selected', {
  phone: '18800002222',
  scene: 'ACTIVE_REPLY',
  leadType: 'XIAN_SUO',
  reminderType: 'DUE_TODAY',
  sourceFrom: 'FOLLOWUP_LIST'
});
await flushUi();

expect(host.querySelector('.profile-return-button')).toBeTruthy();
expect((host.querySelector('.task-queue-backdrop') as HTMLElement).style.display).toBe('none');

(host.querySelector('.profile-return-button') as HTMLButtonElement).click();
await flushUi();

expect((host.querySelector('.task-queue-backdrop') as HTMLElement).style.display).not.toBe('none');
expect(host.querySelector('.tab-button.active')?.textContent).toContain('今日待跟进');
```

Add a second assertion that the customer profile's repeated loaded `customer:selected` event does not replace the saved return context.

- [x] **Step 2: Run the focused App test and verify RED**

```powershell
cd desktop
npm test -- src/renderer/App.test.ts
```

Expected: FAIL because `.profile-return-button` and the return context do not exist.

- [x] **Step 3: Implement a single-level return context**

Add:

```ts
type FollowupTab = 'OVERDUE' | 'DUE_TODAY' | 'APPOINTMENT' | 'NEW_LEAD';
type ProfileReturnContext = {
  panel: DesktopPanelKey;
  taskQueueOpen: boolean;
  followupTab?: FollowupTab;
  reminderType?: FollowupTab;
};

const profileReturnContext = ref<ProfileReturnContext | null>(null);
```

Handle `customer:selected` with its payload. Capture context only when entering customer view from `FOLLOWUP_LIST` or `DASHBOARD` while the current panel is not already `customer`. For followup sources, preserve the active tab supplied as `reminderType` or the current followup store tab.

Implement:

```ts
function returnFromCustomerProfile(): void {
  const context = profileReturnContext.value;
  if (!context) return;
  profileReturnContext.value = null;
  selectDesktopPanel(context.panel);
  taskQueueOpen.value = context.taskQueueOpen;
  if (context.taskQueueOpen && context.followupTab) {
    void nextTick(() => eventBus.emit('followup:switch-tab', { tab: context.followupTab }));
  }
}
```

Render a familiar arrow button in `.desktop-mode-tools` only when the customer panel has a return context:

```vue
<button
  v-if="activeDesktopPanel === 'customer' && profileReturnContext"
  class="profile-return-button"
  type="button"
  aria-label="返回待办"
  title="返回待办"
  @click="returnFromCustomerProfile"
>
  <span aria-hidden="true">←</span>
</button>
```

- [x] **Step 4: Include reminder type in followup customer navigation**

Change `openFollowupCustomer` to emit:

```ts
eventBus.emit('customer:selected', {
  phone: item.phoneFull ?? item.phone,
  scene: 'ACTIVE_REPLY',
  leadType: item.leadType ?? '',
  reminderType: item.reminderType,
  sourceFrom: 'FOLLOWUP_LIST'
});
```

Update the existing store/component expectations accordingly.

- [x] **Step 5: Run App and followup navigation tests and verify GREEN**

```powershell
cd desktop
npm test -- src/renderer/App.test.ts src/renderer/modules/followup-list/followupListStore.test.ts src/renderer/modules/followup-list/FollowupListPanel.test.ts
```

Expected: all focused tests pass.

### Task 2: Add Explicit Customer Context to Quick Search

**Files:**
- Modify: `desktop/src/renderer/modules/quick-search/types.ts`
- Modify: `desktop/src/renderer/modules/quick-search/quickSearchStore.test.ts`
- Modify: `desktop/src/renderer/modules/quick-search/quickSearchStore.ts`
- Modify: `desktop/src/renderer/App.vue`

- [ ] **Step 1: Define the context and pending confirmation types**

```ts
export type QuickSearchCustomerContext = {
  phone: string;
  nickname?: string | null;
  leadType?: string | null;
  sourceTable?: string | null;
  reminderType?: 'OVERDUE' | 'DUE_TODAY' | 'APPOINTMENT' | 'NEW_LEAD' | null;
  returnToFollowups: boolean;
  customer: Record<string, unknown>;
};

export type QuickSearchPendingSend = {
  itemId: number;
  title: string;
  scene: string;
  sentText: string;
};
```

Add `customerContext`, `pendingSend`, and `confirming` to `quickSearchState`.

- [ ] **Step 2: Write failing store tests for explicit context**

Cover these behaviors:

```ts
store.showQuickSearch({
  phone: '13800001111',
  nickname: '王女士',
  leadType: 'XIAN_SUO',
  sourceTable: '私域客资管理表',
  reminderType: 'DUE_TODAY',
  returnToFollowups: true,
  customer: { nickname: '王女士' }
});
await store.copyQuickSearchItem(template);
expect(store.quickSearchState.pendingSend).toMatchObject({ itemId: template.id });
expect(postJsonMock).not.toHaveBeenCalled();

store.declineQuickSearchSend();
expect(store.quickSearchState.pendingSend).toBeNull();
expect(store.quickSearchState.visible).toBe(true);
```

Also prove that `showQuickSearch()` without context performs ordinary copy and never reuses `customerProfileState.profile`.

- [ ] **Step 3: Run Quick Search store tests and verify RED**

```powershell
cd desktop
npm test -- src/renderer/modules/quick-search/quickSearchStore.test.ts
```

Expected: FAIL because the state and functions do not exist and the store still reads the profile store implicitly.

- [ ] **Step 4: Pass context only from a visible customer profile**

In `App.vue`, build the payload only when `activeDesktopPanel.value === 'customer'` and the current profile has a full phone:

```ts
function openQuickSearch(): void {
  const profile = customerProfileState.profile;
  const phone = String(profile?.phoneFull || profile?.customer.phoneFull || profile?.customer.phone || '');
  const context = activeDesktopPanel.value === 'customer' && phone
    ? {
        phone,
        nickname: profile?.customer.nickname,
        leadType: profile?.customer.leadType,
        sourceTable: profile?.customer.sourceTable,
        reminderType: profileReturnContext.value?.reminderType ?? null,
        returnToFollowups: Boolean(profileReturnContext.value?.taskQueueOpen),
        customer: { ...profile?.customer }
      }
    : undefined;
  eventBus.emit('quick-search:show', context);
}
```

Native/global Quick Search events continue to call `showQuickSearch()` without context.

- [ ] **Step 5: Remove the implicit profile-store dependency**

Delete the `customerProfileState` import from `quickSearchStore.ts`. Resolve variables only from `quickSearchState.customerContext`; after a successful text clipboard write, create `pendingSend` only when a context exists. Without context, set the toast to `已复制；未关联客户，本次不记录跟进`.

- [ ] **Step 6: Run Quick Search store tests and verify GREEN**

```powershell
cd desktop
npm test -- src/renderer/modules/quick-search/quickSearchStore.test.ts
```

Expected: all store tests pass.

### Task 3: Render and Submit Inline Send Confirmation

**Files:**
- Modify: `desktop/src/renderer/modules/quick-search/QuickSearchOverlay.test.ts`
- Modify: `desktop/src/renderer/modules/quick-search/QuickSearchOverlay.vue`
- Modify: `desktop/src/renderer/modules/quick-search/quickSearchStore.ts`

- [ ] **Step 1: Write failing rendered interaction tests**

Test the exact user flow:

```ts
eventBus.emit('quick-search:show', customerContext());
await flushUi();
(host.querySelector('.quick-item .primary') as HTMLButtonElement).click();
await flushUi();

expect(host.textContent).toContain('已复制给');
expect(host.textContent).toContain('王女士');
expect(host.querySelector('.quick-send-confirm')).toBeTruthy();
expect(host.querySelector('.quick-send-decline')).toBeTruthy();
```

Click `未发送` and verify the template panel remains open, no API call occurs, and copy buttons become usable again. Click close while pending and verify no API call occurs.

- [ ] **Step 2: Run the overlay test and verify RED**

```powershell
cd desktop
npm test -- src/renderer/modules/quick-search/QuickSearchOverlay.test.ts
```

Expected: FAIL because the inline confirmation controls do not exist.

- [ ] **Step 3: Implement confirmation submission**

Add:

```ts
export async function confirmQuickSearchSent(): Promise<void> {
  const context = quickSearchState.customerContext;
  const pending = quickSearchState.pendingSend;
  if (!context || !pending || quickSearchState.confirming) return;
  quickSearchState.confirming = true;
  try {
    const response = await postJson('/api/v1/chat/send-confirm', {
      phone: context.phone,
      nickname: context.nickname ?? '',
      isNewCustomer: false,
      sourceTable: context.sourceTable ?? '',
      leadType: context.leadType ?? '',
      conversationSummary: `发送模板《${pending.title}》：${pending.sentText}`,
      sentText: pending.sentText,
      selectedDirection: pending.scene || 'QUICK_SEARCH_TEMPLATE',
      completeCurrentFollowup: context.reminderType === 'DUE_TODAY' || context.reminderType === 'OVERDUE'
    });
    if (!response.success) throw new Error(response.message ?? 'send confirm failed');
    eventBus.emit('reply:send-confirmed', { phone: context.phone });
    eventBus.emit('followup:completed', { phone: context.phone, reminderType: context.reminderType });
    eventBus.emit('quick-search:sent', { returnToFollowups: context.returnToFollowups });
    hideQuickSearch();
  } catch {
    quickSearchState.error = '跟进记录失败，请重试';
  } finally {
    quickSearchState.confirming = false;
  }
}
```

`declineQuickSearchSend()` clears only `pendingSend` and keeps the overlay open. `hideQuickSearch()` clears pending/context state and never calls the backend.

- [ ] **Step 4: Render the associated customer and card actions**

Show a compact context line near the header. For the pending card, replace the copy button with:

```vue
<div v-if="state.pendingSend?.itemId === item.id" class="quick-send-actions">
  <span>已复制给：{{ state.customerContext?.nickname || `客户 ${state.customerContext?.phone.slice(-4)}` }}</span>
  <button class="primary small quick-send-confirm" :disabled="state.confirming" @click="confirmQuickSearchSent">
    {{ state.confirming ? '记录中' : '已发送' }}
  </button>
  <button class="secondary small quick-send-decline" :disabled="state.confirming" @click="declineQuickSearchSend">未发送</button>
</div>
```

Disable other copy buttons while `pendingSend` exists.

- [ ] **Step 5: Run Quick Search store and overlay tests and verify GREEN**

```powershell
cd desktop
npm test -- src/renderer/modules/quick-search/quickSearchStore.test.ts src/renderer/modules/quick-search/QuickSearchOverlay.test.ts
```

Expected: both files pass.

### Task 4: Synchronize Followup and Workbench State

**Files:**
- Modify: `desktop/src/renderer/modules/followup-list/followupListStore.test.ts`
- Modify: `desktop/src/renderer/modules/followup-list/followupListStore.ts`
- Modify: `desktop/src/renderer/modules/followup-list/FollowupListPanel.vue`
- Modify: `desktop/src/renderer/modules/workbench/workbenchStore.test.ts`
- Modify: `desktop/src/renderer/modules/workbench/workbenchStore.ts`
- Modify: `desktop/src/renderer/modules/workbench/WorkbenchPanel.vue`
- Modify: `desktop/src/renderer/App.test.ts`
- Modify: `desktop/src/renderer/App.vue`

- [ ] **Step 1: Write failing store completion tests**

```ts
followups.followupListState.groups.DUE_TODAY = [item({ phone: '18800001111' }), item({ phone: '18800002222' })];
followups.followupListState.groups.OVERDUE = [item({ phone: '18800003333', reminderType: 'OVERDUE' })];
followups.completeFollowup('18800001111', 'DUE_TODAY');
expect(followups.followupListState.groups.DUE_TODAY.map((item) => item.phone)).toEqual(['18800002222']);
expect(followups.followupListState.groups.OVERDUE).toHaveLength(1);
```

For workbench, verify completion removes the matching due/overdue row and marks `followupDataDirty=true`.

- [ ] **Step 2: Run store tests and verify RED**

```powershell
cd desktop
npm test -- src/renderer/modules/followup-list/followupListStore.test.ts src/renderer/modules/workbench/workbenchStore.test.ts
```

Expected: FAIL because the completion handlers do not exist.

- [ ] **Step 3: Implement completion handlers and listeners**

```ts
export function completeFollowup(phone: string, reminderType?: FollowupTab | null): void {
  if (reminderType !== 'DUE_TODAY' && reminderType !== 'OVERDUE') return;
  followupListState.groups[reminderType] = followupListState.groups[reminderType]
    .filter((item) => (item.phoneFull ?? item.phone) !== phone);
  followupListState.selectedPhones.delete(phone);
}
```

Add event-bus listeners in `FollowupListPanel.vue` and `WorkbenchPanel.vue`. Workbench removes only `DUE_TODAY`/`OVERDUE` items matching the phone and marks itself dirty.

- [ ] **Step 4: Return to the original followup list after success**

Handle `quick-search:sent` in `App.vue`. If `returnToFollowups` and a return context exist, call `returnFromCustomerProfile()`. Otherwise stay on the customer profile after the overlay closes.

- [ ] **Step 5: Run focused frontend integration tests and verify GREEN**

```powershell
cd desktop
npm test -- src/renderer/App.test.ts src/renderer/modules/followup-list/followupListStore.test.ts src/renderer/modules/followup-list/FollowupListPanel.test.ts src/renderer/modules/workbench/workbenchStore.test.ts src/renderer/modules/workbench/WorkbenchPanel.test.ts src/renderer/modules/quick-search/quickSearchStore.test.ts src/renderer/modules/quick-search/QuickSearchOverlay.test.ts
```

Expected: all focused frontend tests pass.

### Task 5: Persist Confirmed Followups Synchronously

**Files:**
- Create: `src/main/java/com/privateflow/modules/profile/service/FollowupConfirmationService.java`
- Create: `src/test/java/com/privateflow/modules/profile/service/FollowupConfirmationServiceTest.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/SendConfirmRequest.java`
- Modify: `src/main/java/com/privateflow/common/events/CustomerMessageSentEvent.java`
- Modify: `src/main/java/com/privateflow/modules/api/chat/ChatOrchestrationService.java`
- Modify: `src/test/java/com/privateflow/modules/api/chat/ChatOrchestrationServiceTest.java`
- Modify: `src/main/java/com/privateflow/modules/profile/service/ProfileUpdateOrchestrator.java`
- Modify: `src/test/java/com/privateflow/modules/profile/service/ProfileUpdateOrchestratorTest.java`

- [ ] **Step 1: Write failing confirmation-service tests**

Test three cases with an `ArgumentCaptor<Map<String, Object>>` around `ProfileWriter.write`:

```java
assertThat(fields).containsKeys("lastFollowupAt", "followupNotes");
assertThat(fields.get("followupNotes")).isEqualTo("发送模板《到店提醒》：明天见");
```

For `completeCurrentFollowup=true` with no suggestion:

```java
assertThat(fields).containsEntry("nextFollowupAt", null);
assertThat(fields).containsEntry("nextFollowupDir", null);
```

For a suggestion:

```java
assertThat(fields).containsEntry("nextFollowupAt", "2026-07-22T10:00:00");
assertThat(fields).containsEntry("nextFollowupDir", "再次确认到店");
```

- [ ] **Step 2: Run the new service test and verify RED**

```powershell
./mvnw.cmd -Dtest=FollowupConfirmationServiceTest test
```

Expected: FAIL because the service does not exist.

- [ ] **Step 3: Add explicit completion intent to the contracts**

Add `boolean completeCurrentFollowup` to `SendConfirmRequest` and `CustomerMessageSentEvent`. Omitted JSON values deserialize to `false`, preserving existing clients.

- [ ] **Step 4: Implement the synchronous core write**

Create a Spring service with:

```java
public void record(
    Customer customer,
    String conversationSummary,
    String sentText,
    CustomerMessageSentEvent.FollowupSuggestPayload suggestion,
    boolean completeCurrentFollowup) {
  if (customer == null) return;
  Map<String, Object> fields = new LinkedHashMap<>();
  fields.put("lastFollowupAt", LocalDateTime.now());
  fields.put("followupNotes", firstNonBlank(conversationSummary, sentText));
  if (suggestion != null && !blank(suggestion.nextFollowupAt())) {
    fields.put("nextFollowupAt", suggestion.nextFollowupAt());
    fields.put("nextFollowupDir", suggestion.nextFollowupDir());
  } else if (completeCurrentFollowup) {
    fields.put("nextFollowupAt", null);
    fields.put("nextFollowupDir", null);
  }
  profileWriter.write(customer.getPhone(), fields, customer.getVersion(), true);
}
```

- [ ] **Step 5: Call the service before publishing the event**

Make access validation return the existing customer. After calculating summary and followup suggestion, call the confirmation service, then publish `CustomerMessageSentEvent` including the completion flag. New-customer sends skip the local write and continue through the existing table-creation event.

- [ ] **Step 6: Remove duplicate core writes from async profile extraction**

`ProfileUpdateOrchestrator` must continue extracting high-confidence profile/tag changes, but remove its unconditional `lastFollowupAt` and `followupNotes` assignments. Update the test so blank employee-only evidence remains excluded from extraction without expecting the async orchestrator to own the core followup record.

- [ ] **Step 7: Run focused backend tests and verify GREEN**

```powershell
./mvnw.cmd -Dtest=FollowupConfirmationServiceTest,ChatOrchestrationServiceTest,ProfileUpdateOrchestratorTest test
```

Expected: all focused backend tests pass.

### Task 6: Keep Enterprise Table Writes Semantically Aligned

**Files:**
- Modify: `src/test/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdaterTest.java`
- Modify: `src/main/java/com/privateflow/modules/tablewrite/service/ExistingCustomerUpdater.java`
- Modify: any `CustomerMessageSentEvent` constructors in table-write tests to include the new boolean.

- [ ] **Step 1: Write failing table-field tests**

Verify `followupFields(event)` includes:

```java
assertThat(fields).containsEntry("followupNotes", "发送模板《到店提醒》：明天见");
assertThat(fields.get("lastFollowupAt")).isInstanceOf(String.class);
```

For explicit completion without a new suggestion:

```java
assertThat(fields).containsEntry("nextFollowupAt", "");
assertThat(fields).containsEntry("nextFollowupDir", "");
```

For a new suggestion, assert the suggested values replace the clear markers.

- [ ] **Step 2: Run the updater test and verify RED**

```powershell
./mvnw.cmd -Dtest=ExistingCustomerUpdaterTest test
```

Expected: FAIL because the current outbound fields omit last followup and completion clears.

- [ ] **Step 3: Implement aligned outbound fields**

Use the event's already-computed conversation summary. Add `lastFollowupAt=LocalDateTime.now().toString()`. When `followupSuggest` exists, send its next fields; otherwise, when `completeCurrentFollowup` is true, send empty strings for `nextFollowupAt` and `nextFollowupDir` so the remote gateway receives explicit clear values instead of silently omitting the fields.

- [ ] **Step 4: Run table-write and orchestration tests and verify GREEN**

```powershell
./mvnw.cmd -Dtest=ExistingCustomerUpdaterTest,TableWriteOrchestratorTest,QueueRetryManagerTest test
```

Expected: all focused tests pass and failed remote writes still enter the existing queue.

### Task 7: Full Verification and Desktop Packaging

**Files:**
- Verify all modified frontend/backend files.
- Update generated package only through existing scripts.

- [ ] **Step 1: Run the full frontend suite**

```powershell
cd desktop
npm test
```

Expected: all Vitest files pass.

- [ ] **Step 2: Run frontend type checking**

```powershell
cd desktop
npm run typecheck
```

Expected: exit code 0.

- [ ] **Step 3: Run renderer smoke**

```powershell
cd desktop
npm run renderer:smoke
```

Expected: `renderer_smoke=passed`.

- [ ] **Step 4: Run the full backend suite**

```powershell
./mvnw.cmd test
```

Expected: all backend tests pass; only previously documented environment-dependent skips may remain.

- [ ] **Step 5: Rebuild and verify the Windows package**

Close only the running `desktop/release/Private Domain Assistant-win32-x64/Private Domain Assistant.exe` processes, then run:

```powershell
cd desktop
npm run package:verify
```

Expected: `package_verify=passed`; package remains unsigned unless signing is separately configured.

- [ ] **Step 6: Relaunch the packaged application for manual acceptance**

Launch `desktop/release/Private Domain Assistant-win32-x64/Private Domain Assistant.exe` and ask the user to verify:

```text
今日待跟进 -> 档案 -> 模板 -> 复制
未发送：留在模板，客户仍在待办
已发送：记录成功，返回今日待跟进，客户从当前任务移除
返回按钮：不发送也能回到原待办分类
```

## Repository Handling

Do not create commits, push, merge, or discard work automatically. The current branch contains substantial pre-existing user changes and the user explicitly asked to continue in place.
