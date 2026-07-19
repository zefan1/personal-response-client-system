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

async function mountPanel(): Promise<MountedPanel> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    requestTotalTimeoutMs: 1000,
    fallbackRetryIntervalMs: 100,
    fallbackMaxRetries: 2
  }));
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

    expect(selected).toEqual([{
      text: 'Ask for budget',
      direction: 'NEXT_STEP',
      reason: 'reason',
      phone: '18800001111',
      displayPhone: '****1111',
      isFallback: false
    }]);

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

  it('keeps multiple customer tasks in the queue and can switch back to a previous reply', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-a', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', { sessionId: 'session-a', response: response('18800001111', [suggestion('First reply')]) });
    await flushUi();

    eventBus.emit('recognize:start', { sessionId: 'session-b', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', { sessionId: 'session-b', response: response('18800002222', [suggestion('Second reply')]) });
    await flushUi();

    expect(host.querySelectorAll('.reply-task-row')).toHaveLength(1);
    expect(host.textContent).toContain('待处理队列');
    expect(host.textContent).toContain('可复制');
    expect(host.querySelector('.reply-task-time')?.textContent).toBe('刚刚');
    expect(host.textContent).toContain('Second reply');

    (host.querySelector('.reply-task-row') as HTMLElement | undefined)?.click();
    await flushUi();

    expect(host.textContent).toContain('First reply');
    app.unmount();
  });

  it('keeps the full reply workflow while promoting the first suggestion above the task queue', async () => {
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
    const queueIndex = panelChildren.findIndex((item) => item.classList.contains('reply-task-queue'));
    expect(primaryIndex).toBeGreaterThanOrEqual(0);
    expect(moreIndex).toBeGreaterThan(primaryIndex);
    expect(currentTaskIndex).toBeGreaterThan(moreIndex);
    expect(queueIndex).toBeGreaterThan(currentTaskIndex);

    expect(host.querySelector('.reply-primary-card')?.textContent).toContain('Primary reply');
    expect(host.querySelector('.reply-alt-list')?.textContent).toContain('Second reply');
    expect(host.querySelector('.reply-alt-list')?.textContent).toContain('Third reply');
    expect(host.querySelector('.reply-task-queue')?.textContent).toContain('待处理队列');
    expect(host.querySelector('.reply-queue-empty')?.textContent ?? '').toContain('暂无其他待处理任务');

    app.unmount();
  });

  it('shows readable updated times for queue tasks', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-time', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', { sessionId: 'session-time', response: response('18800001111', [suggestion('Timed reply')]) });
    await flushUi();

    expect(host.querySelector('.reply-queue-empty')?.textContent ?? '').toContain('暂无其他待处理任务');

    vi.setSystemTime(new Date('2026-07-03T12:03:00Z'));
    eventBus.emit('recognize:start', { sessionId: 'session-newer', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:result', { sessionId: 'session-newer', response: response('18800002222', [suggestion('Newer reply')]) });
    await flushUi();

    expect(host.querySelector('.reply-task-time')?.textContent).toBe('3 分钟前');
    app.unmount();
  });

  it('lets users choose a candidate directly from a multiple-match queue task', async () => {
    const { app, host, eventBus } = await mountPanel();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));

    eventBus.emit('recognize:start', { sessionId: 'session-multiple', source: 'BUTTON_CLICK' });
    eventBus.emit('recognize:multiple', {
      sessionId: 'session-multiple',
      candidates: [
        { phone: '18800001111', nickname: 'Alice', leadType: 'TUAN_GOU' },
        { phone: '18800002222', nickname: 'Betty', leadType: 'XIAN_SUO' }
      ]
    });
    await flushUi();

    expect(host.querySelector('.reply-current-task.status-multiple')).toBeTruthy();
    expect(host.querySelector('.reply-multiple-state')).toBeTruthy();
    expect(host.textContent).toContain('Alice');
    expect(host.textContent).toContain('Betty');

    (host.querySelector('.reply-candidate-actions button') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(selected).toEqual([{
      sessionId: 'session-multiple',
      phone: '18800001111',
      scene: 'CHAT_RECOGNIZE',
      leadType: 'TUAN_GOU',
      sourceFrom: 'CANDIDATE_LIST'
    }]);
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

    expect(host.querySelectorAll('.reply-task-row')).toHaveLength(0);
    expect(host.querySelector('.reply-queue-empty')?.textContent ?? '').toContain('暂无其他待处理任务');
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

    expect(host.querySelectorAll('.reply-task-row')).toHaveLength(0);
    expect(host.querySelector('.reply-current-task')).toBeFalsy();
    expect(host.querySelector('.reply-empty-state')?.textContent ?? '').toContain('还没有识别当前聊天');

    eventBus.emit('recognize:result', { sessionId: 'session-close', response: response('18800005555', [suggestion('Late reply')]) });
    await flushUi();

    expect(host.textContent).not.toContain('Late reply');
    expect(host.querySelectorAll('.reply-task-row')).toHaveLength(0);
    app.unmount();
  });

  it('shows loading tasks with text-channel and close actions but no retry action', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('recognize:start', { sessionId: 'session-loading', source: 'BUTTON_CLICK' });
    await flushUi();

    const loadingTask = host.querySelector('.reply-current-task.status-loading') as HTMLElement | null;
    expect(loadingTask).toBeTruthy();
    expect(host.querySelector('.reply-queue-empty')?.textContent ?? '').toContain('暂无其他待处理任务');
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
