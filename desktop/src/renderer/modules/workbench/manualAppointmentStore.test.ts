import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  captureScreenshot: vi.fn(),
  postJson: vi.fn(),
  postForm: vi.fn(),
  writeClipboardText: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: vi.fn(),
  postForm: mocks.postForm,
  postJson: mocks.postJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  captureScreenshot: mocks.captureScreenshot,
  writeClipboardText: mocks.writeClipboardText
}));

vi.mock('../quick-search/templateVariables', () => ({
  resolveQuickSearchTemplate: vi.fn((content: string) => content)
}));

vi.mock('./workbenchStore', () => ({
  showWorkbenchSuccessToast: vi.fn()
}));

describe('manual appointment matching', () => {
  beforeEach(() => {
    vi.resetModules();
    mocks.captureScreenshot.mockResolvedValue({ success: true, imageBase64: 'capture' });
    mocks.postJson.mockReset();
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it('cancels an older failed request when retrying and keeps the latest result', async () => {
    let call = 0;
    mocks.postJson.mockImplementation((_path: string, _body: unknown, _timeout: number | undefined, signal?: AbortSignal) => {
      call += 1;
      if (call === 1) {
        return new Promise((_resolve, reject) => {
          signal?.addEventListener('abort', () => reject(new Error('aborted')));
        });
      }
      return Promise.resolve({
        success: true,
        data: { nickname: '微信客户', candidates: [] },
        errorCode: null,
        message: null
      });
    });

    const store = await import('./manualAppointmentStore');
    const first = store.matchCurrentChat();
    await Promise.resolve();
    const second = store.matchCurrentChat();
    await Promise.all([first, second]);

    expect(store.manualAppointmentState.stage).toBe('select');
    expect(store.manualAppointmentState.nickname).toBe('微信客户');
    expect(store.manualAppointmentState.error).toBe('');
    expect(mocks.postJson).toHaveBeenCalledTimes(2);
  });
});
