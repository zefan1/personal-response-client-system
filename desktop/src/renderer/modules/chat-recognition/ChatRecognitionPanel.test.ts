import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  postJson: vi.fn(),
  captureScreenshot: vi.fn(),
  onClipboardImage: vi.fn(),
  connectWsMessageBus: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  postJson: mocks.postJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  captureScreenshot: mocks.captureScreenshot,
  onClipboardImage: mocks.onClipboardImage
}));

vi.mock('../../shared/wsMessageBus', () => ({
  connectWsMessageBus: mocks.connectWsMessageBus
}));

type MountedPanel = {
  app: App<Element>;
  host: HTMLDivElement;
  eventBus: typeof import('../../shared/eventBus')['eventBus'];
};

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function waitForPostJson(): Promise<void> {
  for (let index = 0; index < 50; index += 1) {
    await flushUi();
    await new Promise((resolve) => window.setTimeout(resolve, 0));
    if (mocks.postJson.mock.calls.length > 0) {
      return;
    }
  }
  expect(mocks.postJson).toHaveBeenCalled();
}

async function mountPanel(): Promise<MountedPanel> {
  vi.resetModules();
  mocks.onClipboardImage.mockReturnValue(() => undefined);
  const [{ default: ChatRecognitionPanel }, { eventBus }] = await Promise.all([
    import('./ChatRecognitionPanel.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(ChatRecognitionPanel);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

function setValue(element: HTMLInputElement | HTMLTextAreaElement, value: string): void {
  element.value = value;
  element.dispatchEvent(new Event('input', { bubbles: true }));
  element.dispatchEvent(new Event('change', { bubbles: true }));
}

describe('ChatRecognitionPanel', () => {
  beforeEach(() => {
    mocks.postJson.mockResolvedValue({ success: true, data: response('EXACT') });
    mocks.captureScreenshot.mockResolvedValue({ success: true, imageBase64: 'button-image' });
    mocks.onClipboardImage.mockReturnValue(() => undefined);
  });

  afterEach(() => {
    document.body.innerHTML = '';
    Object.values(mocks).forEach((mock) => mock.mockReset());
  });

  it('captures from the rendered button and emits recognize events after a successful screenshot request', async () => {
    const { app, host, eventBus } = await mountPanel();
    const events: Array<{ event: string; payload: unknown }> = [];
    eventBus.on('recognize:start', (payload) => events.push({ event: 'recognize:start', payload }));
    eventBus.on('recognize:result', (payload) => events.push({ event: 'recognize:result', payload }));

    const capture = host.querySelector('.toolbar .primary') as HTMLButtonElement | null;
    capture?.click();
    await flushUi();

    expect(mocks.connectWsMessageBus).toHaveBeenCalled();
    expect(mocks.captureScreenshot).toHaveBeenCalled();
    await waitForPostJson();
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/chat/recognize', {
      imageBase64: 'button-image',
      textMessage: undefined,
      customerIdentifier: undefined,
      source: 'BUTTON_CLICK'
    });
    expect(events[0]).toMatchObject({ event: 'recognize:start', payload: { source: 'BUTTON_CLICK' } });
    expect(events[1]).toMatchObject({ event: 'recognize:result', payload: { source: 'BUTTON_CLICK', response: response('EXACT') } });
    expect((events[0].payload as { sessionId?: string }).sessionId).toBeTruthy();
    expect((events[1].payload as { sessionId?: string }).sessionId).toBe((events[0].payload as { sessionId?: string }).sessionId);
    app.unmount();
  });

  it('shows the capture coordinator failure reason instead of replacing it with a generic error', async () => {
    mocks.captureScreenshot.mockResolvedValueOnce({
      success: false,
      error: 'CAPTURE_FAILED',
      message: '当前窗口未显示可识别的主聊天会话'
    });
    const { app, host } = await mountPanel();

    (host.querySelector('.toolbar .primary') as HTMLButtonElement | null)?.click();
    await flushUi();

    expect(host.textContent).toContain('当前窗口未显示可识别的主聊天会话');
    expect(mocks.postJson).not.toHaveBeenCalled();
    app.unmount();
  });

  it('opens text mode from the secondary button and submits customer identity plus chat text', async () => {
    const { app, host } = await mountPanel();

    const textMode = host.querySelector('.toolbar .secondary') as HTMLButtonElement | null;
    textMode?.click();
    await flushUi();

    const identity = host.querySelector('.two-box input') as HTMLInputElement | null;
    const chat = host.querySelector('.two-box textarea') as HTMLTextAreaElement | null;
    expect(identity).toBeTruthy();
    expect(chat).toBeTruthy();
    setValue(identity as HTMLInputElement, 'Alice');
    setValue(chat as HTMLTextAreaElement, 'customer wants appointment');

    const form = host.querySelector('.two-box') as HTMLFormElement | null;
    form?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await flushUi();

    await waitForPostJson();
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/chat/recognize', {
      imageBase64: undefined,
      textMessage: 'customer wants appointment',
      customerIdentifier: 'Alice',
      source: 'CLIPBOARD_TEXT'
    });
    expect(host.querySelector('.two-box')).toBeFalsy();
    app.unmount();
  });

  it('disables screenshot recognition and keeps text mode available when image service is down', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('image:status-changed', { status: 'DOWN' });
    await flushUi();

    const capture = host.querySelector('.toolbar .primary') as HTMLButtonElement | null;
    expect(capture?.disabled).toBe(true);
    expect(host.querySelector('.banner')).toBeTruthy();
    expect(host.querySelector('.two-box')).toBeTruthy();

    const chat = host.querySelector('.two-box textarea') as HTMLTextAreaElement | null;
    setValue(chat as HTMLTextAreaElement, 'fallback text');
    (host.querySelector('.two-box') as HTMLFormElement | null)?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await flushUi();

    expect(mocks.captureScreenshot).not.toHaveBeenCalled();
    await waitForPostJson();
    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/chat/recognize', {
      imageBase64: undefined,
      textMessage: 'fallback text',
      customerIdentifier: '',
      source: 'CLIPBOARD_TEXT'
    });
    app.unmount();
  });

  it('keeps clipboard screenshots pending without rendering an inline confirmation card', async () => {
    const { app, host } = await mountPanel();
    const recognition = await import('./recognitionStore');
    const clipboardHandler = mocks.onClipboardImage.mock.calls[0]?.[0] as
      | ((payload: { imageBase64: string; md5: string; width: number; height: number }) => void)
      | undefined;

    clipboardHandler?.({ imageBase64: 'clipboard-image', md5: 'clip-a', width: 300, height: 300 });
    await flushUi();

    expect(mocks.postJson).not.toHaveBeenCalled();
    expect(recognition.recognitionState.pendingClipboardImage?.imageBase64).toBe('clipboard-image');
    expect(host.querySelector('.clipboard-capture-card')).toBeFalsy();
    app.unmount();
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
