import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { PendingReplyTask, PendingReplyTaskStatus } from './types';

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const getAlertsByPhoneMock = vi.fn();
const notifyReplyTaskMock = vi.fn();
const onReplyTaskOpenMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock,
  postJson: postJsonMock
}));

vi.mock('../abnormal-alert/alertStore', () => ({
  getAlertsByPhone: getAlertsByPhoneMock
}));

vi.mock('../../shared/desktopBridge', () => ({
  notifyReplyTask: notifyReplyTaskMock,
  onReplyTaskOpen: onReplyTaskOpenMock
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
  notifyReplyTaskMock.mockReset();
  notifyReplyTaskMock.mockResolvedValue({ success: true });
  onReplyTaskOpenMock.mockReset();
  const pending = await import('./pendingReplyTaskStore');
  const replies = await import('./replySuggestionStore');
  return { pending, replies };
}

describe('pendingReplyTaskStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.spyOn(document, 'hasFocus').mockReturnValue(true);
  });

  afterEach(async () => {
    const replies = await import('./replySuggestionStore');
    replies.cleanupReplySuggestionStore();
    localStorage.clear();
    vi.restoreAllMocks();
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

  it('opens a waiting task immediately in the foreground without a system notification', async () => {
    const { pending, replies } = await freshStores();
    const multipleEvents: unknown[] = [];
    const { eventBus } = await import('../../shared/eventBus');
    const dispose = eventBus.on('recognize:multiple', (payload) => multipleEvents.push(payload));
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();

    expect(notifyReplyTaskMock).not.toHaveBeenCalled();
    expect(replies.replySuggestionState.activeSessionId).toBe('reply-session-1');
    expect(multipleEvents).toEqual([{
      sessionId: 'reply-session-1',
      taskId: 'task-1',
      candidates: [{ phone: '18800001111', nickname: 'Alice' }]
    }]);
    dispose();
  });

  it('opens only one new waiting task from a foreground snapshot', async () => {
    const { pending, replies } = await freshStores();
    const multipleEvents: unknown[] = [];
    const { eventBus } = await import('../../shared/eventBus');
    const dispose = eventBus.on('recognize:multiple', (payload) => multipleEvents.push(payload));
    getJsonMock.mockResolvedValue({
      success: true,
      data: [
        task('WAITING_CUSTOMER'),
        task('WAITING_CUSTOMER', { taskId: 'task-2', replySessionId: 'reply-session-2' })
      ]
    });

    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();

    expect(replies.replySuggestionState.sessions).toHaveLength(2);
    expect(multipleEvents).toEqual([expect.objectContaining({ taskId: 'task-1' })]);
    dispose();
  });

  it('records a foreground live task without replaying the existing UI event on refresh', async () => {
    const { pending } = await freshStores();
    const multipleEvents: unknown[] = [];
    const { eventBus } = await import('../../shared/eventBus');
    const dispose = eventBus.on('recognize:multiple', (payload) => multipleEvents.push(payload));
    pending.receivePendingReplyTask(task('WAITING_CUSTOMER'));
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();

    expect(pending.pendingReplyTaskCount.value).toBe(1);
    expect(multipleEvents).toEqual([]);
    expect(notifyReplyTaskMock).not.toHaveBeenCalled();
    dispose();
  });

  it('allows a foreground task to open automatically after it disappears and returns', async () => {
    const { pending } = await freshStores();
    const multipleEvents: unknown[] = [];
    const { eventBus } = await import('../../shared/eventBus');
    const dispose = eventBus.on('recognize:multiple', (payload) => multipleEvents.push(payload));
    getJsonMock
      .mockResolvedValueOnce({ success: true, data: [task('WAITING_CUSTOMER')] })
      .mockResolvedValueOnce({ success: true, data: [] })
      .mockResolvedValueOnce({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();

    expect(multipleEvents).toHaveLength(2);
    dispose();
  });

  it('notifies a background waiting task once and allows a future reminder after it disappears', async () => {
    vi.mocked(document.hasFocus).mockReturnValue(false);
    const { pending } = await freshStores();
    getJsonMock
      .mockResolvedValueOnce({ success: true, data: [task('WAITING_CUSTOMER')] })
      .mockResolvedValueOnce({ success: true, data: [task('WAITING_CUSTOMER')] })
      .mockResolvedValueOnce({ success: true, data: [] })
      .mockResolvedValueOnce({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();

    expect(notifyReplyTaskMock).toHaveBeenCalledTimes(2);
    expect(notifyReplyTaskMock).toHaveBeenNthCalledWith(1, { taskId: 'task-1' });
    expect(notifyReplyTaskMock).toHaveBeenNthCalledWith(2, { taskId: 'task-1' });
  });

  it.each([
    ['returns success false', () => Promise.resolve({ success: false })],
    ['rejects', () => Promise.reject(new Error('notification unavailable'))]
  ])('retries a background notification after the first attempt %s', async (_label, notifyResult) => {
    vi.mocked(document.hasFocus).mockReturnValue(false);
    const { pending } = await freshStores();
    notifyReplyTaskMock.mockImplementation(notifyResult);
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();
    await Promise.resolve();
    await pending.refreshPendingReplyTasks();

    expect(notifyReplyTaskMock).toHaveBeenCalledTimes(2);
  });

  it('does not duplicate a background notification while the first attempt is in flight', async () => {
    vi.mocked(document.hasFocus).mockReturnValue(false);
    const { pending } = await freshStores();
    const notification = deferred<{ success: boolean }>();
    notifyReplyTaskMock.mockReturnValue(notification.promise);
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });

    await pending.refreshPendingReplyTasks();
    await pending.refreshPendingReplyTasks();

    expect(notifyReplyTaskMock).toHaveBeenCalledTimes(1);
    notification.resolve({ success: true });
    await Promise.resolve();
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

  it('clears account tasks and reply sessions and invalidates an in-flight account refresh', async () => {
    const { pending, replies } = await freshStores();
    pending.syncPendingReplyTask(task('WAITING_CUSTOMER'));
    const oldAccountResponse = deferred<unknown>();
    getJsonMock.mockReturnValueOnce(oldAccountResponse.promise);
    const oldRefresh = pending.refreshPendingReplyTasks();

    pending.resetPendingReplyTasksForSessionChange();
    oldAccountResponse.resolve({ success: true, data: [task('READY', {
      response: savedResponse('Stale account reply')
    })] });
    await oldRefresh;

    expect(pending.pendingReplyTaskState.tasks).toEqual([]);
    expect(pending.pendingReplyTaskState.activeTaskId).toBe('');
    expect(replies.replySuggestionState.sessions).toEqual([]);
  });

  it('keeps a new account empty when its first task recovery fails after reset', async () => {
    const { pending, replies } = await freshStores();
    pending.syncPendingReplyTask(task('WAITING_CUSTOMER'));
    pending.resetPendingReplyTasksForSessionChange();
    getJsonMock.mockRejectedValueOnce(new Error('account B recovery failed'));

    await pending.refreshPendingReplyTasks();

    expect(pending.pendingReplyTaskState.tasks).toEqual([]);
    expect(replies.replySuggestionState.sessions).toEqual([]);
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
    const multipleEvents: unknown[] = [];
    const { eventBus } = await import('../../shared/eventBus');
    const dispose = eventBus.on('recognize:multiple', (payload) => multipleEvents.push(payload));
    getJsonMock.mockResolvedValue({ success: true, data: [task('WAITING_CUSTOMER')] });
    await pending.refreshPendingReplyTasks();
    replies.closeReplySession('reply-session-1');

    replies.startRecognizeLoading({ sessionId: 'reply-session-1', source: 'BUTTON_CLICK' });
    expect(replies.replySuggestionState.sessions).toHaveLength(0);

    expect(pending.openRecoveredReplyTask('task-1')).toBe(true);
    expect(replies.replySuggestionState.sessions).toHaveLength(1);
    expect(replies.activeReplySession.value).toMatchObject({
      sessionId: 'reply-session-1',
      pendingTaskId: 'task-1',
      status: 'MULTIPLE'
    });
    expect(multipleEvents.at(-1)).toEqual({
      sessionId: 'reply-session-1',
      taskId: 'task-1',
      candidates: [{ phone: '18800001111', nickname: 'Alice' }]
    });
    expect(pending.openPendingReplyTask('missing-task')).toBe(false);
    dispose();
  });

  it('opens a saved READY task without repeating Skill or LLM generation', async () => {
    const { pending, replies } = await freshStores();
    pending.syncPendingReplyTask(task('READY', {
      selectedPhone: '18800001111',
      response: savedResponse('Saved reply')
    }));

    expect(pending.openRecoveredReplyTask('task-1')).toBe(true);

    expect(replies.activeReplySession.value).toMatchObject({
      sessionId: 'reply-session-1',
      status: 'READY',
      suggestions: [{ text: 'Saved reply' }]
    });
    expect(postJsonMock).not.toHaveBeenCalled();
  });

  it('opens a notification task through an initialized disposable listener', async () => {
    const dispose = vi.fn();
    let listener: ((payload: { taskId: string }) => void) | undefined;
    const { pending, replies } = await freshStores();
    onReplyTaskOpenMock.mockImplementation((callback: (payload: { taskId: string }) => void) => {
      listener = callback;
      return dispose;
    });
    pending.syncPendingReplyTask(task('WAITING_CUSTOMER'));
    replies.closeReplySession('reply-session-1');

    const cleanup = pending.initializePendingReplyTaskOpenListener();
    listener?.({ taskId: 'task-1' });

    expect(replies.replySuggestionState.activeSessionId).toBe('reply-session-1');
    cleanup();
    expect(dispose).toHaveBeenCalledTimes(1);
  });

  it('restores waiting, generating, ready, and failed server tasks without overwriting sessions', async () => {
    const { pending, replies } = await freshStores();
    getJsonMock.mockResolvedValue({
      success: true,
      data: [
        task('WAITING_CUSTOMER'),
        task('GENERATING', { taskId: 'task-2', replySessionId: 'reply-session-2', selectedPhone: '18800002222' }),
        task('READY', {
          taskId: 'task-3',
          replySessionId: 'reply-session-3',
          selectedPhone: '18800003333',
          response: savedResponse('Saved third reply')
        }),
        task('FAILED', { taskId: 'task-4', replySessionId: 'reply-session-4', selectedPhone: '18800004444' })
      ]
    });

    await pending.refreshPendingReplyTasks();

    expect(replies.replySuggestionState.sessions).toHaveLength(4);
    expect(replies.replySuggestionState.sessions
      .map((session) => [session.sessionId, session.status])
      .sort(([left], [right]) => String(left).localeCompare(String(right)))).toEqual([
      ['reply-session-1', 'MULTIPLE'],
      ['reply-session-2', 'LOADING'],
      ['reply-session-3', 'READY'],
      ['reply-session-4', 'FAILED']
    ]);
    expect(postJsonMock).not.toHaveBeenCalled();
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
