import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { AbnormalAlertPayload, ChatResponse, ProfileSuggestion, ReplySuggestion } from './types';

const postJsonMock = vi.fn();
const getAlertsByPhoneMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  postJson: postJsonMock
}));

vi.mock('../abnormal-alert/alertStore', () => ({
  getAlertsByPhone: getAlertsByPhoneMock
}));

type ReplyModule = typeof import('./replySuggestionStore');
type EventBusModule = typeof import('../../shared/eventBus');

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

async function freshStore(): Promise<{ replies: ReplyModule; eventBus: EventBusModule['eventBus'] }> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    requestTotalTimeoutMs: 1000,
    fallbackRetryIntervalMs: 100,
    fallbackMaxRetries: 2
  }));
  postJsonMock.mockReset();
  getAlertsByPhoneMock.mockReset();
  getAlertsByPhoneMock.mockReturnValue([]);
  const replies = await import('./replySuggestionStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { replies, eventBus };
}

describe('replySuggestionStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
  });

  afterEach(async () => {
    const replies = await import('./replySuggestionStore');
    replies.cleanupReplySuggestionStore();
    vi.useRealTimers();
    localStorage.clear();
    postJsonMock.mockReset();
    getAlertsByPhoneMock.mockReset();
  });

  it('advances recognize skeleton stages and pauses for multiple customer matches', async () => {
    const { replies } = await freshStore();

    replies.startRecognizeLoading();
    expect(replies.replySuggestionState.loadingMode).toBe('FULL');
    expect(replies.replySuggestionState.currentStageIndex).toBe(0);

    vi.advanceTimersByTime(5000);
    expect(replies.replySuggestionState.currentStageIndex).toBe(1);
    vi.advanceTimersByTime(2500);
    expect(replies.replySuggestionState.currentStageIndex).toBe(2);

    replies.pauseForMultipleMatch();
    vi.advanceTimersByTime(7500);
    expect(replies.replySuggestionState.currentStageIndex).toBe(2);

    replies.stopForTimeout();
    expect(replies.replySuggestionState.loadingMode).toBe('NONE');
    replies.stopForImageFailure();
    expect(replies.replySuggestionState.suggestions).toEqual([]);
  });

  it('renders recognize results, refreshes current abnormal alert, and emits masked selected replies', async () => {
    const { replies, eventBus } = await freshStore();
    const selected: unknown[] = [];
    eventBus.on('reply:selected', (payload) => selected.push(payload));
    getAlertsByPhoneMock.mockReturnValue([alert('alert-a')]);

    replies.showRecognizeResult({ response: response('18800001111', [suggestion('Use this')]) });

    expect(replies.replySuggestionState.loadingMode).toBe('NONE');
    expect(replies.replySuggestionState.currentPhone).toBe('18800001111');
    expect(replies.replySuggestionState.currentNickname).toBe('Alice');
    expect(replies.replySuggestionState.currentMatchType).toBe('EXACT');
    expect(replies.replySuggestionState.abnormalAlert).toMatchObject({ alertId: 'alert-a' });

    replies.selectReply(replies.replySuggestionState.suggestions[0]);
    expect(selected).toEqual([{
      text: 'Use this',
      direction: 'NEXT_STEP',
      reason: 'reason',
      phone: '****1111',
      isFallback: false
    }]);
  });

  it('enters fallback mode on empty Skill output and automatically recovers with retry', async () => {
    const { replies } = await freshStore();
    replies.showRecognizeResult(response('18800001111', []));

    expect(replies.replySuggestionState.isFallbackMode).toBe(true);
    expect(replies.replySuggestionState.showRegenerateButton).toBe(false);
    expect(replies.replySuggestionState.suggestions[0].direction).toBe('SYSTEM_FALLBACK');

    postJsonMock.mockResolvedValue({
      success: true,
      data: response('18800001111', [suggestion('Recovered')])
    });

    await vi.advanceTimersByTimeAsync(100);

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/regenerate', {
      phone: '18800001111',
      leadType: '',
      scene: 'REGENERATE',
      previousSuggestions: []
    }, 1000);
    expect(replies.replySuggestionState.isFallbackMode).toBe(false);
    expect(replies.replySuggestionState.suggestions.map((item) => item.text)).toEqual(['Recovered']);
  });

  it('stops fallback retry and exposes manual regenerate after configured automatic retries fail', async () => {
    const { replies } = await freshStore();
    replies.showRecognizeResult(response('18800001111', [{ text: 'fallback', direction: 'SYSTEM_FALLBACK', reason: 'down' }]));
    postJsonMock.mockResolvedValue({ success: false, errorCode: '50-99999' });

    await vi.advanceTimersByTimeAsync(100);
    await vi.advanceTimersByTimeAsync(100);
    await vi.advanceTimersByTimeAsync(100);

    expect(replies.replySuggestionState.fallbackRetryCount).toBe(2);
    expect(replies.replySuggestionState.isFallbackMode).toBe(true);
    expect(replies.replySuggestionState.showRegenerateButton).toBe(true);
    expect(postJsonMock).toHaveBeenCalledTimes(2);
  });

  it('regenerates manually with previous suggestions and shows help hint after repeated manual changes', async () => {
    const { replies } = await freshStore();
    replies.showRecognizeResult(response('18800001111', [suggestion('Original')]));
    postJsonMock
      .mockResolvedValueOnce({ success: true, data: response('18800001111', [suggestion('New 1')]) })
      .mockResolvedValueOnce({ success: true, data: response('18800001111', [suggestion('New 2')]) })
      .mockResolvedValueOnce({ success: true, data: response('18800001111', [suggestion('New 3')]) });

    await replies.regenerateReplies();
    await replies.regenerateReplies();
    await replies.regenerateReplies();

    expect(postJsonMock).toHaveBeenNthCalledWith(1, '/api/v1/chat/regenerate', {
      phone: '18800001111',
      leadType: '',
      scene: 'REGENERATE',
      previousSuggestions: ['Original']
    }, 1000);
    expect(replies.replySuggestionState.regenerateCount).toBe(3);
    expect(replies.replySuggestionState.showHelpHint).toBe(true);
    expect(replies.replySuggestionState.suggestions.map((item) => item.text)).toEqual(['New 3']);
  });

  it('maps manual regenerate failures and missing customer state into user-visible toasts', async () => {
    const { replies } = await freshStore();

    await replies.regenerateReplies();
    expect(replies.replySuggestionState.toast).toBeTruthy();

    replies.startGenerateLoading({ phone: '18800001111', leadType: 'TUAN_GOU', scene: 'ACTIVE_REPLY' });
    postJsonMock.mockResolvedValue({ success: false, errorCode: '80-10002' });

    await replies.regenerateReplies();

    expect(replies.replySuggestionState.loadingMode).toBe('NONE');
    expect(replies.replySuggestionState.regenerating).toBe(false);
    expect(replies.replySuggestionState.toast).toBeTruthy();
  });

  it('requests leader help once, tracks pending/resolved/timeout states, and ignores other phones', async () => {
    const { replies, eventBus } = await freshStore();
    const helpRequests: unknown[] = [];
    eventBus.on('help:request', (payload) => helpRequests.push(payload));
    replies.showRecognizeResult(response('18800001111', [suggestion('Ask help')]));

    replies.requestLeaderHelp();

    expect(helpRequests).toEqual([{
      phone: '18800001111',
      clientMessage: '',
      aiSuggestions: [{ text: 'Ask help', direction: 'NEXT_STEP' }]
    }]);

    replies.handleHelpPending({ phone: '18800002222', helpId: 'ignored' });
    expect(replies.replySuggestionState.activeHelpId).toBe('');

    replies.handleHelpPending({ phone: '18800001111', helpId: 'help-a' });
    replies.requestLeaderHelp();
    expect(helpRequests).toHaveLength(1);
    expect(replies.replySuggestionState.activeHelpId).toBe('help-a');

    replies.handleHelpResolved({ phone: '18800001111', helpId: 'help-a' });
    expect(replies.replySuggestionState.activeHelpId).toBe('');

    replies.handleHelpTimeout({ phone: '18800001111' });
    expect(replies.replySuggestionState.showHelpHint).toBe(true);
  });

  it('handles profile suggestions, resolves them in batch, and keeps unresolved items expanded on failure', async () => {
    const { replies, eventBus } = await freshStore();
    const suggestionEvents: unknown[] = [];
    eventBus.on('suggestion:show', (payload) => suggestionEvents.push(payload));
    replies.showRecognizeResult(response('18800001111', [suggestion('Reply')]));

    replies.handleProfileSuggestions({
      phone: '18800001111',
      suggestions: [profileSuggestion(1), profileSuggestion(2)]
    });

    expect(replies.pendingProfileSuggestionCount.value).toBe(2);
    expect(suggestionEvents).toHaveLength(1);

    postJsonMock.mockResolvedValueOnce({ success: true, data: {} });
    await replies.resolveProfileSuggestion('CONFIRM', replies.replySuggestionState.profileSuggestions[0]);

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/customers/18800001111/suggestions/batch-resolve', {
      action: 'CONFIRM',
      suggestionIds: [1],
      operator: 'desktop'
    });
    expect(replies.pendingProfileSuggestionCount.value).toBe(1);

    postJsonMock.mockRejectedValueOnce(new Error('network down'));
    await replies.resolveProfileSuggestion('REJECT');

    expect(replies.replySuggestionState.profileSuggestions[1].resolving).toBe(false);
    expect(replies.replySuggestionState.profileSuggestionsExpanded).toBe(true);
    expect(replies.replySuggestionState.toast).toBeTruthy();
  });

  it('applies abnormal alerts only for the current phone and clears acknowledged alerts', async () => {
    const { replies } = await freshStore();
    replies.showRecognizeResult(response('18800001111', [suggestion('Reply')]));

    replies.handleAbnormalAlert(alert('other', '18800002222'));
    expect(replies.replySuggestionState.abnormalAlert).toBeNull();

    replies.handleAbnormalAlert(alert('current', '18800001111'));
    expect(replies.replySuggestionState.abnormalAlert?.alertId).toBe('current');

    replies.handleAbnormalAlert({ ...alert('current', '18800001111'), acknowledged: true });
    expect(replies.replySuggestionState.abnormalAlert).toBeNull();
  });
});

function response(phone: string, suggestions: ReplySuggestion[]): ChatResponse {
  return {
    phone,
    nickname: 'Alice',
    match: { matchType: 'EXACT' },
    skill: { suggestions }
  };
}

function suggestion(text: string): ReplySuggestion {
  return {
    text,
    direction: 'NEXT_STEP',
    reason: 'reason'
  };
}

function profileSuggestion(suggestionId: number): ProfileSuggestion {
  return {
    suggestionId,
    fieldName: 'nickname',
    currentValue: 'Old',
    suggestedValue: `New ${suggestionId}`,
    reason: 'AI'
  };
}

function alert(alertId: string, phone = '18800001111'): AbnormalAlertPayload {
  return {
    alertId,
    phone,
    alertType: 'CHURN_RISK',
    message: 'risk',
    level: 'WARN',
    occurredAt: '2026-07-03T12:00:00Z',
    acknowledged: false
  };
}
