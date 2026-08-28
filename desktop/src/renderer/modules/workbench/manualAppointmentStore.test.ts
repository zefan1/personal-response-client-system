import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  captureScreenshot: vi.fn(),
  getJson: vi.fn(),
  postJson: vi.fn(),
  postForm: vi.fn(),
  writeClipboardText: vi.fn(),
  resolveQuickSearchTemplate: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: mocks.getJson,
  postForm: mocks.postForm,
  postJson: mocks.postJson
}));

vi.mock('../../shared/desktopBridge', () => ({
  captureScreenshot: mocks.captureScreenshot,
  writeClipboardText: mocks.writeClipboardText
}));

vi.mock('../quick-search/templateVariables', () => ({
  resolveQuickSearchTemplate: mocks.resolveQuickSearchTemplate
}));

vi.mock('./workbenchStore', () => ({
  showWorkbenchSuccessToast: vi.fn()
}));

describe('manual appointment matching', () => {
  beforeEach(() => {
    vi.resetModules();
    mocks.captureScreenshot.mockResolvedValue({ success: true, imageBase64: 'capture' });
    mocks.postJson.mockReset();
    mocks.resolveQuickSearchTemplate.mockImplementation((content: string) => content);
    mocks.writeClipboardText.mockResolvedValue({ success: true });
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

  it('retries once with the latest customer version without clearing entered values', async () => {
    mocks.postJson
      .mockResolvedValueOnce({ success: false, data: null, errorCode: '50-10002', message: '档案已被更新，请刷新后重试' })
      .mockResolvedValueOnce({ success: true, data: { synced: true, taskId: 9, templateContent: null }, errorCode: null, message: null });
    mocks.getJson.mockResolvedValueOnce({
      success: true,
      data: { customerId: 8, customerVersion: 12, nickname: '客户', phone: '18800001111', values: {}, fields: [] },
      errorCode: null,
      message: null
    });

    const store = await import('./manualAppointmentStore');
    store.manualAppointmentState.form = {
      customerId: 8,
      customerVersion: 11,
      nickname: '客户',
      phone: '18800001111',
      values: { sourceChannel: '抖音' },
      fields: []
    };

    await store.submitManualAppointment();

    expect(mocks.postJson).toHaveBeenCalledTimes(2);
    expect(mocks.postJson.mock.calls[1][1]).toEqual({ customerVersion: 12, values: { sourceChannel: '抖音' } });
  });

  it('copies the latest saved customer values instead of the stale form values', async () => {
    mocks.postJson.mockResolvedValueOnce({
      success: true,
      data: {
        synced: true,
        taskId: 9,
        templateContent: '客户名称：{{客户名称}}\n预约项目：{{预约项目}}',
        templateValues: { customerName: '江怀', appointmentItem: '产后修复', appointmentStore: '万江店' }
      },
      errorCode: null,
      message: null
    });

    const store = await import('./manualAppointmentStore');
    store.manualAppointmentState.form = {
      customerId: 8,
      customerVersion: 11,
      nickname: '微信昵称',
      phone: '18800001111',
      values: { customerName: '', appointmentItem: '' },
      fields: []
    };

    await store.submitManualAppointment();

    expect(mocks.resolveQuickSearchTemplate).toHaveBeenCalledWith(
      '客户名称：{{客户名称}}\n预约项目：{{预约项目}}',
      expect.objectContaining({ customerName: '江怀', appointmentItem: '产后修复', appointmentStore: '万江店', nickname: '微信昵称' }),
      '18800001111'
    );
  });

  it('does not copy unresolved appointment placeholders to the customer', async () => {
    mocks.postJson.mockResolvedValueOnce({
      success: true,
      data: { synced: true, taskId: 9, templateContent: '客户名称：江怀\n预约时间：{{预约时间}}\n预约项目：{{预约项目}}\n欢迎到店', templateValues: {} },
      errorCode: null,
      message: null
    });

    const store = await import('./manualAppointmentStore');
    store.manualAppointmentState.form = { customerId: 8, customerVersion: 11, nickname: '微信昵称', phone: '18800001111', values: {}, fields: [] };

    await store.submitManualAppointment();

    expect(mocks.writeClipboardText).toHaveBeenCalledWith('客户名称：江怀\n欢迎到店');
  });
});
