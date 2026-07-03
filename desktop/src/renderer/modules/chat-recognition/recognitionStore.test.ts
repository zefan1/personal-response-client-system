import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const postJsonMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  postJson: postJsonMock
}));

type RecognitionModule = typeof import('./recognitionStore');
type EventBusModule = typeof import('../../shared/eventBus');

async function freshStore(): Promise<{ recognition: RecognitionModule; eventBus: EventBusModule['eventBus'] }> {
  vi.resetModules();
  postJsonMock.mockReset();
  const recognition = await import('./recognitionStore');
  const { eventBus } = await import('../../shared/eventBus');
  return { recognition, eventBus };
}

describe('recognitionStore', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
  });

  afterEach(() => {
    vi.useRealTimers();
    postJsonMock.mockReset();
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
      source: 'CLIPBOARD_TEXT'
    });
    expect(seen).toEqual([
      { event: 'recognize:start', payload: { source: 'CLIPBOARD_TEXT' } },
      { event: 'recognize:result', payload: { source: 'CLIPBOARD_TEXT', response: response('EXACT') } }
    ]);
    expect(recognition.recognitionState.isRecognizePending).toBe(false);
    expect(recognition.recognitionState.isTwoBoxMode).toBe(false);
  });

  it('emits multiple-match candidates instead of a direct result', async () => {
    const { recognition, eventBus } = await freshStore();
    const multiple: unknown[] = [];
    eventBus.on('recognize:multiple', (payload) => multiple.push(payload));
    postJsonMock.mockResolvedValue({ success: true, data: response('MULTIPLE') });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'base64' });

    expect(multiple).toEqual([{
      candidates: [{ phone: '18800001111' }],
      matchInfo: { matchType: 'MULTIPLE', customers: [{ phone: '18800001111' }], matchCount: 1 }
    }]);
    expect(recognition.recognitionState.lastRequestSource).toBeNull();
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

  it('blocks concurrent recognition except manual button override of clipboard screenshot', async () => {
    const { recognition } = await freshStore();
    postJsonMock.mockResolvedValue({ success: true, data: response('EXACT') });
    recognition.recognitionState.isRecognizePending = true;
    recognition.recognitionState.lastRequestSource = 'CLIPBOARD_TEXT';

    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'blocked' });
    expect(postJsonMock).not.toHaveBeenCalled();
    expect(recognition.recognitionState.toast).toBeTruthy();

    recognition.recognitionState.isRecognizePending = true;
    recognition.recognitionState.lastRequestSource = 'CLIPBOARD_SCREENSHOT';
    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'manual' });
    expect(postJsonMock).toHaveBeenCalledTimes(1);
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

  it('ignores clipboard images while busy or image recognition is down', async () => {
    const { recognition } = await freshStore();
    postJsonMock.mockResolvedValue({ success: true, data: response('EXACT') });

    recognition.recognitionState.isRecognizePending = true;
    await recognition.recognizeClipboardImage({ imageBase64: 'busy', md5: 'a', width: 300, height: 300 });
    expect(postJsonMock).not.toHaveBeenCalled();

    recognition.recognitionState.isRecognizePending = false;
    recognition.recognitionState.imageServiceStatus = 'DOWN';
    await recognition.recognizeClipboardImage({ imageBase64: 'down', md5: 'b', width: 300, height: 300 });
    expect(postJsonMock).not.toHaveBeenCalled();
  });

  it('maps business and network failures to fallback events and user-visible state', async () => {
    const { recognition, eventBus } = await freshStore();
    const events: string[] = [];
    eventBus.on('recognize:image-failed', () => events.push('image-failed'));
    eventBus.on('recognize:timeout', () => events.push('timeout'));
    postJsonMock.mockResolvedValueOnce({ success: false, errorCode: '30-10001' });

    await recognition.triggerRecognize('BUTTON_CLICK', { imageBase64: 'bad-image' });

    expect(recognition.recognitionState.isTwoBoxMode).toBe(true);
    expect(events).toEqual(['image-failed']);

    postJsonMock.mockRejectedValueOnce(new Error('network down'));
    await recognition.triggerRecognize('CLIPBOARD_TEXT', { textMessage: 'network' });

    expect(events).toEqual(['image-failed', 'timeout']);
    expect(recognition.recognitionState.isRecognizePending).toBe(false);
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
