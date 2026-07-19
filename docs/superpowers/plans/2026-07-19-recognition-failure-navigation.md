# 识图失败自动聚焦回复助手 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Make every desktop recognition failure visible in the Reply Assistant automatically and preserve the backend's actionable failure message.

**Architecture:** Keep recognition transport and backend contracts unchanged. `recognitionStore` will carry the API response message into existing failure events, while `App.vue` will focus the reply panel for recognition terminal events and immediately when the global blue recognition action starts. Existing `ReplySuggestionPanel` failure rendering will consume the event message through its current store path.

**Tech Stack:** Vue 3 `<script setup>`, TypeScript, Vitest, Electron bridge, existing event bus.

---

### Task 1: Lock the navigation regression in App tests

**Files:**
- Modify: `desktop/src/renderer/App.test.ts`

- [ ] **Step 1: Add a screenshot-failure test that starts from the workbench**

Use the existing `installDesktopBridge()` and `mountAppWithToken()` helpers. Override the mocked `captureScreenshot` to return `{ success: false, error: 'CAPTURE_FAILED' }`, click the first sidebar quick action, flush Vue updates, and assert that the active nav label is `回复助手` and `triggerRecognize` was not called.

```ts
it('focuses the reply assistant when the global screenshot capture fails', async () => {
  const [{ captureScreenshot }, { triggerRecognize }] = await Promise.all([
    import('./shared/desktopBridge'),
    import('./modules/chat-recognition/recognitionStore')
  ]);
  installDesktopBridge();
  vi.mocked(captureScreenshot).mockResolvedValueOnce({ success: false, error: 'CAPTURE_FAILED' });
  const { app, host } = await mountAppWithToken('#/desktop');

  (host.querySelector('.sidebar-quick-actions button') as HTMLButtonElement).click();
  await flushUi();

  expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent)
    .toBe('回复助手');
  expect(triggerRecognize).not.toHaveBeenCalled();
  app.unmount();
});
```

- [ ] **Step 2: Add a terminal failure event navigation test**

Import the real `eventBus`, mount from `#/desktop`, emit `recognize:image-failed`, flush updates, and assert that the Reply Assistant nav is active. This proves failures from clipboard/workbench recognition are covered even when they do not pass through `recognizeFromAnywhere`.

```ts
it('focuses the reply assistant for recognition failure events', async () => {
  const { eventBus } = await import('./shared/eventBus');
  installDesktopBridge();
  const { app, host } = await mountAppWithToken('#/desktop');

  eventBus.emit('recognize:image-failed', {
    sessionId: 'failure-session',
    errorCode: '30-10001',
    message: '未能从图片中识别到聊天内容，请确认截图中包含聊天窗口'
  });
  await flushUi();

  expect((host.querySelector('.desktop-nav-button.active .nav-label') as HTMLElement | null)?.textContent)
    .toBe('回复助手');
  app.unmount();
});
```

- [ ] **Step 3: Run the focused App test and verify RED**

Run from `C:\Users\85314\Desktop\私域工具\私域辅助系统\desktop`:

```powershell
npm run test -- App.test.ts
```

Expected: the two new tests fail because `App.vue` currently returns before selecting Reply Assistant on capture failure and does not subscribe to failure events.

### Task 2: Lock detailed error propagation in recognitionStore tests

**Files:**
- Modify: `desktop/src/renderer/modules/chat-recognition/recognitionStore.test.ts`

- [ ] **Step 1: Add a test for the backend's detailed failure message**

Mock a failed API response with `errorCode: '30-10001'` and the message returned by the live reproduction. Subscribe to `recognize:image-failed`, run `triggerRecognize`, and assert the event message equals the backend message.

```ts
it('preserves the backend message on image recognition failure events', async () => {
  const { recognition, eventBus } = await freshStore();
  const events: unknown[] = [];
  eventBus.on('recognize:image-failed', (payload) => events.push(payload));
  postJsonMock.mockResolvedValueOnce({
    success: false,
    errorCode: '30-10001',
    message: '未能从图片中识别到聊天内容，请确认截图中包含聊天窗口'
  });

  await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'bad-image' });

  expect(events[0]).toMatchObject({
    errorCode: '30-10001',
    message: '未能从图片中识别到聊天内容，请确认截图中包含聊天窗口'
  });
});
```

