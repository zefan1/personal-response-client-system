import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  getJson: vi.fn(),
  postJson: vi.fn(),
  openAssignmentTable: vi.fn(),
  showSuccess: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: mocks.getJson,
  postJson: mocks.postJson
}));
vi.mock('../../shared/desktopBridge', () => ({ openAssignmentTable: mocks.openAssignmentTable }));
vi.mock('./workbenchStore', () => ({ showWorkbenchSuccessToast: mocks.showSuccess }));

import AssignmentTablePanel from './AssignmentTablePanel.vue';

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

describe('AssignmentTablePanel', () => {
  let app: App<Element>;
  let host: HTMLDivElement;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T04:00:00Z'));
    mocks.getJson.mockResolvedValue({
      success: true,
      data: [
        { id: 1, tableName: '8月分配', monthKey: '2026-08', documentUrl: 'https://doc.weixin.qq.com/old', status: 'ACTIVE' }
      ]
    });
    mocks.postJson.mockResolvedValue({
      success: true,
      data: { id: 2, tableName: '国庆活动客资', monthKey: '2026-09', documentUrl: 'https://doc.weixin.qq.com/new', status: 'ACTIVE' }
    });
    mocks.openAssignmentTable.mockResolvedValue({ success: true });
    host = document.createElement('div');
    document.body.appendChild(host);
    app = createApp(AssignmentTablePanel);
    app.mount(host);
    await flushUi();
  });

  afterEach(() => {
    app.unmount();
    document.body.innerHTML = '';
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('is read-only and opens the current table externally', async () => {
    expect([...host.querySelectorAll('button')].some((button) => button.textContent?.includes('创建新表'))).toBe(false);
    expect(host.querySelector('.assignment-table-history')).toBeNull();
    const openButton = [...host.querySelectorAll('.assignment-table-active button')][0] as HTMLButtonElement;
    openButton.click();
    await flushUi();
    expect(mocks.openAssignmentTable).toHaveBeenCalledWith('https://doc.weixin.qq.com/old');
  });

  it('shows initial load failures instead of an empty-table message only', async () => {
    app.unmount();
    mocks.getJson.mockRejectedValueOnce(new Error('后端服务未启动'));
    app = createApp(AssignmentTablePanel);
    app.mount(host);
    await flushUi();

    expect(host.querySelector('.admin-message.error')?.textContent).toContain('后端服务未启动');

    expect(host.querySelector('.assignment-table-dialog')).toBeNull();
  });
});
