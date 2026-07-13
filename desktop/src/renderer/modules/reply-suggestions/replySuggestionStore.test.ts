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

    vi.advanceTimersByTime(1200);
    expect(replies.replySuggestionState.currentStageIndex).toBe(1);
    vi.advanceTimersByTime(1800);
    expect(replies.replySuggestionState.currentStageIndex).toBe(2);

    replies.pauseForMultipleMatch();
    vi.advanceTimersByTime(7500);
    expect(replies.replySuggestionState.currentStageIndex).toBe(2);

    replies.stopForTimeout();
    expect(replies.replySuggestionState.loadingMode).toBe('NONE');
    replies.stopForImageFailure();
    expect(replies.replySuggestionState.suggestions).toEqual([]);
  });

  it('renders recognize results, refreshes current abnormal alert, and emits full-phone selected replies', async () => {
    const { replies, eventBus } = await freshStore();
    const selected: unknown[] = [];
    eventBus.on('reply:selected', (payload) => selected.push(payload));
    getAlertsByPhoneMock.mockReturnValue([alert('alert-a')]);

    replies.showRecognizeResult({ response: response('18800001111', [suggestion('Use this')]) });

    expect(replies.replySuggestionState.loadingMode).toBe('NONE');
    expect(replies.replySuggestionState.currentPhone).toBe('18800001111');
    expect(replies.replySuggestionState.currentNickname).toBe('Alice');
    expect(replies.replySuggestionState.currentMatchType).toBe('EXACT');
    expect(replies.replySuggestionState.replySource?.source).toBe('SKILL');
    expect(replies.replySuggestionState.abnormalAlert).toMatchObject({ alertId: 'alert-a' });

    replies.selectReply(replies.replySuggestionState.suggestions[0]);
    expect(selected).toEqual([{
      text: 'Use this',
      direction: 'NEXT_STEP',
      reason: 'reason',
      phone: '18800001111',
      displayPhone: '****1111',
      isFallback: false
    }]);
  });

  it('keeps multiple customer reply sessions and lets the user switch back to older replies', async () => {
    const { replies } = await freshStore();

    replies.startRecognizeLoading({ sessionId: 'session-a', source: 'BUTTON_CLICK' });
    replies.showRecognizeResult({ sessionId: 'session-a', response: response('18800001111', [suggestion('First customer')]) });
    replies.startRecognizeLoading({ sessionId: 'session-b', source: 'BUTTON_CLICK' });
    replies.showRecognizeResult({ sessionId: 'session-b', response: response('18800002222', [suggestion('Second customer')]) });

    expect(replies.replySuggestionState.sessions).toHaveLength(2);
    expect(replies.replySuggestionState.suggestions.map((item) => item.text)).toEqual(['Second customer']);

    replies.activateSession('session-a');

    expect(replies.replySuggestionState.currentPhone).toBe('18800001111');
    expect(replies.replySuggestionState.suggestions.map((item) => item.text)).toEqual(['First customer']);
  });

  it('queues a newer recognition task without interrupting the current loading task', async () => {
    const { replies } = await freshStore();

    replies.startRecognizeLoading({ sessionId: 'session-a', source: 'BUTTON_CLICK' });
    expect(replies.replySuggestionState.activeSessionId).toBe('session-a');

    replies.startRecognizeLoading({ sessionId: 'session-b', source: 'CLIPBOARD_SCREENSHOT' });
    expect(replies.replySuggestionState.sessions.map((session) => session.sessionId)).toEqual(['session-b', 'session-a']);
    expect(replies.replySuggestionState.activeSessionId).toBe('session-a');
    expect(replies.replySuggestionState.currentStageText).toBe('已获取截图');

    replies.showRecognizeResult({ sessionId: 'session-b', response: response('18800002222', [suggestion('Second customer')]) });

    expect(replies.replySuggestionState.activeSessionId).toBe('session-a');
    expect(replies.replySuggestionState.currentPhone).toBe('');

    replies.stopForTimeout({ sessionId: 'session-a', message: 'first timed out' });
    replies.activateSession('session-b');

    expect(replies.replySuggestionState.currentPhone).toBe('18800002222');
    expect(replies.replySuggestionState.suggestions.map((item) => item.text)).toEqual(['Second customer']);
  });

  it('updates the matching queue task when recognition fails by session id', async () => {
    const { replies } = await freshStore();

    replies.startRecognizeLoading({ sessionId: 'session-a', source: 'BUTTON_CLICK' });
    replies.startRecognizeLoading({ sessionId: 'session-b', source: 'BUTTON_CLICK' });
    replies.stopForFailure({
      sessionId: 'session-a',
      errorCode: '30-10001',
      message: '图片识别失败，请使用文字通道后重新生成回复'
    });

    const failed = replies.replySuggestionState.sessions.find((session) => session.sessionId === 'session-a');
    const loading = replies.replySuggestionState.sessions.find((session) => session.sessionId === 'session-b');

    expect(failed?.status).toBe('FAILED');
    expect(failed?.failureReason).toContain('图片识别失败');
    expect(failed?.loadingMode).toBe('NONE');
    expect(loading?.status).toBe('LOADING');
  });

  it('does not recreate a removed task when late async events arrive for the same session id', async () => {
    const { replies } = await freshStore();

    replies.startRecognizeLoading({ sessionId: 'session-removed', source: 'BUTTON_CLICK' });
    replies.closeReplySession('session-removed');

    expect(replies.replySuggestionState.sessions).toHaveLength(0);
    expect(replies.replySuggestionState.activeSessionId).toBe('');

    replies.updateRecognizeProgress({ sessionId: 'session-removed', stage: 'GENERATING', message: 'late progress' });
    replies.stopForFailure({ sessionId: 'session-removed', message: 'late failure' });
    replies.stopForTimeout({ sessionId: 'session-removed', message: 'late timeout' });
    replies.stopForImageFailure({ sessionId: 'session-removed', message: 'late image failure' });
    replies.pauseForMultipleMatch({
      sessionId: 'session-removed',
      candidates: [{ phone: '18800003333', nickname: 'Late candidate' }]
    });
    replies.showRecognizeResult({ sessionId: 'session-removed', response: response('18800003333', [suggestion('Late reply')]) });
    replies.startGenerateLoading({ sessionId: 'session-removed', phone: '18800003333', scene: 'CHAT_RECOGNIZE' });
    replies.startRecognizeLoading({ sessionId: 'session-removed', source: 'BUTTON_CLICK' });

    expect(replies.replySuggestionState.sessions).toHaveLength(0);
    expect(replies.replySuggestionState.loadingMode).toBe('NONE');
    expect(replies.replySuggestionState.failureReason).toBe('');
    expect(replies.replySuggestionState.suggestions).toEqual([]);
  });

  it('clears removed-session tombstones during cleanup so a fresh mount can reuse an id', async () => {
    const { replies } = await freshStore();

    replies.startRecognizeLoading({ sessionId: 'session-reusable', source: 'BUTTON_CLICK' });
    replies.closeReplySession('session-reusable');
    replies.cleanupReplySuggestionStore();
    replies.startRecognizeLoading({ sessionId: 'session-reusable', source: 'BUTTON_CLICK' });

    expect(replies.replySuggestionState.sessions).toHaveLength(1);
    expect(replies.replySuggestionState.activeSessionId).toBe('session-reusable');
  });

  it('reuses the original multiple-match session when a candidate is selected', async () => {
    const { replies } = await freshStore();

    replies.startRecognizeLoading({ sessionId: 'session-a', source: 'BUTTON_CLICK' });
    replies.pauseForMultipleMatch({ sessionId: 'session-a' });
    replies.startGenerateLoading({
      sessionId: 'session-a',
      phone: '18800001111',
      leadType: 'TUAN_GOU',
      scene: 'CHAT_RECOGNIZE',
      sourceFrom: 'CANDIDATE_LIST'
    });

    expect(replies.replySuggestionState.sessions).toHaveLength(1);
    expect(replies.replySuggestionState.activeSessionId).toBe('session-a');
    expect(replies.replySuggestionState.currentPhone).toBe('18800001111');
    expect(replies.replySuggestionState.progressStage).toBe('GENERATING');
  });

  it('enters fallback mode on empty Skill output and automatically recovers with retry', async () => {
    const { replies } = await freshStore();
    replies.showRecognizeResult(response('18800001111', []));

    expect(replies.replySuggestionState.isFallbackMode).toBe(true);
    expect(replies.replySuggestionState.replySource?.source).toBe('FALLBACK');
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
      .mockResolvedValueOnce({ success: true, data: response('18800001111', [suggestion('New 3')], '已连续换 5 次，可以尝试求助组长') });

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
    expect(replies.replySuggestionState.helpHintMessage).toBe('已连续换 5 次，可以尝试求助组长');
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

function response(phone: string, suggestions: ReplySuggestion[], warning?: string): ChatResponse {
  return {
    phone,
    nickname: 'Alice',
    match: { matchType: 'EXACT' },
    skill: { suggestions },
    replySource: suggestions[0]?.direction === 'SYSTEM_FALLBACK'
      ? { source: 'FALLBACK', label: '系统兜底', detail: 'fallback' }
      : { source: 'SKILL', label: 'Skill 生成', detail: 'skill' },
    warning
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
