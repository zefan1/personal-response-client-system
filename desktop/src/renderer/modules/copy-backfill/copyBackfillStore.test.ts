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
  });

  afterEach(async () => {
    const store = await import('./copyBackfillStore');
    store.cleanupCopyBackfillStore();
    vi.useRealTimers();
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

  it('copies selected reply text, sends confirmation, and refreshes the active profile', async () => {
    const store = await freshStore();
    const { eventBus } = await import('../../shared/eventBus');
    const confirmed: unknown[] = [];
    eventBus.on('reply:send-confirmed', (payload) => confirmed.push(payload));
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockResolvedValue({ success: true, data: {} });

    await store.handleReplySelected(reply({ text: 'hello', direction: 'NEXT_STEP', isFallback: true }));
    await vi.runAllTimersAsync();

    expect(writeClipboardTextMock).toHaveBeenCalledWith('hello');
    expect(store.copyBackfillState.toast).toBe('已复制并记录发送，档案正在刷新');
    expect(confirmed).toEqual([{ phone: '18800001111' }]);
    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/send-confirm', {
      phone: '18800001111',
      conversationSummary: '',
      isNewCustomer: false,
      sentText: 'hello',
      selectedDirection: 'SYSTEM_FALLBACK'
    }, undefined, expect.any(AbortSignal));
  });

  it('does not send confirmation when clipboard write fails or phone is missing', async () => {
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

  it('keeps copied text usable when send-confirm fails and surfaces the degraded state', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockResolvedValue({ success: false, errorCode: 'BAD_REQUEST', message: 'phone and sentText are required' });

    await store.handleReplySelected(reply({ text: 'hello' }));
    await vi.runAllTimersAsync();

    expect(writeClipboardTextMock).toHaveBeenCalledWith('hello');
    expect(store.copyBackfillState.toast).toBe('已复制，但发送记录失败，请稍后刷新档案确认');
  });

  it('uses the full phone for send-confirm even when a masked display phone is present', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    postJsonMock.mockResolvedValue({ success: true, data: {} });

    await store.handleReplySelected(reply({ phone: '18800001111', displayPhone: '****1111' }));
    await vi.runAllTimersAsync();

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/send-confirm', expect.objectContaining({
      phone: '18800001111'
    }), undefined, expect.any(AbortSignal));
  });

  it('aborts the previous pending send-confirm when a newer reply is selected', async () => {
    const store = await freshStore();
    writeClipboardTextMock.mockResolvedValue({ success: true });
    const signals: AbortSignal[] = [];
    postJsonMock.mockImplementation((_path, _body, _timeout, signal: AbortSignal) => {
      signals.push(signal);
      return new Promise(() => undefined);
    });

    await store.handleReplySelected(reply({ text: 'first' }));
    await store.handleReplySelected(reply({ text: 'second' }));

    expect(signals).toHaveLength(2);
    expect(signals[0].aborted).toBe(true);
    expect(signals[1].aborted).toBe(false);
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
    isFallback: false,
    ...patch
  };
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
