import { createApp, nextTick, type App } from 'vue';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { FollowupItem } from './types';

const apiMocks = vi.hoisted(() => ({
  getJson: vi.fn()
}));

vi.mock('../../shared/apiClient', () => ({
  getJson: apiMocks.getJson
}));

type MountedPanel = {
  app: App<Element>;
  host: HTMLDivElement;
  eventBus: typeof import('../../shared/eventBus')['eventBus'];
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

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountPanel(): Promise<MountedPanel> {
  vi.resetModules();
  localStorage.clear();
  localStorage.setItem('desktop_config', JSON.stringify({
    workbenchRefreshIntervalS: 30,
    workbenchFollowupListLimit: 3,
    workbenchNewLeadListLimit: 2,
    workbenchMaxNotices: 2,
    newReminderFlashMs: 2000,
    toastMaxCount: 2,
    toastNewLeadDismissS: 30
  }));
  const [{ default: WorkbenchPanel }, { eventBus }] = await Promise.all([
    import('./WorkbenchPanel.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(WorkbenchPanel);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

describe('WorkbenchPanel', () => {
  beforeEach(() => {
    installMemoryLocalStorage();
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:00:00Z'));
    apiMocks.getJson.mockResolvedValue({
      success: true,
      data: {
        items: [
          followup({ phone: '18800000001', nickname: 'Overdue A', reminderType: 'OVERDUE', leadType: 'TUAN_GOU', overdueHours: 5 }),
          followup({ phone: '18800000002', nickname: 'Due B', reminderType: 'DUE_TODAY', leadType: 'XIAN_SUO', nextFollowupAt: '2026-07-03T13:00:00' }),
          followup({ phone: '18800000003', nickname: 'Appointment C', reminderType: 'APPOINTMENT', leadType: 'TUAN_GOU' }),
          followup({ phone: '18800000004', nickname: 'Lead D', reminderType: 'NEW_LEAD', leadType: 'XIAN_SUO', sourceTable: 'sheet-a', arrivedAt: '2026-07-03T11:00:00Z' })
        ]
      },
      errorCode: null,
      message: null
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    localStorage.clear();
    apiMocks.getJson.mockReset();
  });

  it('renders loaded metrics, urgent followups, and new leads from the API', async () => {
    const { app, host } = await mountPanel();

    expect(apiMocks.getJson).toHaveBeenCalledWith('/api/v1/followups/today', 5000);
    expect(host.querySelectorAll('.metric-card')).toHaveLength(3);
    expect(host.textContent).toContain('Overdue A');
    expect(host.textContent).toContain('Due B');
    expect(host.textContent).toContain('Lead D');

    app.unmount();
  });

  it('emits dashboard navigation events from rendered links, rows, and metric cards', async () => {
    const { app, host, eventBus } = await mountPanel();
    const seen: Array<{ event: string; payload: unknown }> = [];
    eventBus.on('followup:switch-tab', (payload) => seen.push({ event: 'followup:switch-tab', payload }));
    eventBus.on('customer:selected', (payload) => seen.push({ event: 'customer:selected', payload }));
    eventBus.on('workbench:capture-chat', (payload) => seen.push({ event: 'workbench:capture-chat', payload }));
    eventBus.on('quick-search:show', (payload) => seen.push({ event: 'quick-search:show', payload }));

    const viewAllLinks = [...host.querySelectorAll('.section-inline-head .link-button')] as HTMLButtonElement[];
    viewAllLinks[0].click();
    viewAllLinks[1].click();

    const metricCards = [...host.querySelectorAll('.metric-card')] as HTMLButtonElement[];
    metricCards.forEach((button) => button.click());

    const firstRow = host.querySelector('.workbench-row-main') as HTMLButtonElement | null;
    expect(firstRow).toBeTruthy();
    firstRow?.click();

    const actionButtons = [...host.querySelectorAll('.workbench-action')] as HTMLButtonElement[];
    expect(actionButtons.map((button) => button.querySelector('strong')?.textContent)).toEqual([
      '识别聊天',
      '速搜模板',
      '批量待办'
    ]);
    expect(actionButtons.map((button) => button.textContent)).toEqual([
      '识识别聊天截图生成回复',
      '模速搜模板话术和素材',
      '批批量待办选择客户发送'
    ]);
    actionButtons.forEach((button) => button.click());

    await flushUi();

    expect(seen).toContainEqual({ event: 'followup:switch-tab', payload: { tab: 'OVERDUE' } });
    expect(seen).toContainEqual({ event: 'followup:switch-tab', payload: { tab: 'NEW_LEAD' } });
    expect(seen).toContainEqual({ event: 'followup:switch-tab', payload: { tab: 'APPOINTMENT' } });
    expect(seen).toContainEqual({ event: 'workbench:capture-chat', payload: {} });
    expect(seen).toContainEqual({ event: 'quick-search:show', payload: {} });
    expect(seen).toContainEqual({
      event: 'customer:selected',
      payload: { phone: '18800000001', scene: 'ACTIVE_REPLY', leadType: 'TUAN_GOU', sourceFrom: 'DASHBOARD' }
    });
    expect(host.querySelector('.quick-actions')).toBeFalsy();

    app.unmount();
  });

  it('shows a retry action after load failure and reloads manually', async () => {
    let followupCalls = 0;
    apiMocks.getJson.mockImplementation(async (path: string) => {
      if (path === '/api/v1/notices/active') {
        return { success: true, data: [], errorCode: null, message: null };
      }
      followupCalls += 1;
      if (followupCalls === 1) {
        throw new Error('timeout');
      }
      return {
        success: true,
        data: { items: [followup({ phone: '18800000009', nickname: 'Retry Lead', reminderType: 'DUE_TODAY' })] },
        errorCode: null,
        message: null
      };
    });

    const { app, host } = await mountPanel();
    expect(host.textContent).toContain('数据加载失败，请检查网络后重试');

    const retryButton = host.querySelector('.workbench-status-banner button') as HTMLButtonElement | null;
    expect(retryButton).toBeTruthy();
    retryButton?.click();
    await flushUi();

    const calledPaths = apiMocks.getJson.mock.calls.map(([path]) => path);
    expect(calledPaths.filter((path) => path === '/api/v1/followups/today')).toHaveLength(2);
    expect(calledPaths).toContain('/api/v1/notices/active');
    expect(host.textContent).toContain('Retry Lead');
    expect(host.querySelector('.workbench-status-banner')).toBeFalsy();

    app.unmount();
  });

  it('receives realtime notices and renders one workbench notice banner', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('SYSTEM_NOTICE', {
      noticeId: 'notice-a',
      title: 'Ops Notice',
      content: 'Check datasource mapping',
      level: 'WARN',
      createdAt: '2026-07-03T12:00:00Z',
      expireAt: '2026-07-04T12:00:00Z'
    });
    eventBus.emit('SYSTEM_NOTICE', {
      noticeId: 'notice-a',
      title: 'Ops Notice',
      content: 'Check datasource mapping',
      level: 'WARN',
      createdAt: '2026-07-03T12:00:00Z',
      expireAt: '2026-07-04T12:00:00Z'
    });
    await flushUi();

    expect(host.textContent).toContain('Ops Notice');
    expect(host.querySelectorAll('.workbench-notice')).toHaveLength(1);
    app.unmount();
  });
});

function followup(patch: Partial<FollowupItem>): FollowupItem {
  return {
    phone: '18800000000',
    nickname: 'Customer',
    leadType: 'PENDING',
    reminderType: 'DUE_TODAY',
    nextFollowupAt: '2026-07-03T12:30:00',
    ...patch
  };
}
