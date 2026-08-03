import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AbnormalAlertPayload, ChatResponse, ProfileSuggestion, ReplySuggestion } from './types';

const mocks = vi.hoisted(() => ({
  postJson: vi.fn(),
  getAlertsByPhone: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  postJson: mocks.postJson
}));

vi.mock('../abnormal-alert/alertStore', () => ({
  getAlertsByPhone: mocks.getAlertsByPhone
}));

type MountedPanel = {
  app: App<Element>;
  host: HTMLDivElement;
  eventBus: typeof import('../../shared/eventBus')['eventBus'];
};

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    value: {
      getItem: vi.fn((key: string) => store.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => store.set(key, String(value))),
      removeItem: vi.fn((key: string) => store.delete(key)),
      clear: vi.fn(() => store.clear())
    },
    configurable: true
  });
}

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountPanel(resetStorage = true): Promise<MountedPanel> {
  vi.resetModules();
  if (resetStorage) {
    localStorage.clear();
    localStorage.setItem('desktop_config', JSON.stringify({
      requestTotalTimeoutMs: 1000,
      fallbackRetryIntervalMs: 100,
      fallbackMaxRetries: 2,
      accountUsername: 'admin'
    }));
  }
  const [{ default: ReplySuggestionPanel }, { eventBus }] = await Promise.all([
    import('./ReplySuggestionPanel.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(ReplySuggestionPanel);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

describe('ReplySuggestionPanel', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    mocks.getAlertsByPhone.mockReturnValue([]);
    mocks.postJson.mockResolvedValue({ success: true, data: {} });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    localStorage.clear();
    mocks.postJson.mockReset();
    mocks.getAlertsByPhone.mockReset();
  });

  it('renders recognized reply cards and emits DOM-driven reply and help events', async () => {
    const { app, host, eventBus } = await mountPanel();
    const selected: unknown[] = [];
    const helpRequests: unknown[] = [];
    eventBus.on('reply:selected', (payload) => selected.push(payload));
    eventBus.on('help:request', (payload) => helpRequests.push(payload));

    eventBus.emit('recognize:result', { response: response('18800001111', [suggestion('Ask for budget'), suggestion('Offer appointment')]) });
    await flushUi();

    expect(host.querySelectorAll('.reply-card')).toHaveLength(2);
    expect(host.textContent).toContain('Ask for budget');
    expect(host.textContent).toContain('Alice');
    expect(host.textContent).toContain('****1111');
    expect(host.textContent).toContain('Skill 生成');
    expect(host.querySelector('.reply-current-actions')?.textContent).toContain('复制');
    expect(host.querySelector('.reply-current-time')?.textContent).toBe('刚刚');

    const copyButton = host.querySelector('.reply-card .primary') as HTMLButtonElement | null;
    copyButton?.click();
    await flushUi();

    expect(selected).toEqual([expect.objectContaining({
      text: 'Ask for budget',
      direction: 'NEXT_STEP',
      reason: 'reason',
      phone: '18800001111',
      displayPhone: '****1111',
      replySource: 'SKILL',
      isFallback: false
    })]);

    const actionButtons = [...host.querySelectorAll('.reply-actions button')] as HTMLButtonElement[];
    actionButtons.at(-1)?.click();
    await flushUi();

    expect(helpRequests).toEqual([{
      phone: '18800001111',
      clientMessage: '',
      aiSuggestions: [
        { text: 'Ask for budget', direction: 'NEXT_STEP' },
        { text: 'Offer appointment', direction: 'NEXT_STEP' }
      ]
    }]);
    expect(host.textContent).toContain('Ask for budget');
    app.unmount();
  });

  it('restores recognized reply sessions after the panel is remounted', async () => {
    const first = await mountPanel();

    first.eventBus.emit('recognize:result', {
      sessionId: 'persisted-session',
      response: response('18800001111', [suggestion('Persisted reply')])
    });
    await flushUi();
    first.app.unmount();

    const second = await mountPanel(false);
    expect(second.host.textContent).toContain('Persisted reply');
    expect(second.host.textContent).toContain('Alice');

    second.app.unmount();
  });

  it('restores recognized reply sessions when the same running app returns from login', async () => {
    vi.resetModules();
    localStorage.clear();
    localStorage.setItem('desktop_config', JSON.stringify({
      requestTotalTimeoutMs: 1000,
      fallbackRetryIntervalMs: 100,
      fallbackMaxRetries: 2,
      accountUsername: 'admin'
    }));
    const [{ default: ReplySuggestionPanel }, { eventBus }] = await Promise.all([
      import('./ReplySuggestionPanel.vue'),
      import('../../shared/eventBus')
    ]);

    const firstHost = document.createElement('div');
    document.body.appendChild(firstHost);
    const firstApp = createApp(ReplySuggestionPanel);
    firstApp.mount(firstHost);
    await flushUi();

    eventBus.emit('recognize:result', {
      sessionId: 'same-app-session',
      response: response('18800001111', [suggestion('Same app persisted reply')])
    });
    await flushUi();
    firstApp.unmount();

    const secondHost = document.createElement('div');
    document.body.appendChild(secondHost);
    const secondApp = createApp(ReplySuggestionPanel);
    secondApp.mount(secondHost);
    await flushUi();

    expect(secondHost.textContent).toContain('Same app persisted reply');
    expect(secondHost.textContent).toContain('Alice');

    secondApp.unmount();
  });

  it('shows reply source labels for LLM and fallback responses', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:result', {
      response: {
        ...response('18800001111', [suggestion('LLM reply')]),
        replySource: { source: 'LLM', label: 'LLM 生成', detail: 'route hit' }
      }
    });
    await flushUi();

    expect(host.querySelector('.reply-source-pill.source-llm')?.textContent).toContain('LLM 生成');

    eventBus.emit('recognize:result', {
      response: {
        ...response('18800002222', [{ text: 'fallback reply', direction: 'SYSTEM_FALLBACK', reason: 'down' }]),
        replySource: { source: 'FALLBACK', label: '系统兜底', detail: 'down' }
      }
    });
    await flushUi();

    expect(host.querySelector('.reply-source-pill.source-fallback')?.textContent).toContain('系统兜底');
    app.unmount();
  });

  it('retries recognition instead of regeneration for a fallback without a matched customer', async () => {
    const { app, host, eventBus } = await mountPanel();
    const recognizeRequests: unknown[] = [];
    eventBus.on('desktop:recognize-request', (payload) => recognizeRequests.push(payload));

    eventBus.emit('recognize:result', {
      response: {
        ...response('19900001111', [{ text: 'fallback reply', direction: 'SYSTEM_FALLBACK', reason: 'down' }]),
        needsCustomerIdentifier: true,
        match: { matchType: 'NONE' }
      }
    });
    await flushUi();

    const retryButton = [...host.querySelectorAll('.reply-primary-actions button')]
      .find((button) => button.textContent?.includes('重新识别')) as HTMLButtonElement | undefined;
    expect(retryButton).toBeTruthy();

    retryButton?.click();
    await flushUi();

    expect(recognizeRequests).toEqual([{}]);
    expect(mocks.postJson).not.toHaveBeenCalled();
    app.unmount();
  });

  it('keeps the active reply available while task switching moves to the task drawer', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-a', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', { sessionId: 'session-a', response: response('18800001111', [suggestion('First reply')]) });
    await flushUi();

    eventBus.emit('recognize:start', { sessionId: 'session-b', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', { sessionId: 'session-b', response: response('18800002222', [suggestion('Second reply')]) });
    await flushUi();

    expect(host.querySelector('.reply-task-queue')).toBeFalsy();
    expect(host.querySelector('.reply-current-task')).toBeTruthy();
    expect(host.textContent).toContain('Second reply');
    app.unmount();
  });

  it('keeps generated reply text out of the current task summary', async () => {
    const { app, host, eventBus } = await mountPanel();
    eventBus.emit('recognize:result', { response: response('18800001111', [suggestion('Only show this in reply cards')]) });
    await flushUi();

    expect(host.querySelector('.reply-primary-card')?.textContent).toContain('Only show this in reply cards');
    expect(host.querySelector('.reply-current-task')?.textContent).not.toContain('Only show this in reply cards');
    app.unmount();
  });

  it('renders queued and cancelled recognition job states for the original task', async () => {
    const { app, host, eventBus } = await mountPanel();
    eventBus.emit('recognize:start', { sessionId: 'session-job', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:job', { sessionId: 'session-job', jobId: 'job-1', status: 'QUEUED' });
    await flushUi();

    expect(host.querySelector('.reply-current-task.status-loading')?.textContent).toContain('正在排队识图');

    eventBus.emit('recognize:job', { sessionId: 'session-job', jobId: 'job-1', status: 'CANCELLED' });
    await flushUi();

    expect(host.querySelector('.reply-current-task.status-cancelled')?.textContent).toContain('任务已取消');
    app.unmount();
  });

  it('removes the duplicate header recognition action and keeps the primary copy control fixed', async () => {
    const { app, host, eventBus } = await mountPanel();
    eventBus.emit('recognize:result', { response: response('18800001111', [suggestion('Primary reply')]) });
    await flushUi();

    const headerButtons = [...host.querySelectorAll('.reply-hero button')];
    expect(headerButtons.some((button) => button.textContent?.trim() === '识别聊天')).toBe(false);
    expect(host.querySelector('.reply-primary-copy')).toBeTruthy();
    app.unmount();
  });

  it('opens the personal template editor from an AI reply without copying or sending it', async () => {
    const { app, host, eventBus } = await mountPanel();
    const openings: unknown[] = [];
    eventBus.on('template-editor:show', (payload) => openings.push(payload));
    eventBus.emit('recognize:start', { sessionId: 'session-template', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', {
      sessionId: 'session-template',
      response: response('18800001111', [suggestion('AI response to edit')])
    });
    await flushUi();

    const saveButton = host.querySelector('[data-testid="save-reply-template"]') as HTMLButtonElement | null;
    expect(saveButton).toBeTruthy();
    saveButton?.click();
    await flushUi();

    expect(openings).toEqual([expect.objectContaining({
      body: 'AI response to edit',
      originalAiReply: 'AI response to edit',
      sourceReplySessionId: 'session-template'
    })]);
    expect(mocks.postJson).not.toHaveBeenCalled();
    app.unmount();
  });

  it('keeps the full reply workflow without embedding the task list', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:result', {
      response: response('18800001111', [
        suggestion('Primary reply'),
        suggestion('Second reply'),
        suggestion('Third reply')
      ])
    });
    await flushUi();

    const panelChildren = [...host.querySelector('.reply-panel')?.children ?? []] as HTMLElement[];
    const primaryIndex = panelChildren.findIndex((item) => item.classList.contains('reply-primary-card'));
    const moreIndex = panelChildren.findIndex((item) => item.classList.contains('reply-alt-list'));
    const currentTaskIndex = panelChildren.findIndex((item) => item.classList.contains('reply-current-task'));
    expect(primaryIndex).toBeGreaterThanOrEqual(0);
    expect(moreIndex).toBeGreaterThan(primaryIndex);
    expect(currentTaskIndex).toBeGreaterThan(moreIndex);

    expect(host.querySelector('.reply-primary-card')?.textContent).toContain('Primary reply');
    expect(host.querySelector('.reply-alt-list')?.textContent).toContain('Second reply');
    expect(host.querySelector('.reply-alt-list')?.textContent).toContain('Third reply');
    expect(host.querySelector('.reply-task-queue')).toBeFalsy();

    app.unmount();
  });

  it('shows a readable updated time for the current task', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-time', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', { sessionId: 'session-time', response: response('18800001111', [suggestion('Timed reply')]) });
    await flushUi();

    expect(host.querySelector('.reply-current-time')?.textContent).toBe('刚刚');
    app.unmount();
  });

  it('shows failed recognition tasks with retry and text channel actions', async () => {
    const { app, host, eventBus } = await mountPanel();
    const retryEvents: unknown[] = [];
    eventBus.on('desktop:recognize-request', (payload) => retryEvents.push(payload));

    eventBus.emit('recognize:start', { sessionId: 'session-failed', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:failed', {
      sessionId: 'session-failed',
      errorCode: '30-10001',
      message: '图片识别失败，请使用文字通道后重新生成回复'
    });
    await flushUi();

    expect(host.querySelector('.reply-failure-state')?.textContent ?? '').toContain('图片识别失败');
    expect(host.querySelector('.reply-current-task.status-failed')).toBeTruthy();
    expect(host.textContent).toContain('失败');
    expect(host.querySelector('.reply-current-time')?.textContent).toBe('刚刚');
    expect(host.querySelector('.reply-current-actions')?.textContent).toContain('重试');
    expect(host.querySelector('.reply-current-actions')?.textContent).toContain('文字');

    const retry = [...host.querySelectorAll('.reply-failure-state button')]
      .find((button) => button.textContent?.includes('重试')) as HTMLButtonElement | undefined;
    retry?.click();
    await flushUi();
    expect(retryEvents).toHaveLength(1);

    const textChannel = [...host.querySelectorAll('.reply-failure-state button')]
      .find((button) => button.textContent?.includes('文字通道')) as HTMLButtonElement | undefined;
    textChannel?.click();
    await flushUi();
    expect(host.querySelector('.reply-text-channel textarea')).toBeTruthy();
    app.unmount();
  });

  it('shows the backend detail for image recognition failures', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-image-failed', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:image-failed', {
      sessionId: 'session-image-failed',
      errorCode: '30-10001',
      message: '未能从图片中识别到聊天内容，请确认截图中包含聊天窗口'
    });
    await flushUi();

    expect(host.querySelector('.reply-failure-state')?.textContent ?? '')
      .toContain('未能从图片中识别到聊天内容，请确认截图中包含聊天窗口');
    app.unmount();
  });

  it('shows a close icon for a single task, confirms removal, and ignores late results', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-close', source: 'BUTTON_CLICK' });
    await flushUi();

    expect(host.querySelector('.reply-task-queue')).toBeFalsy();
    const closeButton = host.querySelector('.reply-current-task .icon-close-button') as HTMLButtonElement | null;
    expect(closeButton).toBeTruthy();

    closeButton?.click();
    await flushUi();

    expect(host.querySelector('.reply-current-task')).toBeTruthy();
    expect(host.querySelector('.reply-task-remove-confirm')?.textContent).toContain('移除这条任务？');

    const cancelButton = [...host.querySelectorAll('.reply-task-remove-confirm button')]
      .find((button) => button.textContent?.includes('取消')) as HTMLButtonElement | undefined;
    cancelButton?.click();
    await flushUi();

    expect(host.querySelector('.reply-current-task')).toBeTruthy();
    expect(host.querySelector('.reply-task-remove-confirm')).toBeFalsy();

    closeButton?.click();
    await flushUi();
    const removeButton = [...host.querySelectorAll('.reply-task-remove-confirm button')]
      .find((button) => button.textContent?.includes('移除')) as HTMLButtonElement | undefined;
    removeButton?.click();
    await flushUi();

    expect(host.querySelector('.reply-current-task')).toBeFalsy();
    expect(host.querySelector('.reply-empty-state')?.textContent ?? '').toContain('还没有识别当前聊天');

    eventBus.emit('recognize:result', { sessionId: 'session-close', response: response('18800005555', [suggestion('Late reply')]) });
    await flushUi();

    expect(host.textContent).not.toContain('Late reply');
    expect(host.querySelector('.reply-task-queue')).toBeFalsy();
    app.unmount();
  });

  it('cancels an active recognition job before removing its local task', async () => {
    const { app, host, eventBus } = await mountPanel();
    const cancellations: unknown[] = [];
    eventBus.on('recognition-job:cancel', (payload) => cancellations.push(payload));
    eventBus.emit('recognize:start', { sessionId: 'session-cancel', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:job', { sessionId: 'session-cancel', jobId: 'job-1', status: 'QUEUED' });
    await flushUi();

    (host.querySelector('.reply-current-task .icon-close-button') as HTMLButtonElement).click();
    await flushUi();
    const removeButton = [...host.querySelectorAll('.reply-task-remove-confirm button')]
      .find((button) => button.textContent?.includes('移除')) as HTMLButtonElement | undefined;
    removeButton?.click();
    await flushUi();

    expect(cancellations).toEqual([{ jobId: 'job-1', sessionId: 'session-cancel' }]);
    expect(host.querySelector('.reply-current-task')).toBeFalsy();
    app.unmount();
  });

  it('shows loading tasks with text-channel and close actions but no retry action', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-loading', source: 'BUTTON_CLICK' });
    await flushUi();

    const loadingTask = host.querySelector('.reply-current-task.status-loading') as HTMLElement | null;
    expect(loadingTask).toBeTruthy();
    expect(host.querySelector('.reply-task-queue')).toBeFalsy();
    expect(host.querySelector('.reply-progress-panel')?.textContent).toContain('文字通道');
    expect(host.querySelector('.reply-progress-panel')?.textContent).not.toContain('重试');
    expect(loadingTask?.querySelector('.icon-close-button')).toBeTruthy();
    expect(host.querySelector('.reply-current-time')?.textContent).toBe('刚刚');
    expect(host.querySelector('.reply-current-actions')?.textContent).toContain('文字');

    const textButton = [...host.querySelectorAll('.reply-progress-panel button')]
      .find((button) => button.textContent?.includes('文字通道')) as HTMLButtonElement | undefined;
    textButton?.click();
    await flushUi();

    expect(host.querySelector('.reply-text-channel textarea')).toBeTruthy();
    app.unmount();
  });

  it('keeps current task copy and actions in separate blocks for narrow desktop panels', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-narrow', source: 'BUTTON_CLICK' });
    await flushUi();

    const currentTask = host.querySelector('.reply-current-task') as HTMLElement | null;
    expect(currentTask).toBeTruthy();
    expect(currentTask?.children[0]?.tagName).toBe('DIV');
    expect(currentTask?.children[1]?.classList.contains('reply-current-actions')).toBe(true);
    expect(currentTask?.querySelector('.reply-current-actions .icon-close-button')).toBeTruthy();
    expect(currentTask?.querySelector('.reply-current-actions')?.textContent).toContain('文字');

    app.unmount();
  });

  it('leaves pending clipboard screenshot confirmation to the global agent', async () => {
    const { app, host } = await mountPanel();
    const recognition = await import('../chat-recognition/recognitionStore');
    recognition.recognitionState.pendingClipboardImage = {
      imageBase64: 'clipboard-image',
      md5: 'clip-a',
      width: 360,
      height: 360
    };
    await flushUi();

    expect(mocks.postJson).not.toHaveBeenCalled();
    expect(recognition.recognitionState.pendingClipboardImage?.imageBase64).toBe('clipboard-image');
    expect(host.querySelector('.clipboard-capture-card')).toBeFalsy();
    app.unmount();
  });

  it('renders profile suggestions and resolves them through the batch API from actual buttons', async () => {
    const { app, host, eventBus } = await mountPanel();
    eventBus.emit('recognize:result', { response: response('18800002222', [suggestion('Reply')]) });
    await flushUi();

    eventBus.emit('PROFILE_SUGGESTIONS', {
      phone: '18800002222',
      suggestions: [profileSuggestion(11, 'intentLevel', 'LOW', 'HIGH'), profileSuggestion(12, 'intendedStore', '', 'Store A')]
    });
    await flushUi();

    expect(host.textContent).toContain('资料更新建议（2）');
    expect(host.querySelector('.inline-profile-suggestions .suggestion-list')).toBeFalsy();

    (host.querySelector('.inline-profile-suggestions .suggestion-toggle') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelectorAll('.inline-profile-suggestions .suggestion-item')).toHaveLength(2);
    expect(host.textContent).toContain('intentLevel');
    expect(host.textContent).toContain('intendedStore');

    const confirmAll = host.querySelector('.inline-profile-suggestions .suggestion-head .secondary') as HTMLButtonElement | null;
    confirmAll?.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/customers/18800002222/suggestions/batch-resolve', {
      action: 'CONFIRM',
      suggestionIds: [11, 12],
      operator: 'desktop'
    });
    expect(host.textContent).toContain('资料更新建议（0）');
    expect(host.querySelector('.inline-profile-suggestions .suggestion-list')).toBeFalsy();
    app.unmount();
  });

  it('shows copy-backfill profile suggestions inline and resolves a single item after expansion', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('suggestion:show', {
      phone: '18800004444',
      suggestions: [profileSuggestion(21, 'nickname', 'Old', 'New'), profileSuggestion(22, 'intentLevel', 'LOW', 'HIGH')]
    });
    await flushUi();

    expect(host.textContent).toContain('资料更新建议（2）');
    expect(host.querySelector('.inline-profile-suggestions')).toBeTruthy();
    expect(host.querySelector('.inline-profile-suggestions .suggestion-list')).toBeFalsy();

    (host.querySelector('.inline-profile-suggestions .suggestion-toggle') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.querySelectorAll('.inline-profile-suggestions .suggestion-item')).toHaveLength(2);
    const firstConfirm = host.querySelector('.inline-profile-suggestions .suggestion-item .suggestion-actions .secondary') as HTMLButtonElement | null;
    firstConfirm?.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/customers/18800004444/suggestions/batch-resolve', {
      action: 'CONFIRM',
      suggestionIds: [21],
      operator: 'desktop'
    });
    expect(host.textContent).toContain('资料更新建议（1）');
    app.unmount();
  });

  it('shows current abnormal alerts and clears the banner after an acknowledged alert event', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.getAlertsByPhone.mockReturnValue([alert('alert-a', '18800003333', false)]);

    eventBus.emit('recognize:result', { response: response('18800003333', [suggestion('Reply')]) });
    await flushUi();

    expect(host.querySelector('.alert-banner')?.textContent ?? '').toContain('High churn risk');

    eventBus.emit('abnormal:alert', alert('alert-a', '18800003333', true));
    await flushUi();

    expect(host.querySelector('.alert-banner')).toBeFalsy();
    app.unmount();
  });

  it('opens the fallback text channel and binds input to the shared recognition state', async () => {
    const { app, host } = await mountPanel();
    const recognition = await import('../chat-recognition/recognitionStore');
    recognition.recognitionState.isRecognizePending = false;
    recognition.recognitionState.lastRequestSource = null;
    recognition.recognitionState.lastRequestContentMd5 = '';
    recognition.recognitionState.lastRequestTime = 0;
    recognition.recognitionState.imageServiceStatus = 'UNKNOWN';
    recognition.recognitionState.isTwoBoxMode = false;

    const textMode = [...host.querySelectorAll('.reply-text-channel button')]
      .find((button) => button.textContent?.includes('文字通道')) as HTMLButtonElement | undefined;
    expect(textMode).toBeTruthy();
    textMode?.click();
    await flushUi();

    const identity = host.querySelector('.reply-text-channel input') as HTMLInputElement | null;
    const chat = host.querySelector('.reply-text-channel textarea') as HTMLTextAreaElement | null;
    expect(identity).toBeTruthy();
    expect(chat).toBeTruthy();
    setValue(identity as HTMLInputElement, 'Alice');
    setValue(chat as HTMLTextAreaElement, 'customer asks for appointment');

    expect(recognition.recognitionState.customerIdentityInput).toBe('Alice');
    expect(recognition.recognitionState.chatContentInput).toBe('customer asks for appointment');
    app.unmount();
  });

  it('shows the recognized platform, nickname, messages, and customer choices before generating a reply', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:result', {
      sessionId: 'choose-customer',
      response: {
        nickname: '小雨',
        awaitingCustomerSelection: true,
        recognition: {
          platform: 'WECHAT',
          nickname: '小雨',
          messages: [
            { role: 'CUSTOMER', text: '我想了解产后修复' },
            { role: 'EMPLOYEE', text: '好的，我帮您看看' }
          ]
        },
        match: {
          matchType: 'MULTIPLE',
          customers: [
            { customerId: 42, nickname: '小雨', leadType: 'XIAN_SUO', intendedStore: '上海门店' },
            { customerId: 43, nickname: '小雨', leadType: 'TUAN_GOU', intendedStore: '杭州门店' }
          ]
        }
      } as ChatResponse
    });
    await flushUi();

    expect(host.querySelector('.recognized-chat-result')?.textContent).toContain('WECHAT');
    expect(host.querySelector('.recognized-chat-result')?.textContent).toContain('我想了解产后修复');
    expect(host.querySelectorAll('.recognized-customer-choice')).toHaveLength(2);
    expect(host.textContent).not.toContain('推荐回复');
    app.unmount();
  });

  it('continues a waiting recognition only after the employee chooses a candidate by customer ID', async () => {
    const { app, host, eventBus } = await mountPanel();
    mocks.postJson.mockResolvedValue({ success: true, data: response('13800000000', [suggestion('已根据选择生成回复')]) });

    eventBus.emit('recognize:result', {
      sessionId: 'choose-customer-request',
      response: {
        nickname: '小雨',
        awaitingCustomerSelection: true,
        recognition: { platform: 'WECHAT', nickname: '小雨', messages: [] },
        match: {
          matchType: 'MULTIPLE',
          customers: [{ customerId: 42, nickname: '小雨', leadType: 'XIAN_SUO' }]
        }
      } as ChatResponse
    });
    eventBus.emit('recognize:job', {
      sessionId: 'choose-customer-request',
      jobId: 'job-42',
      status: 'READY'
    });
    await flushUi();

    (host.querySelector('.recognized-customer-choice') as HTMLButtonElement).click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith(
      '/api/v1/chat/recognition-jobs/job-42/select-customer',
      { customerId: 42 },
      1000
    );
    expect(host.textContent).toContain('已根据选择生成回复');
    app.unmount();
  });
});

