import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ProfileSuggestion, ReplySelectedPayload } from './types';

const postJsonMock = vi.fn();
const writeClipboardTextMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  postJson: postJsonMock
}));

vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: writeClipboardTextMock
}));

type CopyBackfillModule = typeof import('./copyBackfillStore');

function installMemoryLocalStorage(): void {
  const store = new Map<string, string>();
  const storage = {
    getItem: vi.fn((key: string) => store.get(key) ?? null),
    setItem: vi.fn((key: string, value: string) => {
      store.set(key, String(value));
    }),
    removeItem: vi.fn((key: string) => {
      store.delete(key);
    }),
    clear: vi.fn(() => {
      store.clear();
    })
  };
  Object.defineProperty(globalThis, 'localStorage', {
    value: storage,
    configurable: true
  });
}

installMemoryLocalStorage();

async function freshStore(): Promise<CopyBackfillModule> {
  vi.resetModules();
  postJsonMock.mockReset();
  writeClipboardTextMock.mockReset();
  return await import('./copyBackfillStore');
}

describe('copyBackfillStore', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    localStorage.clear();
  });

  afterEach(async () => {
    const store = await import('./copyBackfillStore');
    store.cleanupCopyBackfillStore();
    vi.useRealTimers();
    localStorage.clear();
    postJsonMock.mockReset();
    writeClipboardTextMock.mockReset();
  });

  it('rejects empty selected replies before touching clipboard or backend', async () => {
    const store = await freshStore();

    await store.handleReplySelected(reply({ text: '   ' }));

    expect(writeClipboardTextMock).not.toHaveBeenCalled();
    expect(postJsonMock).not.toHaveBeenCalled();
    expect(store.copyBackfillState.toast).toBeTruthy();
  });

  it('opens a persisted send decision after copy without confirming that it was sent', async () => {
    const store = await freshStore();
    const { eventBus } = await import('../../shared/eventBus');
    const confirmed: unknown[] = [];
    eventBus.on('reply:send-confirmed', (payload) => confirmed.push(payload));
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockResolvedValue({ success: true, data: {} });

    await store.handleReplySelected(reply({
      text: 'hello',
      direction: 'NEXT_STEP',
      isFallback: true,
      replySource: 'FALLBACK'
    }));
    await vi.runAllTimersAsync();

    expect(writeClipboardTextMock).toHaveBeenCalledWith('hello');
    expect(confirmed).toEqual([]);
    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/ai-usage', {
      phone: '18800001111',
      taskId: 'task-1',
      replySessionId: 'reply-session-1',
      replySource: 'FALLBACK',
      copiedText: 'hello'
    }, undefined, expect.any(AbortSignal));
    expect(postJsonMock.mock.calls.map(([path]) => path)).not.toContain('/api/v1/chat/send-confirm');
    expect(store.copyBackfillState.pendingSendDecision).toMatchObject({
      text: 'hello',
      phone: '18800001111',
      direction: 'NEXT_STEP',
      replySessionId: 'reply-session-1',
      status: 'AWAITING_DECISION'
    });
    expect(JSON.parse(localStorage.getItem('copy_backfill_pending_send') ?? 'null')).toMatchObject({
      text: 'hello',
      phone: '18800001111',
      status: 'AWAITING_DECISION',
      confirmationId: expect.any(String)
    });
  });

  it('unlocks without updating when the employee chooses not sent', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });

    await store.handleReplySelected(reply({ text: 'not sent' }));
    store.discardPendingSendDecision();

    expect(store.copyBackfillState.pendingSendDecision).toBeNull();
    expect(localStorage.getItem('copy_backfill_pending_send')).toBeNull();
    expect(postJsonMock.mock.calls.map(([path]) => path)).not.toContain('/api/v1/chat/send-confirm');
  });

  it('unlocks only after send confirmation is accepted', async () => {
    const store = await freshStore();
    const { eventBus } = await import('../../shared/eventBus');
    const confirmed: unknown[] = [];
    eventBus.on('reply:send-confirmed', (payload) => confirmed.push(payload));
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockImplementation(async (path: string) => path === '/api/v1/chat/send-confirm'
      ? { success: true, data: { accepted: true } }
      : { success: true, data: {} });
    await store.handleReplySelected(reply({ text: 'sent reply', nickname: 'Alice' }));
    const confirmationId = store.copyBackfillState.pendingSendDecision?.confirmationId;

    const accepted = await store.confirmPendingSendDecision();

    expect(accepted).toBe(true);
    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/send-confirm', {
      confirmationId,
      customerId: 7,
      phone: '18800001111',
      nickname: 'Alice',
      conversationSummary: '',
      isNewCustomer: false,
      sentText: 'sent reply',
      selectedDirection: 'NEXT_STEP'
    });
    expect(store.copyBackfillState.pendingSendDecision).toBeNull();
    expect(localStorage.getItem('copy_backfill_pending_send')).toBeNull();
    expect(confirmed).toEqual([{ phone: '18800001111', customerId: 7 }]);
  });

  it('keeps the gate locked when send confirmation fails so it can be retried', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockImplementation(async (path: string) => path === '/api/v1/chat/send-confirm'
      ? { success: false, errorCode: 'NETWORK_ERROR', message: 'offline', data: null }
      : { success: true, data: {} });
    await store.handleReplySelected(reply({ text: 'retry me' }));

    const accepted = await store.confirmPendingSendDecision();

    expect(accepted).toBe(false);
    expect(store.copyBackfillState.pendingSendDecision).toMatchObject({
      text: 'retry me',
      status: 'SUBMIT_FAILED',
      errorMessage: 'offline'
    });
    expect(localStorage.getItem('copy_backfill_pending_send')).not.toBeNull();
  });

  it('does not submit a send confirmation without the customer created during recognition', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    await store.handleReplySelected(reply({ customerId: null, phone: '', nickname: 'Only nickname' }));

    const accepted = await store.confirmPendingSendDecision();

    expect(accepted).toBe(false);
    expect(postJsonMock.mock.calls.map(([path]) => path)).not.toContain('/api/v1/chat/send-confirm');
    expect(store.copyBackfillState.pendingSendDecision).toMatchObject({
      phone: '',
      status: 'AWAITING_DECISION',
      errorMessage: '当前回复没有对应的客户档案，请重新识别聊天'
    });
  });

  it('restores an interrupted pending decision after restart as retryable', async () => {
    localStorage.setItem('copy_backfill_pending_send', JSON.stringify({
      confirmationId: 'confirm-restart-1',
      text: 'already copied',
      direction: 'NEXT_STEP',
      reason: 'reason',
      phone: '18800001111',
      nickname: 'Alice',
      replySessionId: 'reply-session-restart',
      isFallback: false,
      status: 'SUBMITTING',
      createdAt: '2026-07-03T11:59:00.000Z',
      errorMessage: ''
    }));

    const store = await freshStore();

    expect(store.copyBackfillState.pendingSendDecision).toMatchObject({
      confirmationId: 'confirm-restart-1',
      text: 'already copied',
      status: 'AWAITING_DECISION',
      errorMessage: ''
    });
  });

  it('does not record AI usage when clipboard write fails or phone is missing', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValueOnce({ success: false, error: 'denied' });

    await store.handleReplySelected(reply({ text: 'hello' }));

    expect(postJsonMock).not.toHaveBeenCalled();
    expect(store.copyBackfillState.toast).toBeTruthy();

    writeClipboardTextMock.mockResolvedValueOnce({ success: true });
    await store.handleReplySelected(reply({ text: 'copied only', phone: '' }));

    expect(writeClipboardTextMock).toHaveBeenCalledWith('copied only');
    expect(postJsonMock).not.toHaveBeenCalled();
    expect(store.copyBackfillState.toast).toBe('已复制到剪贴板，请粘贴到微信发送');
  });

  it('keeps copied text usable when AI usage recording fails and surfaces the degraded state', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockResolvedValue({ success: false, errorCode: 'BAD_REQUEST', message: 'phone and sentText are required' });

    await store.handleReplySelected(reply({ text: 'hello' }));
    await vi.runAllTimersAsync();

    expect(writeClipboardTextMock).toHaveBeenCalledWith('hello');
    expect(store.copyBackfillState.toast).toBe('已复制，但 AI 使用记录未同步，不影响正常跟进');
  });

  it('uses the recognized phone for AI usage', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockResolvedValue({ success: true, data: {} });

    await store.handleReplySelected(reply({ phone: '18800001111' }));
    await vi.runAllTimersAsync();

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/ai-usage', expect.objectContaining({
      phone: '18800001111'
    }), undefined, expect.any(AbortSignal));
  });

  it('does not cancel in-flight AI usage recording when the component unmounts', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    const signals: AbortSignal[] = [];
    postJsonMock.mockImplementation((_path, _body, _timeout, signal: AbortSignal) => {
      signals.push(signal);
      return new Promise(() => undefined);
    });

    await store.handleReplySelected(reply({ text: 'first' }));
    store.cleanupCopyBackfillStore();

    expect(postJsonMock.mock.calls.map(([path]) => path)).toEqual(['/api/v1/chat/ai-usage']);
    expect(signals).toHaveLength(1);
    expect(signals[0].aborted).toBe(false);
  });

  it('does not let a second copied reply overwrite the unresolved send decision', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });

    await store.handleReplySelected(reply({ text: 'first pending reply' }));
    const confirmationId = store.copyBackfillState.pendingSendDecision?.confirmationId;
    await store.handleReplySelected(reply({ text: 'second reply must wait' }));

    expect(writeClipboardTextMock).toHaveBeenCalledTimes(1);
    expect(store.copyBackfillState.pendingSendDecision).toMatchObject({
      confirmationId,
      text: 'first pending reply'
    });
  });

  it('keeps a pending send decision locked across component unmount and remount', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });

    await store.handleReplySelected(reply({ text: 'still waiting for confirmation' }));
    const confirmationId = store.copyBackfillState.pendingSendDecision?.confirmationId;

    store.cleanupCopyBackfillStore();

    expect(store.copyBackfillState.pendingSendDecision).toMatchObject({
      confirmationId,
      text: 'still waiting for confirmation',
      status: 'AWAITING_DECISION'
    });
    expect(localStorage.getItem('copy_backfill_pending_send')).not.toBeNull();
  });

  it('stores incoming suggestions collapsed until the inline panel expands them', async () => {
    const store = await freshStore();

    store.handleSuggestionShow({ phone: '18800001111', suggestions: [suggestion(1), suggestion(2, { resolved: true })] });

    expect(store.copyBackfillState.suggestionToastVisible).toBe(false);
    expect(store.copyBackfillState.suggestionToastCollapsed).toBe(true);
    expect(store.copyBackfillState.suggestionToastSuggestions.map((item) => item.resolved)).toEqual([false, true]);

    store.reopenSuggestionToast();
    expect(store.copyBackfillState.suggestionToastVisible).toBe(true);
    store.closeSuggestionToast();
    expect(store.copyBackfillState.suggestionToastVisible).toBe(false);
    expect(store.copyBackfillState.suggestionToastCollapsed).toBe(true);
  });

  it('resolves a single inline suggestion and keeps the expanded panel open when unresolved suggestions remain', async () => {
    const store = await freshStore();
    postJsonMock.mockResolvedValue({ success: true, data: {} });
    store.handleSuggestionShow({ phone: '18800001111', suggestions: [suggestion(1), suggestion(2)] });
    store.reopenSuggestionToast();

    await store.resolveToastSuggestion('CONFIRM', store.copyBackfillState.suggestionToastSuggestions[0]);

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111/suggestions/batch-resolve', {
      action: 'CONFIRM',
      suggestionIds: [1],
      operator: 'desktop'
    });
    expect(store.copyBackfillState.suggestionToastSuggestions[0]).toMatchObject({
      resolved: true,
      resolving: false,
      resolveAction: 'CONFIRM'
    });
    expect(store.copyBackfillState.suggestionToastVisible).toBe(true);
  });

  it('resolves all remaining toast suggestions and hides the toast when complete', async () => {
    const store = await freshStore();
    postJsonMock.mockResolvedValue({ success: true, data: {} });
    store.handleSuggestionShow({ phone: '18800001111', suggestions: [suggestion(1), suggestion(2)] });
    store.reopenSuggestionToast();

    await store.resolveToastSuggestion('REJECT');

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111/suggestions/batch-resolve', {
      action: 'REJECT',
      suggestionIds: [1, 2],
      operator: 'desktop'
    });
    expect(store.copyBackfillState.suggestionToastVisible).toBe(false);
    expect(store.copyBackfillState.suggestionToastCollapsed).toBe(false);
  });

  it('restores resolving state after inline suggestion resolve failure', async () => {
    const store = await freshStore();
    postJsonMock.mockRejectedValue(new Error('network down'));
    store.handleSuggestionShow({ phone: '18800001111', suggestions: [suggestion(1)] });
    store.reopenSuggestionToast();

    await store.resolveToastSuggestion('CONFIRM');

    expect(store.copyBackfillState.suggestionToastSuggestions[0].resolving).toBe(false);
    expect(store.copyBackfillState.toast).toBeTruthy();
    expect(store.copyBackfillState.suggestionToastVisible).toBe(true);
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

function suggestion(suggestionId: number, patch: Partial<ProfileSuggestion> = {}): ProfileSuggestion {
  return {
    suggestionId,
    fieldName: 'nickname',
    currentValue: 'Old',
    suggestedValue: `New ${suggestionId}`,
    reason: 'AI',
    ...patch
  };
}
