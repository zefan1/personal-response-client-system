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

  it('emits dashboard navigation events from rendered links, rows, and quick actions', async () => {
    const { app, host, eventBus } = await mountPanel();
    const seen: Array<{ event: string; payload: unknown }> = [];
    eventBus.on('followup:switch-tab', (payload) => seen.push({ event: 'followup:switch-tab', payload }));
    eventBus.on('customer:selected', (payload) => seen.push({ event: 'customer:selected', payload }));
    eventBus.on('workbench:capture-chat', (payload) => seen.push({ event: 'workbench:capture-chat', payload }));
    eventBus.on('quick-search:show', (payload) => seen.push({ event: 'quick-search:show', payload }));

    const viewAllLinks = [...host.querySelectorAll('.section-inline-head .link-button')] as HTMLButtonElement[];
    viewAllLinks[0].click();
    viewAllLinks[1].click();

    const firstRow = host.querySelector('.workbench-row-main') as HTMLButtonElement | null;
    expect(firstRow).toBeTruthy();
    firstRow?.click();

    const quickActions = [...host.querySelectorAll('.quick-actions button')] as HTMLButtonElement[];
    quickActions.forEach((button) => button.click());
    await flushUi();

    expect(seen).toContainEqual({ event: 'followup:switch-tab', payload: { tab: 'OVERDUE' } });
    expect(seen).toContainEqual({ event: 'followup:switch-tab', payload: { tab: 'NEW_LEAD' } });
    expect(seen).toContainEqual({
      event: 'customer:selected',
      payload: { phone: '18800000001', scene: 'ACTIVE_REPLY', leadType: 'TUAN_GOU', sourceFrom: 'DASHBOARD' }
    });
    expect(seen).toContainEqual({ event: 'workbench:capture-chat', payload: {} });
    expect(seen).toContainEqual({ event: 'quick-search:show', payload: {} });
    expect(host.querySelector('.banner')?.textContent ?? '').not.toEqual('');

    app.unmount();
  });

  it('renders and dismisses realtime notices through the actual notice button', async () => {
    const { app, host, eventBus } = await mountPanel();

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
    const close = host.querySelector('.workbench-notice button') as HTMLButtonElement | null;
    expect(close).toBeTruthy();
    close?.click();
    await flushUi();

    expect(host.textContent).not.toContain('Ops Notice');
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
