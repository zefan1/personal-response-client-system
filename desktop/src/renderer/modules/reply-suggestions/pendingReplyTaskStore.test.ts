import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PendingReplyTask, PendingReplyTaskStatus } from './types';

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const getAlertsByPhoneMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock,
  postJson: postJsonMock
}));

vi.mock('../abnormal-alert/alertStore', () => ({
  getAlertsByPhone: getAlertsByPhoneMock
}));

function installMemoryLocalStorage(): void {
  const values = new Map<string, string>();
  Object.defineProperty(globalThis, 'localStorage', {
    configurable: true,
    value: {
      getItem: vi.fn((key: string) => values.get(key) ?? null),
      setItem: vi.fn((key: string, value: string) => values.set(key, String(value))),
      removeItem: vi.fn((key: string) => values.delete(key)),
      clear: vi.fn(() => values.clear())
    }
  });
}

async function freshStores() {
  vi.resetModules();
  getJsonMock.mockReset();
  postJsonMock.mockReset();
  getAlertsByPhoneMock.mockReset();
  getAlertsByPhoneMock.mockReturnValue([]);
  const pending = await import('./pendingReplyTaskStore');
  const replies = await import('./replySuggestionStore');
  return { pending, replies };
}

describe('pendingReplyTaskStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
  });

  afterEach(async () => {
    const replies = await import('./replySuggestionStore');
    replies.cleanupReplySuggestionStore();
    localStorage.clear();
  });

  it('replaces server state and synchronizes waiting tasks into reply sessions', async () => {
    const { pending, replies } = await freshStores();
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();

    expect(getJsonMock).toHaveBeenCalledWith('/api/v1/chat/reply-tasks', 5000);
    expect(pending.pendingReplyTaskState.tasks).toEqual([task('WAITING_CUSTOMER')]);
    expect(pending.pendingReplyTaskCount.value).toBe(1);
    expect(replies.replySuggestionState.sessions).toHaveLength(1);
    expect(replies.activeReplySession.value).toMatchObject({
      sessionId: 'reply-session-1',
      pendingTaskId: 'task-1',
      status: 'MULTIPLE'
    });
  });

  it('keeps the last server snapshot when refresh fails', async () => {
    const { pending, replies } = await freshStores();
    getJsonMock.mockResolvedValueOnce({ success: true, data: [task('WAITING_CUSTOMER')] });
    await pending.refreshPendingReplyTasks();

    getJsonMock.mockRejectedValueOnce(new Error('network down'));
    await pending.refreshPendingReplyTasks();

    expect(pending.pendingReplyTaskState.tasks).toEqual([task('WAITING_CUSTOMER')]);
    expect(pending.pendingReplyTaskCount.value).toBe(1);
    expect(replies.replySuggestionState.sessions).toHaveLength(1);

    getJsonMock.mockResolvedValueOnce({ success: false, data: null });
    await pending.refreshPendingReplyTasks();
    expect(pending.pendingReplyTaskState.tasks).toEqual([task('WAITING_CUSTOMER')]);
    expect(replies.replySuggestionState.sessions).toHaveLength(1);
  });

  it('does not duplicate the reply session when the same task is refreshed twice', async () => {
    const { pending, replies } = await freshStores();
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();

    expect(replies.replySuggestionState.sessions).toHaveLength(1);
    expect(pending.openPendingReplyTask('task-1')).toBe(true);
    expect(replies.replySuggestionState.activeSessionId).toBe('reply-session-1');
  });

  it('ignores an older refresh that completes after a newer READY snapshot', async () => {
    const { pending, replies } = await freshStores();
    const older = deferred<unknown>();
    const newer = deferred<unknown>();
    getJsonMock
      .mockReturnValueOnce(older.promise)
      .mockReturnValueOnce(newer.promise);

    const olderRefresh = pending.refreshPendingReplyTasks();
    const newerRefresh = pending.refreshPendingReplyTasks();
    newer.resolve({ success: true, data: [task('READY', {
      selectedPhone: '18800001111',
      response: savedResponse('New saved reply')
    })] });
    await newerRefresh;
    older.resolve({ success: true, data: [task('WAITING_CUSTOMER')] });
    await olderRefresh;

    expect(pending.pendingReplyTaskState.tasks[0]).toMatchObject({ status: 'READY' });
    expect(replies.replySuggestionState.sessions).toHaveLength(1);
    expect(replies.activeReplySession.value).toMatchObject({
      status: 'READY',
      pendingTaskStatus: 'READY',
      suggestions: [{ text: 'New saved reply' }]
    });
  });

  it('removes unfinished sessions missing from a successful server snapshot', async () => {
    const { pending, replies } = await freshStores();
    getJsonMock
      .mockResolvedValueOnce({ success: true, data: [task('WAITING_CUSTOMER')] })
      .mockResolvedValueOnce({ success: true, data: [] });

    await pending.refreshPendingReplyTasks();
    expect(replies.replySuggestionState.sessions).toHaveLength(1);

    await pending.refreshPendingReplyTasks();

    expect(pending.pendingReplyTaskState.tasks).toEqual([]);
    expect(replies.replySuggestionState.sessions).toEqual([]);
  });

  it('explicitly opens a server task after its reply session was dismissed', async () => {
    const { pending, replies } = await freshStores();
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });
    await pending.refreshPendingReplyTasks();
    replies.closeReplySession('reply-session-1');

    replies.startRecognizeLoading({ sessionId: 'reply-session-1', source: 'BUTTON_CLICK' });
    expect(replies.replySuggestionState.sessions).toHaveLength(0);

    expect(pending.openPendingReplyTask('task-1')).toBe(true);
    expect(replies.replySuggestionState.sessions).toHaveLength(1);
    expect(replies.activeReplySession.value).toMatchObject({
      sessionId: 'reply-session-1',
      pendingTaskId: 'task-1',
      status: 'MULTIPLE'
    });
    expect(pending.openPendingReplyTask('missing-task')).toBe(false);
  });

  it('removes a cancelled task and its original multiple-match session immediately', async () => {
    const { pending, replies } = await freshStores();
    pending.syncPendingReplyTask(task('WAITING_CUSTOMER'));

    pending.removePendingReplyTask('task-1', 'reply-session-1');

    expect(pending.pendingReplyTaskState.tasks).toEqual([]);
    expect(pending.pendingReplyTaskState.activeTaskId).toBe('');
    expect(replies.replySuggestionState.sessions).toEqual([]);
    expect(pending.openPendingReplyTask('task-1')).toBe(false);
  });
});

function task(
  status: PendingReplyTaskStatus,
  overrides: Partial<PendingReplyTask> = {}
): PendingReplyTask {
  return {
    taskId: 'task-1',
    replySessionId: 'reply-session-1',
    status,
    candidates: [{ phone: '18800001111', nickname: 'Alice' }],
    selectedPhone: null,
    response: null,
    errorCode: null,
    expiresAt: '2026-07-04T12:00:00',
    ...overrides
  };
}

function savedResponse(text: string) {
  return {
    phone: '18800001111',
    nickname: 'Alice',
    match: { matchType: 'EXACT' as const },
    skill: { suggestions: [{ text, direction: 'NEXT_STEP', reason: 'saved' }] }
  };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((resolver) => {
    resolve = resolver;
  });
  return { promise, resolve };
}
