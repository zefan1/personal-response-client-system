import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ProfileSuggestion, ReplySelectedPayload } from './types';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  writeClipboardText: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: mocks.getJson,
  postJson: mocks.postJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: mocks.writeClipboardText
}));

type MountedAgent = {
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

installMemoryLocalStorage();

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountAgent(): Promise<MountedAgent> {
  vi.resetModules();
  const [{ default: CopyBackfillAgent }, { eventBus }] = await Promise.all([
    import('./CopyBackfillAgent.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(CopyBackfillAgent);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

describe('CopyBackfillAgent', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    mocks.writeClipboardText.mockResolvedValue({ success: true });
    mocks.postJson.mockResolvedValue({ success: true, data: {} });
    localStorage.clear();
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    Object.values(mocks).forEach((mock) => mock.mockReset());
  });

  it('copies selected replies without treating the reply as sent', async () => {
    const { app, host, eventBus } = await mountAgent();
    const confirmed: unknown[] = [];
    eventBus.on('reply:send-confirmed', (payload) => confirmed.push(payload));

    eventBus.emit('reply:selected', reply({ text: 'Use this reply', direction: 'NEXT_STEP' }));
    await flushUi();
    await flushUi();

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('Use this reply');
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/chat/send-pending', expect.objectContaining({ copiedText: 'Use this reply' }));
    expect(mocks.postJson.mock.calls.map(([path]) => path)).not.toContain('/api/v1/chat/ai-usage');
    expect(mocks.postJson.mock.calls.map(([path]) => path)).not.toContain('/api/v1/chat/send-confirm');
    expect(confirmed).toEqual([]);
    app.unmount();
  });

  it('blocks the whole interface after copy and unlocks immediately when not sent is chosen', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('reply:selected', reply({ text: 'Please send this reply' }));
    await flushUi();

    const gate = host.querySelector<HTMLElement>('.send-confirm-gate');
    expect(gate).toBeTruthy();
    expect(gate?.getAttribute('role')).toBe('dialog');
    expect(gate?.getAttribute('aria-modal')).toBe('true');
    expect(gate?.textContent).toContain('Please send this reply');
    const notSentButton = Array.from(host.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('未发送'));
    expect(notSentButton).toBeTruthy();

    notSentButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushUi();

    expect(host.querySelector('.send-confirm-gate')).toBeFalsy();
    expect(mocks.postJson.mock.calls.map(([path]) => path)).not.toContain('/api/v1/chat/send-confirm');
    app.unmount();
  });

  it('unlocks after confirmed-send is accepted by the backend', async () => {
    mocks.postJson.mockImplementation(async (path: string) => path === '/api/v1/chat/send-confirm'
      ? { success: true, data: { accepted: true } }
      : { success: true, data: {} });
    const { app, host, eventBus } = await mountAgent();
    eventBus.emit('reply:selected', reply({ text: 'Actually sent' }));
    await flushUi();

    const confirmButton = Array.from(host.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('已发送'));
    confirmButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushUi();

    expect(mocks.postJson.mock.calls.map(([path]) => path)).toContain('/api/v1/chat/send-confirm');
    expect(host.querySelector('.send-confirm-gate')).toBeFalsy();
    app.unmount();
  });

  it('keeps the full-screen gate visible when confirmed-send submission fails', async () => {
    mocks.postJson.mockImplementation(async (path: string) => path === '/api/v1/chat/send-confirm'
      ? { success: false, data: null, message: '服务暂不可用' }
      : { success: true, data: {} });
    const { app, host, eventBus } = await mountAgent();
    eventBus.emit('reply:selected', reply({ text: 'Retry this confirmation' }));
    await flushUi();

    const confirmButton = Array.from(host.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('已发送'));
    confirmButton?.dispatchEvent(new MouseEvent('click', { bubbles: true }));
    await flushUi();

    expect(host.querySelector('.send-confirm-gate')).toBeTruthy();
    expect(host.textContent).toContain('服务暂不可用');
    expect(host.textContent).toContain('重试');
    app.unmount();
  });

  it('keeps polling recognition jobs submitted before the send-confirm gate opens', async () => {
    mocks.postJson.mockImplementation(async (path: string) => {
      if (path === '/api/v1/chat/recognition-jobs') {
        return {
          success: true,
          data: {
            jobId: 'job-before-gate',
            replySessionId: 'server-session',
            status: 'QUEUED',
            errorCode: null,
            response: null
          }
        };
      }
      return { success: true, data: {} };
    });
    mocks.getJson.mockResolvedValue({
      success: true,
      data: {
        jobId: 'job-before-gate',
        replySessionId: 'server-session',
        status: 'READY',
        errorCode: null,
        response: {
          phone: '18800002222',
          nickname: 'Background result',
          skill: { suggestions: [{ text: 'done', direction: 'NEXT_STEP', reason: 'reason' }] }
        }
      }
    });
    const { app, host, eventBus } = await mountAgent();
    const recognition = await import('../chat-recognition/recognitionStore');
    const results: unknown[] = [];
    eventBus.on('recognize:result', (payload) => results.push(payload));

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'queued-image' });
    eventBus.emit('reply:selected', reply({ text: 'Gate from another reply' }));
    await flushUi();
    expect(host.querySelector('.send-confirm-gate')).toBeTruthy();

    await vi.advanceTimersByTimeAsync(1000);
    await flushUi();

    expect(mocks.getJson).toHaveBeenCalledWith('/api/v1/chat/recognition-jobs/job-before-gate', 5000);
    expect(results).toEqual([expect.objectContaining({
      source: 'BUTTON_CLICK',
      response: expect.objectContaining({ nickname: 'Background result' })
    })]);
    expect(host.querySelector('.send-confirm-gate')).toBeTruthy();
    app.unmount();
  });

  it('keeps profile suggestions out of the global floating layer', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('suggestion:show', {
      phone: '18800001111',
      suggestions: [suggestion(1, 'nickname'), suggestion(2, 'intentLevel')]
    });
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();
    expect(host.querySelector('.suggestion-reopen')).toBeFalsy();
    expect(host.textContent).not.toContain('AI 更新建议');
    app.unmount();
  });

  it('collapses pending suggestions on recognize-start without rendering a fixed reopen button', async () => {
    const { app, host, eventBus } = await mountAgent();

    eventBus.emit('suggestion:show', {
      phone: '18800002222',
      suggestions: [suggestion(11, 'stage'), suggestion(12, 'store')]
    });
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();

    eventBus.emit('recognize:start', {});
    await flushUi();

    expect(host.querySelector('.suggestion-toast')).toBeFalsy();
    expect(host.querySelector('.suggestion-reopen')).toBeFalsy();
    app.unmount();
  });
});

function reply(patch: Partial<ReplySelectedPayload>): ReplySelectedPayload {
  return {
    text: 'hello',
    direction: 'NEXT_STEP',
    reason: 'reason',
    phone: '18800001111',
    customerId: 7,
    taskId: 'task-1',
    replySessionId: 'reply-session-1',
    replySource: 'SKILL',
    isFallback: false,
    ...patch
  } as ReplySelectedPayload;
}

function suggestion(suggestionId: number, fieldName: string): ProfileSuggestion {
  return {
    suggestionId,
    fieldName,
    currentValue: 'Old',
    suggestedValue: `New ${suggestionId}`,
    reason: 'AI'
  };
}
