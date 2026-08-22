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

async function flushUi() {
  await Promise.resolve();
  await Promise.resolve();
  await nextTick();
}

async function mountPanel(): Promise<MountedPanel> {
  vi.resetModules();
  const [{ default: FollowupListPanel }, { eventBus }] = await Promise.all([
    import('./FollowupListPanel.vue'),
    import('../../shared/eventBus')
  ]);
  const host = document.createElement('div');
  document.body.appendChild(host);
  const app = createApp(FollowupListPanel);
  app.mount(host);
  await flushUi();
  return { app, host, eventBus };
}

function activeTabText(host: Element): string {
  return host.querySelector('.tab-button.active')?.textContent ?? '';
}

function loadedItems(): FollowupItem[] {
  return [
    item({ phone: '18800000001', nickname: 'Overdue', reminderType: 'OVERDUE', overdueHours: 3 }),
    item({ phone: '18800000002', nickname: 'Today', reminderType: 'DUE_TODAY' }),
    item({ phone: '18800000003', nickname: 'Appointment', reminderType: 'APPOINTMENT', appointmentTime: '14:30' }),
    item({ phone: '18800000004', phoneFull: '18800000004', nickname: 'New Lead', reminderType: 'NEW_LEAD', sourceTable: 'sheet-a', assignedKeeper: 'admin' })
  ];
}

describe('FollowupListPanel', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-07-03T12:08:00Z'));
    apiMocks.getJson.mockResolvedValue({
      success: true,
      data: {
        keeperId: 'keeper-a',
        totalCount: 4,
        items: loadedItems()
      },
      errorCode: null,
      message: null
    });
  });

  afterEach(() => {
    document.body.innerHTML = '';
    vi.useRealTimers();
    apiMocks.getJson.mockReset();
  });

  it('renders loaded followups and switches tabs from user clicks', async () => {
    const { app, host } = await mountPanel();

    expect(apiMocks.getJson).toHaveBeenCalledWith('/api/v1/followups/today', 10000);
    const tabs = [...host.querySelectorAll('.tab-button')] as HTMLButtonElement[];
    expect(tabs).toHaveLength(4);
    expect(tabs.map((tab) => tab.textContent?.replace(/\s+/g, ' ').trim())).toEqual([
      '今日待跟进 1',
      '逾期跟进 1',
      '今日预约 1',
      '新客资 1'
    ]);
    expect(activeTabText(host)).toContain('今日待跟进');
    expect(activeTabText(host)).toContain('1');
    expect(host.textContent).toContain('Today');
    expect(host.textContent).toContain('20:08');
    const refreshButton = host.querySelector('.panel-header .icon-refresh-button') as HTMLButtonElement | null;
    expect(refreshButton?.textContent?.trim()).toBe('↻');
    expect(refreshButton?.getAttribute('aria-label')).toBe('刷新今日跟进');
    expect(refreshButton?.textContent).not.toContain('刷新');

    tabs[2].click();
    await flushUi();

    expect(activeTabText(host)).toContain('1');
    expect(host.textContent).toContain('Appointment');
    expect(host.textContent).not.toContain('Overdue');

    app.unmount();
  });

  it('honors all valid workbench tab-switch events and ignores invalid payloads', async () => {
    const { app, host, eventBus } = await mountPanel();

    eventBus.emit('followup:switch-tab', { tab: 'DUE_TODAY' });
    await flushUi();
    expect(host.textContent).toContain('Today');

    eventBus.emit('followup:switch-tab', { tab: 'APPOINTMENT' });
    await flushUi();
    expect(host.textContent).toContain('Appointment');

    eventBus.emit('followup:switch-tab', { tab: 'NEW_LEAD' });
    await flushUi();
    expect(host.textContent).toContain('New Lead');
    expect(host.textContent).toContain('手机号 18800000004');
    expect(host.textContent).toContain('来源 sheet-a');
    expect(host.textContent).not.toContain('admin');

    eventBus.emit('followup:switch-tab', { tab: 'NOT_A_TAB' });
    await flushUi();
    expect(host.textContent).toContain('New Lead');

    app.unmount();
  });

  it('offers an explicit profile action for each followup customer', async () => {
    const { app, host, eventBus } = await mountPanel();
    const selected: unknown[] = [];
    eventBus.on('customer:selected', (payload) => selected.push(payload));

    const profileButton = host.querySelector('.followup-profile-button') as HTMLButtonElement | null;
    expect(profileButton).toBeTruthy();
    expect(profileButton?.getAttribute('aria-label')).toContain('查看 Today 的客户档案');
    profileButton?.click();
    await flushUi();

    expect(selected).toContainEqual({
      phone: '18800000002',
      scene: 'ACTIVE_REPLY',
      leadType: 'PENDING',
      reminderType: 'DUE_TODAY',
      sourceFrom: 'FOLLOWUP_LIST'
    });
    app.unmount();
  });

  it('emits batch template events from the rendered batch bar', async () => {
    const { app, host, eventBus } = await mountPanel();
    const batchEvents: unknown[] = [];
    eventBus.on('batch:start', (payload) => batchEvents.push(payload));

    const checkbox = host.querySelector('.followup-row input[type="checkbox"]') as HTMLInputElement | null;
    expect(checkbox).toBeTruthy();
    if (checkbox) {
      checkbox.checked = true;
    }
    checkbox?.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();

    const batchButtons = [...host.querySelectorAll('.batch-bar button')] as HTMLButtonElement[];
    expect(batchButtons.length).toBeGreaterThanOrEqual(3);
    expect(host.querySelector('.batch-selection-count')?.textContent).toContain('已选 1 个');
    expect(host.querySelector('.batch-secondary-actions')).toBeTruthy();
    expect(host.querySelector('.batch-primary-action')?.textContent).toContain('批量发模板');
    batchButtons[2].click();
    await flushUi();

    expect(batchEvents).toEqual([{ phones: ['18800000002'], source: 'FOLLOWUP_LIST' }]);
    app.unmount();
  });

  it('hides the batch bar after switching away from the selected customer category', async () => {
    const { app, host } = await mountPanel();
    const tabs = [...host.querySelectorAll('.tab-button')] as HTMLButtonElement[];

    tabs[1].click();
    await flushUi();
    const checkbox = host.querySelector('.followup-row input[type="checkbox"]') as HTMLInputElement;
    checkbox.checked = true;
    checkbox.dispatchEvent(new Event('change', { bubbles: true }));
    await flushUi();
    expect(host.querySelector('.batch-selection-count')?.textContent).toContain('已选 1 个');

    tabs[0].click();
    await flushUi();
    expect(host.querySelector('.batch-bar')).toBeNull();

    app.unmount();
  });

  it('removes a confirmed followup from the current rendered tab', async () => {
    const { app, host, eventBus } = await mountPanel();
    expect(host.textContent).toContain('Today');

    eventBus.emit('followup:completed', { phone: '18800000002', reminderType: 'DUE_TODAY' });
    await flushUi();

    expect(host.textContent).not.toContain('Today');
    expect(activeTabText(host)).toContain('0');

    eventBus.emit('followup:switch-tab', { tab: 'APPOINTMENT' });
    await flushUi();
    expect(host.textContent).toContain('Appointment');
    app.unmount();
  });
});

function item(patch: Partial<FollowupItem>): FollowupItem {
  return {
    phone: '18800000000',
    phoneFull: undefined,
    nickname: 'Customer',
    leadType: 'PENDING',
    reminderType: 'DUE_TODAY',
    alertLevel: 'NORMAL',
    overdueHours: null,
    ...patch
  };
}
