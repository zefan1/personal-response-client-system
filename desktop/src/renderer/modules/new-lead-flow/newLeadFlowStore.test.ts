import { beforeEach, describe, expect, it, vi } from 'vitest';

const getJsonMock = vi.fn();
const postJsonMock = vi.fn();
const writeClipboardTextMock = vi.fn();

vi.mock('../../shared/apiClient', () => ({
  getJson: getJsonMock,
  postJson: postJsonMock
}));
vi.mock('../../shared/desktopBridge', () => ({
  writeClipboardText: writeClipboardTextMock
}));

describe('newLeadFlowStore', () => {
  beforeEach(() => {
    vi.resetModules();
    getJsonMock.mockReset();
    postJsonMock.mockReset();
    writeClipboardTextMock.mockResolvedValue({ success: true });
  });

  it('re-reads the profile and retries once after a version conflict', async () => {
    const { confirmLeadContact, newLeadFlowState } = await import('./newLeadFlowStore');
    newLeadFlowState.item = {
      phone: '13537442729',
      phoneFull: '13537442729',
      customerVersion: 5,
      nickname: null
    };
    newLeadFlowState.nicknameDraft = '微信昵称';
    postJsonMock
      .mockResolvedValueOnce({ success: false, data: null, errorCode: '50-10002', message: '档案已被更新，请刷新后重试' })
      .mockResolvedValueOnce({ success: true, data: { version: 7 }, errorCode: null, message: null });
    getJsonMock.mockResolvedValueOnce({
      success: true,
      data: { customer: { version: 6, nickname: null } },
      errorCode: null,
      message: null
    });

    await confirmLeadContact();

    expect(postJsonMock).toHaveBeenCalledTimes(2);
    expect(postJsonMock.mock.calls[1][1]).toMatchObject({ version: 6, nickname: '微信昵称' });
    expect(newLeadFlowState.open).toBe(false);
    expect(newLeadFlowState.error).toBe('');
  });

  it('does not overwrite a nickname entered by another operator', async () => {
    const { confirmLeadContact, newLeadFlowState } = await import('./newLeadFlowStore');
    newLeadFlowState.item = {
      phone: '13537442729',
      phoneFull: '13537442729',
      customerVersion: 5,
      nickname: null
    };
    newLeadFlowState.nicknameDraft = '本次填写';
    postJsonMock.mockResolvedValueOnce({ success: false, data: null, errorCode: '50-10002', message: '档案已被更新，请刷新后重试' });
    getJsonMock.mockResolvedValueOnce({
      success: true,
      data: { customer: { version: 6, nickname: '其他同事已填写' } },
      errorCode: null,
      message: null
    });

    await confirmLeadContact();

    expect(postJsonMock).toHaveBeenCalledTimes(1);
    expect(newLeadFlowState.open).toBe(false);
    expect(newLeadFlowState.error).toBe('该客户的微信昵称已被其他人填写，请确认后再提交');
  });
});
