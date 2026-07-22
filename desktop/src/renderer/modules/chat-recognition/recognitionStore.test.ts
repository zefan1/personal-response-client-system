import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const postJsonMock = vi.fn();
const notifyReplyTaskMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  postJson: postJsonMock
}));

vi.mock('../../shared/desktopBridge', () => ({
  notifyReplyTask: notifyReplyTaskMock,
  onReplyTaskOpen: vi.fn(() => () => undefined)
}));

type RecognitionModule = typeof import('./recognitionStore');
type EventBusModule = typeof import('../../shared/eventBus');
type PendingModule = typeof import('../reply-suggestions/pendingReplyTaskStore');

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

async function freshStore(): Promise<{
  recognition: RecognitionModule;
  eventBus: EventBusModule['eventBus'];
  pending: PendingModule;
}> {
  vi.resetModules();
  postJsonMock.mockReset();
  notifyReplyTaskMock.mockReset();
  notifyReplyTaskMock.mockResolvedValue({ success: true });
  const recognition = await import('./recognitionStore');
  const { eventBus } = await import('../../shared/eventBus');
  const pending = await import('../reply-suggestions/pendingReplyTaskStore');
  return { recognition, eventBus, pending };
}

describe('recognitionStore', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    vi.spyOn(document, 'hasFocus').mockReturnValue(true);
    localStorage.clear();
  });

  afterEach(() => {
    vi.useRealTimers();
    postJsonMock.mockReset();
    notifyReplyTaskMock.mockReset();
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('emits recognize start and result for exact text recognition', async () => {
    const { recognition, eventBus } = await freshStore();
    const seen: Array<{ event: string; payload: unknown }> = [];
    eventBus.on('recognize:start', (payload) => seen.push({ event: 'recognize:start', payload }));
    eventBus.on('recognize:result', (payload) => seen.push({ event: 'recognize:result', payload }));
    postJsonMock.mockResolvedValue({ success: true, data: response('EXACT') });

    await recognition.triggerRecognize('CLIPBOARD_TEXT', {
      customerIdentifier: 'Alice',
      textMessage: 'hello'
    });

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/recognize', {
      imageBase64: undefined,
      textMessage: 'hello',
      customerIdentifier: 'Alice',
      source: 'CLIPBOARD_TEXT',
      replySessionId: expect.any(String)
    }, 0);
    expect(seen[0]).toMatchObject({ event: 'recognize:start', payload: { source: 'CLIPBOARD_TEXT' } });
    expect(seen[1]).toMatchObject({ event: 'recognize:result', payload: { source: 'CLIPBOARD_TEXT', response: response('EXACT') } });
    expect((seen[0].payload as { sessionId?: string }).sessionId).toBeTruthy();
    expect((seen[1].payload as { sessionId?: string }).sessionId).toBe((seen[0].payload as { sessionId?: string }).sessionId);
    expect(recognition.recognitionState.isRecognizePending).toBe(false);
    expect(recognition.recognitionState.isTwoBoxMode).toBe(false);
  });

  it('submits the shared text channel fields through the recognition API', async () => {
    const { recognition } = await freshStore();
    postJsonMock.mockResolvedValue({ success: true, data: response('EXACT') });
    recognition.recognitionState.customerIdentityInput = 'Alice';
    recognition.recognitionState.chatContentInput = 'customer asks for appointment';

    await recognition.submitTextRecognition();

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/recognize', {
      imageBase64: undefined,
      textMessage: 'customer asks for appointment',
      customerIdentifier: 'Alice',
      source: 'CLIPBOARD_TEXT',
      replySessionId: expect.any(String)
    }, 0);
  });

  it('emits multiple-match candidates instead of a direct result', async () => {
    vi.mocked(document.hasFocus).mockReturnValue(false);
    const { recognition, eventBus, pending } = await freshStore();
    const events: Array<{ type: string; payload: unknown }> = [];
    eventBus.on('recognize:progress', (payload) => events.push({ type: 'recognize:progress', payload }));
    eventBus.on('recognize:multiple', (payload) => events.push({ type: 'recognize:multiple', payload }));
    postJsonMock.mockResolvedValue({
      success: true,
      data: {
        ...response('MULTIPLE'),
        pendingTask: {
          taskId: 'task-1',
          replySessionId: 'server-session-1',
          status: 'WAITING_CUSTOMER',
          candidates: [{ phone: '18800001111', nickname: 'Alice' }],
          selectedPhone: null,
          response: null,
          errorCode: null,
          expiresAt: '2026-07-04T12:00:00'
        }
      }
    });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'base64' });

    expect(events.filter((event) => event.type === 'recognize:progress')).toEqual([
      expect.objectContaining({ payload: expect.objectContaining({ stage: 'UPLOADING' }) })
    ]);
    expect(events.find((event) => event.type === 'recognize:multiple')?.payload).toMatchObject({
      sessionId: 'server-session-1',
      taskId: 'task-1',
      candidates: [{ phone: '18800001111', nickname: 'Alice' }],
      matchInfo: { matchType: 'MULTIPLE', customers: [{ phone: '18800001111' }], matchCount: 1 }
    });
    expect(events.filter((event) => event.type === 'recognize:multiple')).toHaveLength(1);
    expect(recognition.recognitionState.lastRequestSource).toBeNull();
    expect(pending.pendingReplyTaskState.tasks).toEqual([
      expect.objectContaining({ taskId: 'task-1', status: 'WAITING_CUSTOMER' })
    ]);
    expect(pending.pendingReplyTaskCount.value).toBe(1);
    expect(notifyReplyTaskMock).toHaveBeenCalledTimes(1);
    expect(postJsonMock).toHaveBeenCalledTimes(1);
  });

  it('fails a multiple-match response without a persisted task id', async () => {
    const { recognition, eventBus } = await freshStore();
    const events: Array<{ type: string; payload: unknown }> = [];
    eventBus.on('recognize:progress', (payload) => events.push({ type: 'recognize:progress', payload }));
    eventBus.on('recognize:multiple', (payload) => events.push({ type: 'recognize:multiple', payload }));
    eventBus.on('recognize:failed', (payload) => events.push({ type: 'recognize:failed', payload }));
    postJsonMock.mockResolvedValue({ success: true, data: response('MULTIPLE') });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'base64' });

    expect(events.some((event) => event.type === 'recognize:multiple')).toBe(false);
    expect(events.some((event) => event.type === 'recognize:progress'
      && (event.payload as { stage?: string }).stage === 'GENERATING')).toBe(false);
    expect(events.find((event) => event.type === 'recognize:failed')?.payload).toMatchObject({
      errorCode: 'CLIENT_PROTOCOL_ERROR'
    });
  });

  it('deduplicates identical recognition content inside one second', async () => {
    const { recognition } = await freshStore();
    postJsonMock.mockResolvedValue({ success: true, data: response('EXACT') });

    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'same' });
    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'same' });

    expect(postJsonMock).toHaveBeenCalledTimes(1);

    vi.setSystemTime(new Date('2026-07-03T12:00:02Z'));
    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'same' });

    expect(postJsonMock).toHaveBeenCalledTimes(2);
  });

  it('allows concurrent recognition requests and tracks pending count', async () => {
    const { recognition } = await freshStore();
    let resolveFirst: (value: unknown) => void = () => undefined;
    postJsonMock
      .mockReturnValueOnce(new Promise((resolve) => { resolveFirst = resolve; }))
      .mockResolvedValueOnce({ success: true, data: response('EXACT') });

    const first = recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'first' });
    await Promise.resolve();
    expect(recognition.recognitionState.isRecognizePending).toBe(true);
    expect(recognition.recognitionState.pendingCount).toBe(1);

    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'second' });
    expect(postJsonMock).toHaveBeenCalledTimes(2);
    expect(recognition.recognitionState.pendingCount).toBe(1);

    resolveFirst({ success: true, data: response('EXACT') });
    await first;
    expect(recognition.recognitionState.pendingCount).toBe(0);
    expect(recognition.recognitionState.isRecognizePending).toBe(false);
  });

  it('routes image service DOWN status to text mode while still allowing text recognition', async () => {
    const { recognition } = await freshStore();
    recognition.handleImageServiceStatus({ status: 'DOWN' });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'base64' });
    expect(postJsonMock).not.toHaveBeenCalled();
    expect(recognition.recognitionState.isTwoBoxMode).toBe(true);

    postJsonMock.mockResolvedValue({ success: true, data: response('EXACT') });
    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'fallback text' });

    expect(postJsonMock).toHaveBeenCalledTimes(1);
    expect(recognition.recognitionState.isTwoBoxMode).toBe(false);

    recognition.handleImageServiceStatus({ status: 'UP' });
    expect(recognition.recognitionState.imageServiceStatus).toBe('UP');
  });

  it('keeps clipboard screenshots pending until the user confirms recognition', async () => {
    const { recognition } = await freshStore();
    postJsonMock.mockResolvedValue({ success: true, data: response('EXACT') });

    recognition.recognitionState.isRecognizePending = true;
    await recognition.recognizeClipboardImage({ imageBase64: 'busy', md5: 'a', width: 300, height: 300 });
    expect(postJsonMock).not.toHaveBeenCalled();
    expect(recognition.recognitionState.pendingClipboardImage?.imageBase64).toBe('busy');

    await recognition.recognizePendingClipboardImage();
    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/recognize', {
      imageBase64: 'busy',
      textMessage: undefined,
      customerIdentifier: undefined,
      source: 'CLIPBOARD_SCREENSHOT',
      replySessionId: expect.any(String)
    }, 0);
    expect(recognition.recognitionState.pendingClipboardImage).toBeNull();
  });

  it('drops pending clipboard screenshots when dismissed or image recognition is down', async () => {
    const { recognition } = await freshStore();

    await recognition.recognizeClipboardImage({ imageBase64: 'pending', md5: 'a', width: 300, height: 300 });
    expect(recognition.recognitionState.pendingClipboardImage?.imageBase64).toBe('pending');

    recognition.dismissPendingClipboardImage();
    expect(recognition.recognitionState.pendingClipboardImage).toBeNull();
    expect(postJsonMock).not.toHaveBeenCalled();

    recognition.recognitionState.isRecognizePending = false;
    recognition.recognitionState.imageServiceStatus = 'DOWN';
    await recognition.recognizeClipboardImage({ imageBase64: 'down', md5: 'b', width: 300, height: 300 });
    expect(postJsonMock).not.toHaveBeenCalled();
    expect(recognition.recognitionState.pendingClipboardImage).toBeNull();
    expect(recognition.recognitionState.isTwoBoxMode).toBe(true);
  });

  it('maps business and network failures to fallback events and user-visible state', async () => {
    const { recognition, eventBus } = await freshStore();
    const events: Array<{ event: string; payload: unknown }> = [];
    eventBus.on('recognize:image-failed', (payload) => events.push({ event: 'image-failed', payload }));
    eventBus.on('recognize:timeout', (payload) => events.push({ event: 'timeout', payload }));
    postJsonMock.mockResolvedValueOnce({ success: false, errorCode: '30-10001' });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'bad-image' });

    expect(recognition.recognitionState.isTwoBoxMode).toBe(true);
    expect(events[0]).toMatchObject({
      event: 'image-failed',
      payload: { errorCode: '30-10001' }
    });
    expect((events[0].payload as { sessionId?: string }).sessionId).toBeTruthy();

    postJsonMock.mockRejectedValueOnce(new Error('network down'));
    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'network' });

    expect(events[1]).toMatchObject({ event: 'timeout' });
    expect((events[1].payload as { sessionId?: string }).sessionId).toBeTruthy();
    expect(recognition.recognitionState.isRecognizePending).toBe(false);
  });

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

  it('uses the generic image failure message when the API omits a message', async () => {
    const { recognition, eventBus } = await freshStore();
    const events: unknown[] = [];
    eventBus.on('recognize:image-failed', (payload) => events.push(payload));
    postJsonMock.mockResolvedValueOnce({ success: false, errorCode: '30-10001', message: null });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'bad-image' });

    expect(events[0]).toMatchObject({
      errorCode: '30-10001',
      message: '图片识别失败，请粘贴客户标识和聊天内容'
    });
  });

  it('emits a failed event with the matching session id for non-image business errors', async () => {
    const { recognition, eventBus } = await freshStore();
    const events: unknown[] = [];
    eventBus.on('recognize:failed', (payload) => events.push(payload));
    postJsonMock.mockResolvedValueOnce({ success: false, errorCode: '30-10002' });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'bad-format' });

    expect(events[0]).toMatchObject({
      errorCode: '30-10002',
      message: '图片格式不支持，请使用 PNG/JPG 截图'
    });
    expect((events[0] as { sessionId?: string }).sessionId).toBeTruthy();
  });
});

function response(matchType: 'EXACT' | 'MULTIPLE') {
  return {
    phone: '18800001111',
    nickname: 'Alice',
    match: matchType === 'MULTIPLE'
      ? { matchType, customers: [{ phone: '18800001111' }], matchCount: 1 }
      : { matchType },
    skill: {
      suggestions: [{ text: 'hello', direction: 'NEXT_STEP', reason: 'reason' }]
    }
  };
}
