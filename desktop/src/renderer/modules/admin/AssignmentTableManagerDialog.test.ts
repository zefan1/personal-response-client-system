import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

const mocks = vi.hoisted(() => ({
  deleteJson: vi.fn(),
  getJson: vi.fn(),
  postJson: vi.fn(),
  openAssignmentTable: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({ deleteJson: mocks.deleteJson, getJson: mocks.getJson, postJson: mocks.postJson }));
vi.mock('../../shared/desktopBridge', () => ({ openAssignmentTable: mocks.openAssignmentTable }));

import AssignmentTableManagerDialog from './AssignmentTableManagerDialog.vue';

async function flushUi(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

describe('AssignmentTableManagerDialog', () => {
  let app: App<Element>;
  let host: HTMLDivElement;
  let changed: ReturnType<typeof vi.fn>;

  beforeEach(async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-09-03T04:00:00Z'));
    mocks.getJson.mockResolvedValue({
      success: true,
      data: [
        { id: 1, tableName: '8月分配', monthKey: '2026-08', documentUrl: 'https://doc.weixin.qq.com/old', status: 'ACTIVE' },
        { id: 2, tableName: '7月分配', monthKey: '2026-07', documentUrl: 'https://doc.weixin.qq.com/older', status: 'ARCHIVED' }
      ]
    });
    mocks.postJson.mockResolvedValue({
      success: true,
      data: { id: 3, tableName: '国庆活动客资', monthKey: '2026-09', documentUrl: 'https://doc.weixin.qq.com/new', status: 'ACTIVE' }
    });
    mocks.deleteJson.mockResolvedValue({ success: true, data: { deleted: true } });
    mocks.openAssignmentTable.mockResolvedValue({ success: true });
    host = document.createElement('div');
    document.body.appendChild(host);
    changed = vi.fn();
    app = createApp(AssignmentTableManagerDialog, { onChanged: changed });
    app.mount(host);
    await flushUi();
  });

  afterEach(() => {
    app.unmount();
    document.body.innerHTML = '';
    vi.useRealTimers();
    vi.clearAllMocks();
  });

  it('creates a custom-named table and switches the visible current table', async () => {
    expect(host.textContent).toContain('8月分配');
    expect(host.textContent).toContain('7月分配');
    const input = host.querySelector('input') as HTMLInputElement;
    expect(input.value).toBe('9月新客分配');
    input.value = '国庆活动客资';
    input.dispatchEvent(new Event('input'));
    (host.querySelector('.assignment-table-manager-create') as HTMLFormElement).dispatchEvent(new Event('submit'));
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/assignment-tables', { tableName: '国庆活动客资' }, 120_000);
    expect(host.querySelector('.assignment-table-manager-current')?.textContent).toContain('国庆活动客资');
    expect(host.querySelector('.assignment-table-manager-history')?.textContent).toContain('8月分配');
    expect(changed).toHaveBeenCalledTimes(1);
  });

  it('opens the active table through the desktop external-browser bridge', async () => {
    const button = host.querySelector('.assignment-table-manager-current button') as HTMLButtonElement;
    button.click();
    await flushUi();
    expect(mocks.openAssignmentTable).toHaveBeenCalledWith('https://doc.weixin.qq.com/old');
  });

  it('rebinds an archived table and only deletes its local history record', async () => {
    mocks.postJson.mockImplementation(async (path: string) => path.endsWith('/rebind')
      ? {
          success: true,
          data: { id: 2, tableName: '7月分配', monthKey: '2026-07', documentUrl: 'https://doc.weixin.qq.com/older', status: 'ACTIVE' }
        }
      : {
          success: true,
          data: { id: 3, tableName: '国庆活动客资', monthKey: '2026-09', documentUrl: 'https://doc.weixin.qq.com/new', status: 'ACTIVE' }
        });
    const confirm = vi.spyOn(window, 'confirm').mockReturnValue(true);
    const rebind = [...host.querySelectorAll('.assignment-table-manager-history-actions button')]
      .find((button) => button.textContent === '换绑') as HTMLButtonElement;
    rebind.click();
    await flushUi();

    expect(mocks.postJson).toHaveBeenCalledWith('/api/v1/assignment-tables/2/rebind', {});
    expect(host.querySelector('.assignment-table-manager-current')?.textContent).toContain('7月分配');
    const remove = [...host.querySelectorAll('.assignment-table-manager-history-actions button')]
      .find((button) => button.textContent === '删除') as HTMLButtonElement;
    remove.click();
    await flushUi();

    expect(confirm).toHaveBeenCalledTimes(2);
    expect(mocks.deleteJson).toHaveBeenCalledWith('/api/v1/assignment-tables/1');
    expect(host.textContent).not.toContain('8月分配');
  });

  it('refreshes failed creation history and shows the actionable backend reason', async () => {
    mocks.postJson.mockResolvedValueOnce({ success: false, data: null, message: '字段未完整复制：管家（期望文本，实际未返回）' });
    mocks.getJson.mockResolvedValueOnce({
      success: true,
      data: [
        { id: 4, tableName: '9月新客分配', monthKey: '2026-09', documentUrl: 'https://doc.weixin.qq.com/failed', status: 'FAILED', errorMessage: '字段未完整复制：管家（期望文本，实际未返回）' },
        { id: 1, tableName: '8月分配', monthKey: '2026-08', documentUrl: 'https://doc.weixin.qq.com/old', status: 'ACTIVE' }
      ]
    });
    const form = host.querySelector('.assignment-table-manager-create') as HTMLFormElement;
    form.dispatchEvent(new Event('submit'));
    await flushUi();
    await flushUi();

    expect(host.textContent).toContain('字段未完整复制：管家');
    expect(host.querySelector('.assignment-table-manager-history')?.textContent).toContain('9月新客分配');
    const failedOpenButton = [...host.querySelectorAll('.assignment-table-manager-history button')]
      .find((button) => button.textContent === '打开') as HTMLButtonElement;
    failedOpenButton.click();
    await flushUi();
    expect(mocks.openAssignmentTable).toHaveBeenCalledWith('https://doc.weixin.qq.com/failed');
  });
});
