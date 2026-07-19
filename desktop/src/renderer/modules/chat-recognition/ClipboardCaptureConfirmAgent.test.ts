import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const postJsonMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  postJson: postJsonMock,
  getJson: vi.fn()
}));

type MountedAgent = {
  app: App<Element>;
  host: HTMLDivElement;
  recognition: typeof import('./recognitionStore');
  desktopStatus: typeof import('../../shared/desktopStatusStore');
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

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function waitForPostJson(): Promise<void> {
  for (let index = 0; index < 50; index += 1) {
    await flushUi();
    if (postJsonMock.mock.calls.length > 0) {
      return;
    }
    await vi.advanceTimersByTimeAsync(0);
  }
  expect(postJsonMock).toHaveBeenCalled();
}

async function mountAgent(): Promise<MountedAgent> {
  vi.resetModules();
  postJsonMock.mockReset();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({ clipboardScreenshotConfirmPromptS: 10 }));
  const [{ default: ClipboardCaptureConfirmAgent }, recognition, desktopStatus] = await Promise.all([
    import('./ClipboardCaptureConfirmAgent.vue'),
    import('./recognitionStore'),
    import('../../shared/desktopStatusStore')
  ]);
  desktopStatus.desktopStatusState.runtimeConfig.clipboardScreenshotConfirmPromptS = 10;
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(ClipboardCaptureConfirmAgent);
  app.mount(host);
  await flushUi();
  return { app, host, recognition, desktopStatus };
}

describe('ClipboardCaptureConfirmAgent', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    postJsonMock.mockResolvedValue({
      success: true,
      data: {
        match: { matchType: 'EXACT' },
        skill: { suggestions: [{ text: 'ok', direction: 'NEXT_STEP' }] }
      }
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    localStorage.clear();
    vi.useRealTimers();
    postJsonMock.mockReset();
  });

  it('confirms a pending screenshot before calling the recognition API', async () => {
    const { app, host, recognition } = await mountAgent();

    await recognition.recognizeClipboardImage({ imageBase64: 'clipboard-image', md5: 'clip-a', width: 300, height: 300 });
    await flushUi();

    expect(host.textContent).toContain('发现新截图');
    expect(postJsonMock).not.toHaveBeenCalled();

    (host.querySelector('.clipboard-confirm-actions .primary') as HTMLButtonElement | null)?.click();
    await waitForPostJson();

    expect(postJsonMock).toHaveBeenCalledWith('/api/v1/chat/recognize', {
      imageBase64: 'clipboard-image',
      textMessage: undefined,
      customerIdentifier: undefined,
      source: 'CLIPBOARD_SCREENSHOT'
    }, 0);
    expect(recognition.recognitionState.pendingClipboardImage).toBeNull();
    app.unmount();
  });

  it('auto ignores after the configured seconds and resets when a newer screenshot arrives', async () => {
    const { app, host, recognition } = await mountAgent();

    await recognition.recognizeClipboardImage({ imageBase64: 'old-image', md5: 'clip-a', width: 300, height: 300 });
    await flushUi();
    await vi.advanceTimersByTimeAsync(9000);
    await recognition.recognizeClipboardImage({ imageBase64: 'new-image', md5: 'clip-b', width: 300, height: 300 });
    await flushUi();
    await vi.advanceTimersByTimeAsync(1000);
    await flushUi();

    expect(recognition.recognitionState.pendingClipboardImage?.imageBase64).toBe('new-image');
    expect(host.textContent).toContain('发现新截图');

    await vi.advanceTimersByTimeAsync(9000);
    await flushUi();

    expect(recognition.recognitionState.pendingClipboardImage).toBeNull();
    expect(postJsonMock).not.toHaveBeenCalled();
    app.unmount();
  });

  it('does not auto ignore when the configured seconds is zero', async () => {
    const { app, host, recognition, desktopStatus } = await mountAgent();
    desktopStatus.desktopStatusState.runtimeConfig.clipboardScreenshotConfirmPromptS = 0;

    await recognition.recognizeClipboardImage({ imageBase64: 'clipboard-image', md5: 'clip-a', width: 300, height: 300 });
    await flushUi();
    await vi.advanceTimersByTimeAsync(60000);
    await flushUi();

    expect(recognition.recognitionState.pendingClipboardImage?.imageBase64).toBe('clipboard-image');
    expect(host.textContent).toContain('确认这是客户聊天后再识别');
    app.unmount();
  });
});