- [ ] **Step 2: Add a fallback-message assertion**

Mock the same error code without `message` and assert the event still contains `图片识别失败，请粘贴客户标识和聊天内容`.

- [ ] **Step 3: Run the focused recognitionStore test and verify RED**

```powershell
npm run test -- modules/chat-recognition/recognitionStore.test.ts
```

Expected: the detailed-message assertion fails because `handleError` currently accepts only the error code and always emits the fixed text.

### Task 3: Implement the minimal event and message fixes

**Files:**
- Modify: `desktop/src/renderer/App.vue:292-300,431-440`
- Modify: `desktop/src/renderer/modules/chat-recognition/recognitionStore.ts:60-62,143-157`

- [ ] **Step 1: Focus Reply Assistant when global recognition begins or capture fails**

Call `selectDesktopPanel('reply')` at the start of `recognizeFromAnywhere()` after clearing the desktop notice. Keep the existing screenshot failure notice and return path; the panel is already focused when the error is shown.

- [ ] **Step 2: Subscribe App to recognition terminal events**

Register these existing event names beside the current result subscription:

```ts
const focusReplyAssistant = () => selectDesktopPanel('reply');
eventDisposers.push(eventBus.on('recognize:result', focusReplyAssistant));
eventDisposers.push(eventBus.on('recognize:image-failed', focusReplyAssistant));
eventDisposers.push(eventBus.on('recognize:failed', focusReplyAssistant));
eventDisposers.push(eventBus.on('recognize:timeout', focusReplyAssistant));
eventDisposers.push(eventBus.on('recognize:multiple', focusReplyAssistant));
```

Keep `suggestion:show` as its existing separate subscription. All disposers continue to be removed in `onBeforeUnmount`.

- [ ] **Step 3: Pass API message into `handleError`**

Change the call to `handleError(response.errorCode, sessionId, response.message)` and implement a `message?: string | null` parameter. For each branch, compute the branch fallback and emit `message?.trim() || fallback`; the `30-10001` branch still enables text mode.

```ts
function handleError(errorCode: string | null, sessionId: string, message?: string | null): void {
  if (errorCode === '30-10001') {
    const detail = message?.trim() || '图片识别失败，请粘贴客户标识和聊天内容';
    recognitionState.toast = detail;
    recognitionState.isTwoBoxMode = true;
    eventBus.emit('recognize:image-failed', { sessionId, errorCode, message: detail });
    return;
  }
  const fallback = errorCode === '30-10002'
    ? '图片格式不支持，请使用 PNG/JPG 截图'
    : errorCode === '80-10002'
      ? '登录已失效，请重新登录'
      : '识别失败，请稍后重试';
  const detail = message?.trim() || fallback;
  recognitionState.toast = detail;
  eventBus.emit('recognize:failed', { sessionId, errorCode, message: detail });
}
```

- [ ] **Step 4: Run the focused tests and verify GREEN**

```powershell
npm run test -- App.test.ts modules/chat-recognition/recognitionStore.test.ts
```

Expected: all tests in both files pass, including the new regression cases.

### Task 4: Run proportional desktop verification

**Files:**
- No additional source files.

- [ ] **Step 1: Run desktop typecheck**

```powershell
npm run typecheck
```

Expected: exit code 0 with no TypeScript errors.

- [ ] **Step 2: Run the full desktop Vitest suite**

```powershell
npm run test
```

Expected: all existing and new tests pass.

- [ ] **Step 3: Build the desktop renderer**

```powershell
npm run build
```

Expected: exit code 0 and a fresh renderer build.

- [ ] **Step 4: Run Electron renderer smoke**

```powershell
npm run renderer:smoke
```

Expected: `renderer_smoke=passed`, including the global recognition flow and reply-panel layout checks.

- [ ] **Step 5: Inspect the final diff**

```powershell
git diff --check
git status --short
git diff -- desktop/src/renderer/App.vue desktop/src/renderer/modules/chat-recognition/recognitionStore.ts desktop/src/renderer/App.test.ts desktop/src/renderer/modules/chat-recognition/recognitionStore.test.ts
```

Expected: only the scoped source/test changes are present in addition to pre-existing user changes; no whitespace errors.