function setValue(element: HTMLInputElement | HTMLTextAreaElement, value: string): void {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

function response(phone: string, suggestions: ReplySuggestion[]): ChatResponse {
  return {
    phone,
    nickname: 'Alice',
    match: { matchType: 'EXACT' },
    skill: { suggestions },
    replySource: suggestions[0]?.direction === 'SYSTEM_FALLBACK'
      ? { source: 'FALLBACK', label: '系统兜底', detail: 'fallback' }
      : { source: 'SKILL', label: 'Skill 生成', detail: 'skill' }
  };
}

function suggestion(text: string): ReplySuggestion {
  return {
    text,
    direction: 'NEXT_STEP',
    reason: 'reason'
  };
}

function profileSuggestion(suggestionId: number, fieldName: string, currentValue: unknown, suggestedValue: unknown): ProfileSuggestion {
  return {
    suggestionId,
    fieldName,
    currentValue,
    suggestedValue,
    reason: 'AI'
  };
}

function alert(alertId: string, phone: string, acknowledged: boolean): AbnormalAlertPayload {
  return {
    alertId,
    phone,
    alertType: 'CHURN_RISK',
    message: 'High churn risk',
    level: 'WARN',
    occurredAt: '2026-07-03T12:00:00Z',
    acknowledged
  };
}
